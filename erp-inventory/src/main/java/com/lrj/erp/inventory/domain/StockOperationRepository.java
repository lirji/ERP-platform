package com.lrj.erp.inventory.domain;
import java.math.BigDecimal;

/** 库存作业持久化端口；仅操作本模块拥有的数据。 */
public interface StockOperationRepository {
    /** 创建不可变业务内容；数量只允许盘点录入时修改。 */
    long insert(StockOperation operation);
    /** 锁定单据，序列化审批、执行及取消。 */
    StockOperation lock(long tenantId, long id);
    /** 版本受控的状态写入。 */
    boolean transition(StockOperation operation, String next);
    /** 盘点冻结；一桶仅一个未结束盘点。 */
    boolean freeze(InventoryBucket bucket, long id);
    /** 解冻必须匹配盘点归属。 */
    boolean unfreeze(InventoryBucket bucket, long id);
    /** 盘点数量录入，保存差异而非覆盖现存库存。 */
    void recordCount(StockOperation operation, BigDecimal difference);
    /** 记录调出时金额，后续接收携带相同价值。 */
    void recordValue(StockOperation operation, BigDecimal value);
    /** 来源桶在途数量更新，减少时有上界保护。 */
    boolean transit(InventoryBucket bucket, BigDecimal delta);
}
