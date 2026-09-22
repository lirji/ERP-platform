package com.lrj.erp.sales.infrastructure;

import com.lrj.erp.sales.domain.SalesRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

/** {@link SalesRepository} 的 MyBatis 实现：把影响行数翻译成领域语义。 */
@Repository
public class MyBatisSalesRepository implements SalesRepository {

    private final SalesMapper mapper;

    public MyBatisSalesRepository(SalesMapper mapper) {
        this.mapper = mapper;
    }

    @Override public long insertOrder(long tenantId, long companyId, String orderNo, long customerId,
                                      String customerRefJson, long warehouseId, BigDecimal totalAmount,
                                      String orgPath, long createdBy) {
        return mapper.insertOrder(tenantId, companyId, orderNo, customerId, customerRefJson,
                warehouseId, totalAmount, orgPath, createdBy);
    }

    @Override public long insertOrderLine(long tenantId, long orderId, int lineNo, long skuId,
                                          String skuRefJson, String batchNo,
                                          BigDecimal orderedQty, BigDecimal unitPrice) {
        return mapper.insertOrderLine(tenantId, orderId, lineNo, skuId, skuRefJson,
                batchNo, orderedQty, unitPrice);
    }

    @Override public OrderHeader lockOrder(long tenantId, long orderId) {
        return mapper.lockOrder(tenantId, orderId);
    }

    @Override public void bindCreditApproval(long tenantId, long orderId, long approvalId) {
        requireUpdated(mapper.bindCreditApproval(tenantId, orderId, approvalId));
    }

    /** 影响零行表示状态不匹配，不能当作成功。 */
    private void requireUpdated(int count) {
        if (count != 1) throw new com.lrj.erp.kernel.error.DomainException(
                com.lrj.erp.kernel.error.SystemErrorCode.OPTIMISTIC_LOCK, java.util.Map.of());
    }

    @Override public OrderHeader findOrder(long tenantId, long orderId) {
        return mapper.findOrder(tenantId, orderId);
    }

    @Override public List<OrderLine> findOrderLines(long tenantId, long orderId) {
        return mapper.findOrderLines(tenantId, orderId);
    }

    @Override public OrderLine findOrderLine(long tenantId, long orderLineId) {
        return mapper.findOrderLine(tenantId, orderLineId);
    }

    @Override public boolean increaseShippedIfWithinOrdered(long tenantId, long orderLineId, BigDecimal qty) {
        return mapper.increaseShippedIfWithinOrdered(tenantId, orderLineId, qty) > 0;
    }

    @Override public void updateOrderState(long tenantId, long orderId, String state, long newVersion) {
        requireUpdated(mapper.updateOrderState(tenantId, orderId, state, newVersion));
    }

    @Override public void markReserved(long tenantId, long orderId, boolean reserved) {
        requireUpdated(mapper.markReserved(tenantId, orderId, reserved));
    }

    @Override public long insertShipment(long tenantId, long companyId, String shipmentNo, long orderId,
                                         long warehouseId, String orgPath, long createdBy) {
        return mapper.insertShipment(tenantId, companyId, shipmentNo, orderId, warehouseId, orgPath, createdBy);
    }

    @Override public long insertShipmentLine(long tenantId, long shipmentId, long orderLineId,
                                             long skuId, String batchNo, BigDecimal qty) {
        return mapper.insertShipmentLine(tenantId, shipmentId, orderLineId, skuId, batchNo, qty);
    }

    @Override public void markDelivered(long tenantId, long shipmentId) {
        requireUpdated(mapper.markDelivered(tenantId, shipmentId));
    }

    @Override public void markSigned(long tenantId, long shipmentId) {
        requireUpdated(mapper.markSigned(tenantId, shipmentId));
    }

    /** 必须命中本次刚插入的来源行，否则整体回滚。 */
    @Override public void recordLineAmount(long tenantId,long lineId,BigDecimal amount,String currency) {
        if(mapper.recordLineAmount(tenantId,lineId,amount,currency)!=1) throw new IllegalStateException("来源金额快照写入失败");
    }

    @Override public boolean hasAnyShipment(long tenantId, long orderId) {
        return mapper.hasAnyShipment(tenantId, orderId);
    }

    @Override public void ensureCreditAccount(long tenantId, long customerId) {
        mapper.ensureCreditAccount(tenantId, customerId);
    }

    @Override public boolean occupyCreditIfWithinLimit(long tenantId, long customerId,
                                                       BigDecimal amount, BigDecimal creditLimit) {
        return mapper.occupyCreditIfWithinLimit(tenantId, customerId, amount, creditLimit) > 0;
    }

    @Override public void occupyCreditForced(long tenantId, long customerId, BigDecimal amount) {
        requireUpdated(mapper.occupyCreditForced(tenantId, customerId, amount));
    }

    @Override public void releaseCredit(long tenantId, long customerId, BigDecimal amount) {
        requireUpdated(mapper.releaseCredit(tenantId, customerId, amount));
    }

    @Override public BigDecimal usedCredit(long tenantId, long customerId) {
        BigDecimal used = mapper.usedCredit(tenantId, customerId);
        return used == null ? BigDecimal.ZERO : used;
    }
}
