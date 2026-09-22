package com.lrj.erp.inventory.infrastructure;
import com.lrj.erp.kernel.reporting.ReportSource;
import org.springframework.stereotype.Component;
import java.util.List;
/** 本模块的只读导出适配器；分页和变更指纹均由权威模块负责。 */
@Component
public class InventoryReportSource implements ReportSource {
    private final InventoryReportMapper mapper;
    public InventoryReportSource(InventoryReportMapper mapper) { this.mapper=mapper; }
    public Kind kind() { return Kind.INVENTORY; }
    public String fingerprint(long tenantId) { ReportSource.validate(tenantId,0,1);return mapper.fingerprint(tenantId); }
    public List<Row> page(long tenantId,long afterId,int limit) { ReportSource.validate(tenantId,afterId,limit);return mapper.page(tenantId,afterId,limit); }
}
