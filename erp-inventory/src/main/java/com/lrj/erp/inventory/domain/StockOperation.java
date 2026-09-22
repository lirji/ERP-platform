package com.lrj.erp.inventory.domain;
import java.math.BigDecimal;

/** 单桶库存作业；调拨保留目标桶，在途成本保留调出时事实，不按目标均价重算。 */
public record StockOperation(long id, long tenantId, long companyId, String documentNo,
        String kind, long warehouseId, long locationId, long skuId, String batchNo,
        Long targetWarehouseId, Long targetLocationId, String targetBatchNo,
        BigDecimal quantity, BigDecimal snapshotQuantity, BigDecimal inventoryValue,
        String reason, String state, long version, String reasonCode, Long createdBy) {
    public static final String DOC_TYPE = "STOCK_OPERATION";
    /** 来源库存桶。 */
    public InventoryBucket source() { return new InventoryBucket(tenantId, companyId, warehouseId, locationId, skuId, batchNo); }
    /** 调拨目标库存桶；只供 TRANSFER 调用。 */
    public InventoryBucket target() { return new InventoryBucket(tenantId, companyId, targetWarehouseId, targetLocationId, skuId, targetBatchNo); }
}
