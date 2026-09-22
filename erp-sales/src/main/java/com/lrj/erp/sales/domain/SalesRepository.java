package com.lrj.erp.sales.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * 销售仓储端口。定义在 domain，由 infrastructure 实现。
 * 依赖方向 {@code application → domain ← infrastructure}，由架构测试强制。
 */
public interface SalesRepository {

    long insertOrder(long tenantId, long companyId, String orderNo, long customerId,
                     String customerRefJson, long warehouseId, BigDecimal totalAmount,
                     String orgPath, long createdBy);

    long insertOrderLine(long tenantId, long orderId, int lineNo, long skuId, String skuRefJson,
                         String batchNo, BigDecimal orderedQty, BigDecimal unitPrice);

    /** 修改聚合前先锁订单，串行化预占、取消和分批出库。 */
    OrderHeader lockOrder(long tenantId, long orderId);

    /** 绑定信用审批实例，唯一约束防止重复使用放行额度。 */
    void bindCreditApproval(long tenantId, long orderId, long approvalId);

    OrderHeader findOrder(long tenantId, long orderId);

    List<OrderLine> findOrderLines(long tenantId, long orderId);

    OrderLine findOrderLine(long tenantId, long orderLineId);

    /**
     * 累加已出库数量；<b>仅当不超过订购量</b>时成功。
     * 上界写进 WHERE，与 CHECK 约束双保险；并发分批出库时判断与累加原子完成。
     *
     * @return false 表示会超发
     */
    boolean increaseShippedIfWithinOrdered(long tenantId, long orderLineId, BigDecimal qty);

    void updateOrderState(long tenantId, long orderId, String state, long newVersion);

    void markReserved(long tenantId, long orderId, boolean reserved);

    long insertShipment(long tenantId, long companyId, String shipmentNo, long orderId,
                        long warehouseId, String orgPath, long createdBy);

    long insertShipmentLine(long tenantId, long shipmentId, long orderLineId, long skuId,
                            String batchNo, BigDecimal qty);

    /** 保存本次来源行金额和币种，供退货累计比例冲红；不改变历史原金额。 */
    void recordLineAmount(long tenantId,long lineId,BigDecimal amount,String currency);

    void markDelivered(long tenantId, long shipmentId);

    void markSigned(long tenantId, long shipmentId);

    boolean hasAnyShipment(long tenantId, long orderId);

    // ------------------------------------------------------------- 信用

    void ensureCreditAccount(long tenantId, long customerId);

    /**
     * 占用信用额度；<b>仅当占用后不超过授信上限</b>时成功。
     * 上限由调用方从主数据读入后传进来——信用上限属于客户主数据，
     * 不在销售侧冗余一份（两处存同一个上限必然出现不一致）。
     *
     * @return false 表示超出授信
     */
    boolean occupyCreditIfWithinLimit(long tenantId, long customerId,
                                      BigDecimal amount, BigDecimal creditLimit);

    /** 无条件占用；用于信用超限已获审批放行的场景。 */
    void occupyCreditForced(long tenantId, long customerId, BigDecimal amount);

    void releaseCredit(long tenantId, long customerId, BigDecimal amount);

    BigDecimal usedCredit(long tenantId, long customerId);

    record OrderHeader(long id, long tenantId, long companyId, String orderNo, long customerId,
                       long warehouseId, BigDecimal totalAmount, String state, boolean reserved,
                       long version, String orgPath) { }

    record OrderLine(long id, long orderId, int lineNo, long skuId, String skuRefJson,
                     String batchNo, BigDecimal orderedQty, BigDecimal shippedQty,
                     BigDecimal unitPrice) {

        /** 尚未出库的量。 */
        public BigDecimal outstanding() {
            return orderedQty.subtract(shippedQty);
        }
    }
}
