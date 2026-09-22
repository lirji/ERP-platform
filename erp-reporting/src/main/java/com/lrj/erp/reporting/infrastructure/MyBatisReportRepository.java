package com.lrj.erp.reporting.infrastructure;
import com.lrj.erp.reporting.domain.ReportRepository;
import com.lrj.erp.kernel.reporting.ReportSource.*;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
/** 候选数据与游标在用例事务内提交，影响行数异常不允许被当作成功。 */
@Repository
public class MyBatisReportRepository implements ReportRepository {
    private final ReportMapper mapper;
    public MyBatisReportRepository(ReportMapper mapper) { this.mapper=mapper; }
    public Long create(long t,Kind k,String f) { return mapper.create(t,k,f); }
    public Job active(long t,Kind k) { return mapper.active(t,k); }
    public Job lock(long t,long id) { return mapper.lock(t,id); }
    public Job current(long t,Kind k) { return mapper.current(t,k); }
    public void append(long t,long id,List<Row> rows) { if(mapper.append(t,id,rows)!=rows.size()) throw new IllegalStateException("快照批次写入不完整"); }
    public boolean advance(Job j,long cursor) { return mapper.advance(j,cursor)==1; }
    public boolean finish(Job j,String state) { return mapper.finish(j,state)==1; }
    public void publish(Job j) { if(mapper.publish(j)!=1) throw new IllegalStateException("快照发布失败"); }
    public int purgeRows(long t,long id,int limit) { return mapper.purgeRows(t,id,limit); }
    public boolean deleteJob(long t,long id) { return mapper.deleteJob(t,id)==1; }
    public List<Row> page(long t,long id,long cursor,int limit) { return mapper.page(t,id,cursor,limit); }
    public Summary summary(long t,long id) { return mapper.summary(t,id); }
    public List<Aging> aging(long t,long id,LocalDate date) { return mapper.aging(t,id,date); }
}
