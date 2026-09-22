package com.lrj.erp.reporting.infrastructure;
import com.lrj.erp.reporting.domain.ReportRepository.*;
import com.lrj.erp.kernel.reporting.ReportSource.*;
import org.apache.ibatis.annotations.*;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import java.time.LocalDate;
import java.util.List;
/** 报表 SQL 不得包含其他模块业务表。 */
@Mapper
public interface ReportMapper {
    /** INSERT RETURNING 使用显式租户，避免被按 SELECT 解析。 */
    @InterceptorIgnore(tenantLine="true",dataPermission="true")
    Long create(@Param("tenantId") long tenantId,@Param("kind") Kind kind,@Param("fingerprint") String fingerprint);
    Job active(@Param("tenantId") long tenantId,@Param("kind") Kind kind);
    Job lock(@Param("tenantId") long tenantId,@Param("id") long id);
    Job current(@Param("tenantId") long tenantId,@Param("kind") Kind kind);
    int append(@Param("tenantId") long tenantId,@Param("jobId") long jobId,@Param("rows") List<Row> rows);
    int advance(@Param("j") Job job,@Param("cursor") long cursor);
    int finish(@Param("j") Job job,@Param("state") String state);
    int publish(@Param("j") Job job);
    int purgeRows(@Param("tenantId") long tenantId,@Param("jobId") long jobId,@Param("limit") int limit);
    int deleteJob(@Param("tenantId") long tenantId,@Param("jobId") long jobId);
    List<Row> page(@Param("tenantId") long tenantId,@Param("jobId") long jobId,@Param("afterId") long afterId,@Param("limit") int limit);
    Summary summary(@Param("tenantId") long tenantId,@Param("jobId") long jobId);
    List<Aging> aging(@Param("tenantId") long tenantId,@Param("jobId") long jobId,@Param("asOf") LocalDate asOf);
}
