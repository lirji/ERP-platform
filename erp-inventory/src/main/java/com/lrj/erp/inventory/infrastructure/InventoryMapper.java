package com.lrj.erp.inventory.infrastructure;

import com.lrj.erp.inventory.domain.InventoryBucket;
import com.lrj.erp.inventory.domain.PostingRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

/** 库存持久层。SQL 见 resources/mapper/InventoryMapper.xml。 */
@Mapper
public interface InventoryMapper {

    /** 确保桶存在；并发首次过账由唯一索引保证只建一行。 */
    int ensureBucket(@Param("b") InventoryBucket bucket);

    BalanceRow findBalance(@Param("b") InventoryBucket bucket);

    /**
     * 入库：直接加。
     * @return 影响行数
     */
    int increaseOnHand(@Param("b") InventoryBucket bucket, @Param("qty") BigDecimal qty);

    /**
     * 出库：<b>条件更新</b>，仅当可用量足够时才扣。
     *
     * <p>把"够不够"写进 WHERE，让判断与扣减在同一条语句里原子完成。
     * 先查可用量再扣，在并发下必然超卖——两个线程可以同时读到"够"。
     *
     * @return 影响行数；0 表示可用量不足，<b>不得当作成功</b>
     */
    int decreaseOnHandIfAvailable(@Param("b") InventoryBucket bucket, @Param("qty") BigDecimal qty);

    /**
     * 写流水。撞 uk_inv_txn_source 表示同一来源行已过账（INV-04）。
     * @return 插入行数；0 表示重复过账，应当跳过而不是报错
     */
    int appendTransaction(@Param("r") PostingRequest request,
                          @Param("signedQty") BigDecimal signedQty,
                          @Param("traceId") String traceId);

    /** INV-01 对账：该桶全部流水的代数和。 */
    BigDecimal sumSignedQuantity(@Param("b") InventoryBucket bucket);

    /** 计价必须与库存数量共享同一行锁，避免并发入库使用旧均价。 */
    com.lrj.erp.inventory.domain.CostSnapshot lockCost(@Param("b") InventoryBucket bucket);

    /** 成本查询不加锁。 */
    com.lrj.erp.inventory.domain.CostSnapshot cost(@Param("b") InventoryBucket bucket);

    /** 更新桶成本；影响行数必须为一。 */
    int updateCost(@Param("b") InventoryBucket bucket, @Param("value") BigDecimal value);

    /** 给本事务新建流水记录成本，不改写历史流水。 */
    int recordTransactionCost(@Param("r") PostingRequest request, @Param("value") BigDecimal value);

    /** 只查询本模块的来源成本。 */
    BigDecimal sourceValue(@Param("tenantId") long tenantId, @Param("docType") String docType, @Param("docId") String docId, @Param("lineId") String lineId);

    // ---------------------------------------------------------------- 预占

    int insertReservation(@Param("b") InventoryBucket bucket,
                          @Param("sourceDocType") String sourceDocType,
                          @Param("sourceDocId") String sourceDocId,
                          @Param("sourceLineId") String sourceLineId,
                          @Param("qty") BigDecimal qty);

    /** 预占时把 reserved 加上；仅当可用量足够。 */
    int increaseReservedIfAvailable(@Param("b") InventoryBucket bucket, @Param("qty") BigDecimal qty);

    int decreaseReserved(@Param("b") InventoryBucket bucket, @Param("qty") BigDecimal qty);

    ReservationRow findReservation(@Param("tenantId") long tenantId,
                                   @Param("sourceDocType") String sourceDocType,
                                   @Param("sourceDocId") String sourceDocId,
                                   @Param("sourceLineId") String sourceLineId);

    /** 消耗预占；仅当 consumed+released+qty <= reserved_qty 时成功。 */
    int consumeReservation(@Param("id") long id, @Param("qty") BigDecimal qty);

    /** 释放预占；仅当 consumed+released+qty <= reserved_qty 时成功。 */
    int releaseReservation(@Param("id") long id, @Param("qty") BigDecimal qty);

    record BalanceRow(long id, BigDecimal onHand, BigDecimal reserved,
                      BigDecimal locked, BigDecimal inTransit, long version) { }

    record ReservationRow(long id, BigDecimal reservedQty, BigDecimal consumedQty,
                          BigDecimal releasedQty, long version) { }
}
