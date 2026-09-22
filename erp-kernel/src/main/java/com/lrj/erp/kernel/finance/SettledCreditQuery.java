package com.lrj.erp.kernel.finance;
import java.math.BigDecimal;

/** 财务提供的权威信用释放查询端口；信用硬校验不依赖可能滞后的事件投影。 */
public interface SettledCreditQuery {
    /** 红字金额加未被红字覆盖的有效核销净额，按租户/客户隔离，避免重复释放。 */
    BigDecimal releasedCredit(long tenantId,long customerId);
}
