package com.lrj.erp.inventory.infrastructure;

import com.lrj.erp.inventory.domain.*;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

/**
 * {@link InventoryRepository} 的 MyBatis 实现。
 *
 * <p>把 Mapper 的「影响行数」翻译成领域语义的布尔值：
 * 影响 0 行在持久化层只是一个数字，在领域层则是"可用量不足"或"已过账"这类明确结论。
 * 翻译放在这里，应用层就不必知道任何持久化细节。
 */
@Repository
public class MyBatisInventoryRepository implements InventoryRepository {

    private final InventoryMapper mapper;

    public MyBatisInventoryRepository(InventoryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void ensureBucket(InventoryBucket bucket) {
        mapper.ensureBucket(bucket);
    }

    @Override
    public InventoryBalance findBalance(InventoryBucket bucket) {
        InventoryMapper.BalanceRow row = mapper.findBalance(bucket);
        if (row == null) {
            // 返回全零而不是 null：桶尚未建立与桶内为 0，在业务上是同一件事
            return new InventoryBalance(0, bucket, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, 0);
        }
        return new InventoryBalance(row.id(), bucket, row.onHand(), row.reserved(),
                row.locked(), row.inTransit(), row.version());
    }

    @Override
    public void increaseOnHand(InventoryBucket bucket, BigDecimal qty) {
        mapper.increaseOnHand(bucket, qty);
    }

    @Override
    public boolean decreaseOnHandIfAvailable(InventoryBucket bucket, BigDecimal qty) {
        return mapper.decreaseOnHandIfAvailable(bucket, qty) > 0;
    }

    @Override
    public boolean appendTransaction(PostingRequest request, BigDecimal signedQuantity, String traceId) {
        return mapper.appendTransaction(request, signedQuantity, traceId) > 0;
    }

    @Override
    public BigDecimal sumSignedQuantity(InventoryBucket bucket) {
        return mapper.sumSignedQuantity(bucket);
    }

    @Override
    public boolean insertReservation(InventoryBucket bucket, String sourceDocType,
                                     String sourceDocId, String sourceLineId, BigDecimal qty) {
        return mapper.insertReservation(bucket, sourceDocType, sourceDocId, sourceLineId, qty) > 0;
    }

    @Override
    public boolean increaseReservedIfAvailable(InventoryBucket bucket, BigDecimal qty) {
        return mapper.increaseReservedIfAvailable(bucket, qty) > 0;
    }

    @Override
    public void decreaseReserved(InventoryBucket bucket, BigDecimal qty) {
        mapper.decreaseReserved(bucket, qty);
    }

    @Override
    public Reservation findReservation(long tenantId, String sourceDocType,
                                       String sourceDocId, String sourceLineId) {
        InventoryMapper.ReservationRow r =
                mapper.findReservation(tenantId, sourceDocType, sourceDocId, sourceLineId);
        return r == null ? null
                : new Reservation(r.id(), r.reservedQty(), r.consumedQty(), r.releasedQty(), r.version());
    }

    @Override
    public boolean consumeReservation(long reservationId, BigDecimal qty) {
        return mapper.consumeReservation(reservationId, qty) > 0;
    }

    @Override
    public boolean releaseReservation(long reservationId, BigDecimal qty) {
        return mapper.releaseReservation(reservationId, qty) > 0;
    }
}
