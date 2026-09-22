package com.lrj.erp.inventory.domain;

import java.math.BigDecimal;

/**
 * 库存仓储<b>端口</b>。定义在 domain，由 infrastructure 实现。
 *
 * <p>依赖方向：{@code application → domain ← infrastructure}。
 * 应用层依赖这个接口而不是 MyBatis Mapper——否则用例编排就绑死了持久化技术，
 * 领域规则也无法脱离数据库测试。这条方向由 ModuleDependencyArchitectureTest
 * 的六边形分层规则强制（它曾真的拦下过本类被引入前的实现）。
 *
 * <p>接口用<b>领域语言</b>描述意图，而不是暴露 SQL 细节：
 * {@code decreaseOnHandIfAvailable} 表达的是"可用才扣"这条业务规则，
 * 至于它靠 WHERE 条件还是别的手段实现，是 infrastructure 的事。
 */
public interface InventoryRepository {

    /** 确保桶存在；并发首次过账时收敛为一行。 */
    void ensureBucket(InventoryBucket bucket);

    /** 当前余额；不存在返回全零余额而不是 null。 */
    InventoryBalance findBalance(InventoryBucket bucket);

    void increaseOnHand(InventoryBucket bucket, BigDecimal qty);

    /**
     * 可用量足够才扣减。
     * @return false 表示可用量不足（调用方须判为业务失败，不得当作成功）
     */
    boolean decreaseOnHandIfAvailable(InventoryBucket bucket, BigDecimal qty);

    /**
     * 追加流水；同一来源行同一方向只允许一条（INV-04）。
     * @return false 表示该来源行已过账，应幂等跳过
     */
    boolean appendTransaction(PostingRequest request, BigDecimal signedQuantity, String traceId);

    /** INV-01 对账用：该桶全部流水的代数和。 */
    BigDecimal sumSignedQuantity(InventoryBucket bucket);

    /** 锁定数量和成本快照；调用方须在事务内且已确保桶存在。 */
    CostSnapshot lockCost(InventoryBucket bucket);

    /** 同事务更新余额成本并补齐本次尚未提交的流水成本。 */
    void recordCost(PostingRequest request, BigDecimal signedValue, BigDecimal remainingValue);

    /** 查询账面成本，NULL 表示依据不足。 */
    CostSnapshot cost(InventoryBucket bucket);

    // ---------------------------------------------------------------- 预占

    /** @return false 表示同一来源行已预占（幂等） */
    boolean insertReservation(InventoryBucket bucket, String sourceDocType,
                              String sourceDocId, String sourceLineId, BigDecimal qty);

    /** @return false 表示可用量不足 */
    boolean increaseReservedIfAvailable(InventoryBucket bucket, BigDecimal qty);

    void decreaseReserved(InventoryBucket bucket, BigDecimal qty);

    Reservation findReservation(long tenantId, String sourceDocType,
                                String sourceDocId, String sourceLineId);

    /** @return false 表示超出未消耗量 */
    boolean consumeReservation(long reservationId, BigDecimal qty);

    /** @return false 表示超出未消耗量 */
    boolean releaseReservation(long reservationId, BigDecimal qty);

    /** 预占的领域视图。 */
    record Reservation(long id, BigDecimal reservedQty, BigDecimal consumedQty,
                       BigDecimal releasedQty, long version) {

        /** 尚未消耗也未释放的量——释放的上界。 */
        public BigDecimal remaining() {
            return reservedQty.subtract(consumedQty).subtract(releasedQty);
        }
    }
}
