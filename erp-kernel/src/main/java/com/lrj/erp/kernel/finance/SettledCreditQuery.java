package com.lrj.erp.kernel.finance;
import java.math.BigDecimal;

/** 财务提供的权威已核销应收查询端口；信用硬校验不依赖可能滞后的事件投影。 */
public interface SettledCreditQuery {
    /** 正向核销减去反核销，按租户及客户隔离。 */
    BigDecimal netSettled(long tenantId,long customerId);
}
