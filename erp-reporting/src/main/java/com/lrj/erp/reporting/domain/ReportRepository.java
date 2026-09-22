package com.lrj.erp.reporting.domain;
import com.lrj.erp.kernel.reporting.ReportSource.*;
import java.time.Instant;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
/** 报表持久化端口，只负责 rpt_* 投影和任务，不拥有业务事实。 */
public interface ReportRepository {
    record Job(long id,long tenantId,String kind,String fingerprint,long cursorId,String state,Instant createdAt,Instant finishedAt) {}
    record Summary(BigDecimal quantity,BigDecimal inTransit,BigDecimal amount,BigDecimal reduction,BigDecimal unsettled,long unknownAmounts,long rows) {}
    record Aging(String bucket,BigDecimal outstanding,long rows) {}
    Long create(long tenantId,Kind kind,String fingerprint);
    Job active(long tenantId,Kind kind);
    Job lock(long tenantId,long id);
    Job current(long tenantId,Kind kind);
    void append(long tenantId,long jobId,List<Row> rows);
    boolean advance(Job job,long cursor);
    boolean finish(Job job,String state);
    void publish(Job job);
    /** 清理一个非当前快照的有界批次。 */
    int purgeRows(long tenantId,long jobId,int limit);
    boolean deleteJob(long tenantId,long jobId);
    List<Row> page(long tenantId,long jobId,long afterId,int limit);
    Summary summary(long tenantId,long jobId);
    List<Aging> aging(long tenantId,long jobId,LocalDate asOf);
}
