package com.lrj.erp.finance.infrastructure;
import com.lrj.erp.kernel.monitoring.IntegrityProbe;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
/** 后台全租户只读对账；不输出单据数据，仅返回低基数计数。 */
@Component
public class PayableIntegrityProbe implements IntegrityProbe {
    private final PayableIntegrityMapper mapper;
    public PayableIntegrityProbe(PayableIntegrityMapper mapper) { this.mapper=mapper; }
    public String name() { return "ap"; }
    @Transactional(readOnly=true,timeout=5)
    public Batch scan(long afterId,int limit) {
        if(afterId<0 || limit<1 || limit>1000) throw new IllegalArgumentException("对账分页越界");
        return mapper.scan(afterId,limit);
    }
}
