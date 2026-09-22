package com.lrj.erp.procurement.infrastructure;
import com.lrj.erp.kernel.reporting.ReportSource.Row;
import org.apache.ibatis.annotations.*;
import java.util.List;
/** 类型化导出 SQL 仍在拥有业务表的模块，不向报表暴露数据库。 */
@Mapper
public interface PurchaseReportMapper {
    String fingerprint(@Param("tenantId") long tenantId);
    List<Row> page(@Param("tenantId") long tenantId,@Param("afterId") long afterId,@Param("limit") int limit);
}
