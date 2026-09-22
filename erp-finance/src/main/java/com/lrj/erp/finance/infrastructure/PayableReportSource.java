package com.lrj.erp.finance.infrastructure;
import com.lrj.erp.kernel.reporting.ReportSource;
import org.springframework.stereotype.Component;
import java.util.List;
/** 本模块的只读导出适配器；分页和变更指纹均由权威模块负责。 */
@Component
public class PayableReportSource implements ReportSource {
    private final PayableReportMapper mapper;
    public PayableReportSource(PayableReportMapper mapper) { this.mapper=mapper; }
    public Kind kind() { return Kind.AP; }
    public String fingerprint(long tenantId) { ReportSource.validate(tenantId,0,1);return mapper.fingerprint(tenantId); }
    public List<Row> page(long tenantId,long afterId,int limit) { ReportSource.validate(tenantId,afterId,limit);return mapper.page(tenantId,afterId,limit); }
}
