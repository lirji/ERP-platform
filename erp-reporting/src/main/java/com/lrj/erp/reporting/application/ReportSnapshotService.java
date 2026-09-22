package com.lrj.erp.reporting.application;
import com.lrj.erp.reporting.domain.ReportRepository;
import com.lrj.erp.reporting.domain.ReportRepository.*;
import com.lrj.erp.kernel.reporting.ReportSource;
import com.lrj.erp.kernel.reporting.ReportSource.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/** 可恢复快照：每步有界事务，候选集完整且来源稳定才切换已发布指针。 */
@Service
public class ReportSnapshotService {
    private final ReportRepository repository;
    private final Map<Kind,ReportSource> sources=new EnumMap<>(Kind.class);
    public ReportSnapshotService(ReportRepository repository,List<ReportSource> sources) {
        this.repository=repository;
        for(var source:sources) if(this.sources.put(source.kind(),source)!=null) throw new IllegalStateException("报表导出端口重复");
    }
    public record Page(long snapshotId,String kind,java.time.Instant snapshotTime,List<Row> rows) {}
    public record Totals(long snapshotId,String kind,java.time.Instant snapshotTime,Summary values) {}
    public record AgeReport(long snapshotId,LocalDate asOf,List<Aging> buckets) {}

    /** 同一租户/类型最多一个 BUILDING 任务，重复启动返回原检查点。 */
    @Transactional
    public long start(long tenantId,Kind kind) {
        ReportSource source=source(tenantId,kind);
        Job active=repository.active(tenantId,kind);
        if(active!=null) return active.id();
        Long id=repository.create(tenantId,kind,source.fingerprint(tenantId));
        return id==null?repository.active(tenantId,kind).id():id;
    }
    /** 一步至多1000行；写候选行与游标同事务。终态任务可安全重复调用。 */
    @Transactional(timeout=60)
    public Job step(long tenantId,long id,int batchSize) {
        ReportSource.validate(tenantId,0,batchSize);
        Job job=required(tenantId,id);
        if(!job.state().equals("BUILDING")) return job;
        ReportSource source=source(tenantId,Kind.valueOf(job.kind()));
        List<Row> rows=source.page(tenantId,job.cursorId(),batchSize);
        if(!rows.isEmpty()) {
            long cursor=job.cursorId();
            for(Row row:rows) {
                if(row.sourceId()<=cursor) throw new IllegalStateException("权威导出游标未严格递增");
                cursor=row.sourceId();
            }
            repository.append(tenantId,id,rows);
            if(!repository.advance(job,cursor)) throw new IllegalStateException("重建检查点并发冲突");
        } else {
            // 发生业务变更只废弃候选集，不能把半旧半新的报表发布给用户。
            boolean stable=job.fingerprint().equals(source.fingerprint(tenantId));
            if(!repository.finish(job,stable?"PUBLISHED":"STALE")) throw new IllegalStateException("重建状态并发冲突");
            if(stable) repository.publish(job);
        }
        return required(tenantId,id);
    }
    /** 取消构建只失效候选快照，不改变业务事实或上一份已发布快照。 */
    @Transactional
    public void cancel(long tenantId,long id) {
        Job job=required(tenantId,id);
        if(job.state().equals("BUILDING") && !repository.finish(job,"STALE"))
            throw new IllegalStateException("取消重建并发冲突");
    }
    /** 显式回收非当前终态快照，一步最多1000行；调用方应确认没有读者继续翻该快照。 */
    @Transactional
    public boolean purgeStep(long tenantId,long id,int limit) {
        ReportSource.validate(tenantId,0,limit);
        Job job=repository.lock(tenantId,id);
        if(job==null) return true;
        Job current=repository.current(tenantId,Kind.valueOf(job.kind()));
        if(job.state().equals("BUILDING") || current!=null && current.id()==id)
            throw new IllegalArgumentException("不能清理构建中或当前发布快照");
        if(repository.purgeRows(tenantId,id,limit)>0) return false;
        if(!repository.deleteJob(tenantId,id)) throw new IllegalStateException("快照任务清理失败");
        return true;
    }

    /** 查询任务可恢复检查点；租户不匹配返回不存在。 */
    @Transactional
    public Job job(long tenantId,long id) { return required(tenantId,id); }

    /** 首次查询使用当前发布快照，后续翻页应固定返回的 snapshotId。 */
    @Transactional(readOnly=true)
    public Page currentPage(long tenantId,Kind kind,long afterId,int limit) {
        ReportSource.validate(tenantId,afterId,limit);
        Job job=current(tenantId,kind);
        return page(job,afterId,limit);
    }
    /** 固定快照翻页，重建发布新快照也不会在本次遍历中混入其他版本。 */
    @Transactional
    public Page snapshotPage(long tenantId,long snapshotId,long afterId,int limit) {
        ReportSource.validate(tenantId,afterId,limit);
        Job job=required(tenantId,snapshotId);
        if(!job.state().equals("PUBLISHED")) throw new IllegalArgumentException("快照尚未发布");
        return page(job,afterId,limit);
    }
    /** 金额汇总同时给出未知金额行数，不把部分已知成本宣称为完整成本。 */
    @Transactional(readOnly=true)
    public Totals totals(long tenantId,Kind kind) {
        Job job=current(tenantId,kind);
        return new Totals(job.id(),job.kind(),job.createdAt(),repository.summary(tenantId,job.id()));
    }
    /** 以快照日期计算未核销账龄，不把它解释为合同逾期。 */
    @Transactional(readOnly=true)
    public AgeReport aging(long tenantId,Kind kind) {
        if(kind!=Kind.AR && kind!=Kind.AP) throw new IllegalArgumentException("仅 AR/AP 提供账龄");
        Job job=current(tenantId,kind);
        LocalDate date=job.createdAt().atZone(ZoneId.of("Asia/Shanghai")).toLocalDate();
        return new AgeReport(job.id(),date,repository.aging(tenantId,job.id(),date));
    }
    private Page page(Job job,long cursor,int limit) {
        return new Page(job.id(),job.kind(),job.createdAt(),repository.page(job.tenantId(),job.id(),cursor,limit));
    }
    private Job current(long tenantId,Kind kind) {
        source(tenantId,kind);
        Job job=repository.current(tenantId,kind);
        if(job==null) throw new IllegalArgumentException("尚无已发布报表，请先重建");
        return job;
    }
    private Job required(long tenantId,long id) {
        Job job=repository.lock(tenantId,id);
        if(job==null) throw new IllegalArgumentException("报表任务不存在");
        return job;
    }
    private ReportSource source(long tenantId,Kind kind) {
        ReportSource.validate(tenantId,0,1);
        if(kind==null || !sources.containsKey(kind)) throw new IllegalArgumentException("报表来源未配置");
        return sources.get(kind);
    }
}
