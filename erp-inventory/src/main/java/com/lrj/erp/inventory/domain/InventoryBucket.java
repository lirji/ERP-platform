package com.lrj.erp.inventory.domain;

/**
 * 库存桶：台账的主键维度。
 *
 * <p>提示词禁止事项第 8 条禁止把库存做成 {@code sku_id + stock}。
 * 六元组是最小可用维度：少任何一维，都会在多公司、多仓、批次管理出现时
 * 需要改台账主键——那是全量数据迁移。
 *
 * @param locationId MVP 用哨兵 {@link #NO_LOCATION}（假设 A-11）
 * @param batchNo    非批次管理的 SKU 用哨兵 {@link #NO_BATCH}
 */
public record InventoryBucket(long tenantId, long companyId, long warehouseId,
                              long locationId, long skuId, String batchNo) {

    /** 库位未启用时的哨兵。维度先在主键里，启用时只填值、不改结构。 */
    public static final long NO_LOCATION = 0L;

    /** 非批次管理时的批次哨兵。用 '-' 而不是 NULL：NULL 在唯一索引里不等于 NULL，会让同一个桶建出多行。 */
    public static final String NO_BATCH = "-";

    public InventoryBucket {
        if (batchNo == null || batchNo.isBlank()) {
            batchNo = NO_BATCH;
        }
    }

    public static InventoryBucket of(long tenantId, long companyId, long warehouseId, long skuId) {
        return new InventoryBucket(tenantId, companyId, warehouseId, NO_LOCATION, skuId, NO_BATCH);
    }

    public static InventoryBucket ofBatch(long tenantId, long companyId, long warehouseId,
                                          long skuId, String batchNo) {
        return new InventoryBucket(tenantId, companyId, warehouseId, NO_LOCATION, skuId, batchNo);
    }
}
