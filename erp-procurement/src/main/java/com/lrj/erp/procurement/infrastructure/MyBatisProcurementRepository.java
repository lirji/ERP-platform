package com.lrj.erp.procurement.infrastructure;

import com.lrj.erp.procurement.domain.ProcurementRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

/** {@link ProcurementRepository} 的 MyBatis 实现：把影响行数翻译成领域语义。 */
@Repository
public class MyBatisProcurementRepository implements ProcurementRepository {

    private final ProcurementMapper mapper;

    public MyBatisProcurementRepository(ProcurementMapper mapper) {
        this.mapper = mapper;
    }

    @Override public long insertOrder(long tenantId, long companyId, String orderNo, long supplierId,
                                      String supplierRefJson, long warehouseId, String orgPath, long createdBy) {
        return mapper.insertOrder(tenantId, companyId, orderNo, supplierId, supplierRefJson,
                warehouseId, orgPath, createdBy);
    }

    @Override public long insertOrderLine(long tenantId, long orderId, int lineNo, long skuId,
                                          String skuRefJson, BigDecimal orderedQty, BigDecimal unitPrice) {
        return mapper.insertOrderLine(tenantId, orderId, lineNo, skuId, skuRefJson, orderedQty, unitPrice);
    }

    @Override public OrderHeader lockOrder(long tenantId, long orderId) {
        return mapper.lockOrder(tenantId, orderId);
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

    @Override public boolean increaseReceivedIfWithinOrdered(long tenantId, long orderLineId, BigDecimal qty) {
        return mapper.increaseReceivedIfWithinOrdered(tenantId, orderLineId, qty) > 0;
    }

    @Override public boolean closeOrderLine(long tenantId, long orderLineId) {
        return mapper.closeOrderLine(tenantId, orderLineId) > 0;
    }

    @Override public void updateOrderState(long tenantId, long orderId, String state, long newVersion) {
        mapper.updateOrderState(tenantId, orderId, state, newVersion);
    }

    @Override public long insertReceipt(long tenantId, long companyId, String receiptNo, long orderId,
                                        long warehouseId, String orgPath, long createdBy) {
        return mapper.insertReceipt(tenantId, companyId, receiptNo, orderId, warehouseId, orgPath, createdBy);
    }

    @Override public long insertReceiptLine(long tenantId, long receiptId, long orderLineId, long skuId,
                                            String batchNo, BigDecimal qty) {
        return mapper.insertReceiptLine(tenantId, receiptId, orderLineId, skuId, batchNo, qty);
    }

    @Override public boolean hasAnyReceipt(long tenantId, long orderId) {
        return mapper.hasAnyReceipt(tenantId, orderId);
    }
}
