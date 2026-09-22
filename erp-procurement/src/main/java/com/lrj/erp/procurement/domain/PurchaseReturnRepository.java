package com.lrj.erp.procurement.domain;
import java.math.BigDecimal;
/** 退货只通过本模块持久化端口访问来源行，跨域效果通过库存 API 和事件。 */
public interface PurchaseReturnRepository {
    record Source(long lineId,long sourceId,String sourceNo,long companyId,long partnerId,long warehouseId,long skuId,
            String batchNo,BigDecimal quantity,BigDecimal returnedQty,BigDecimal postedAmount,String currency) {}
    record Return(long id,long tenantId,String documentNo,long sourceLineId,BigDecimal quantity,String batchNo,
            String commandId,String reason,BigDecimal amount,String state,long version,long createdBy) {}
    Source lockSource(long tenantId,long lineId);
    Return lock(long tenantId,long id);
    Return command(long tenantId,String commandId);
    long insert(Return document);
    boolean addReturned(long tenantId,long sourceLineId,BigDecimal quantity);
    boolean transition(Return document,String next);
    void amount(Return document,BigDecimal amount);
}
