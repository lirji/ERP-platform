package com.lrj.erp.procurement.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * 采购仓储端口。定义在 domain，由 infrastructure 实现。
 * 依赖方向 {@code application → domain ← infrastructure}，由架构测试强制。
 */
public interface ProcurementRepository {

    long insertOrder(long tenantId, long companyId, String orderNo, long supplierId,
                     String supplierRefJson, long warehouseId, String orgPath, long createdBy);

    long insertOrderLine(long tenantId, long orderId, int lineNo, long skuId,
                         String skuRefJson, BigDecimal orderedQty, BigDecimal unitPrice);

    OrderHeader findOrder(long tenantId, long orderId);

    List<OrderLine> findOrderLines(long tenantId, long orderId);

    OrderLine findOrderLine(long tenantId, long orderLineId);

    /**
     * 累加已收数量；<b>仅当不超过订购量</b>时成功。
     *
     * <p>上界写进 WHERE：与 CHECK 约束形成双保险，且并发分批收货时
     * 判断与累加原子完成——先查已收量再累加，两个线程可以同时读到"还能收"。
     *
     * @return false 表示会超收
     */
    boolean increaseReceivedIfWithinOrdered(long tenantId, long orderLineId, BigDecimal qty);

    boolean closeOrderLine(long tenantId, long orderLineId);

    void updateOrderState(long tenantId, long orderId, String state, long newVersion);

    long insertReceipt(long tenantId, long companyId, String receiptNo, long orderId,
                       long warehouseId, String orgPath, long createdBy);

    long insertReceiptLine(long tenantId, long receiptId, long orderLineId, long skuId,
                           String batchNo, BigDecimal qty);

    /** 该订单是否已发生过收货——取消的守卫条件。 */
    boolean hasAnyReceipt(long tenantId, long orderId);

    record OrderHeader(long id, long tenantId, long companyId, String orderNo, long supplierId,
                       long warehouseId, String state, long version, String orgPath) { }

    record OrderLine(long id, long orderId, int lineNo, long skuId, String skuRefJson,
                     BigDecimal orderedQty, BigDecimal receivedQty, boolean closed) {

        /** 尚可收货的量。 */
        public BigDecimal outstanding() {
            return orderedQty.subtract(receivedQty);
        }
    }
}
