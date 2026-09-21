package com.lrj.erp.inventory.application;

import com.lrj.erp.inventory.domain.InventoryBucket;
import com.lrj.erp.inventory.domain.InventoryErrorCode;
import com.lrj.erp.inventory.domain.InventoryRepository;
import com.lrj.erp.kernel.error.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 库存预占（CAP-C03）：销售订单占用可用量，出库时消耗，取消时释放。
 *
 * <p>预占与余额必须一起动：只改预占表不改 {@code inv_balance.reserved}，
 * 可用量就不会因预占而减少，超卖照样发生。
 */
@Service
public class ReservationService {

    private final InventoryRepository repository;

    public ReservationService(InventoryRepository repository) {
        this.repository = repository;
    }

    /**
     * 预占。可用量不足则失败。
     *
     * @return true 表示本次新建了预占；false 表示同一来源行已预占（幂等）
     */
    @Transactional
    public boolean reserve(InventoryBucket bucket, String sourceDocType,
                           String sourceDocId, String sourceLineId, BigDecimal qty) {
        if (qty == null || qty.signum() <= 0) {
            throw new DomainException(InventoryErrorCode.INVALID_POSTING,
                    Map.of("field", "quantity", "reason", "预占数量必须为正"));
        }
        if (!repository.insertReservation(bucket, sourceDocType, sourceDocId, sourceLineId, qty)) {
            return false;
        }
        // 可用量判断写进 WHERE，与预占累加原子完成
        if (!repository.increaseReservedIfAvailable(bucket, qty)) {
            throw new DomainException(InventoryErrorCode.INSUFFICIENT_STOCK,
                    Map.of("skuId", bucket.skuId(), "required", qty, "phase", "RESERVE"));
        }
        return true;
    }

    /**
     * 出库消耗预占：预占转为实际出库，{@code reserved} 相应减少。
     * 余额的 {@code on_hand} 由过账服务扣减，这里只处理预占部分。
     */
    @Transactional
    public void consume(InventoryBucket bucket, String sourceDocType,
                        String sourceDocId, String sourceLineId, BigDecimal qty) {
        InventoryRepository.Reservation r = require(bucket.tenantId(), sourceDocType, sourceDocId, sourceLineId);
        if (!repository.consumeReservation(r.id(), qty)) {
            throw new DomainException(InventoryErrorCode.RELEASE_EXCEEDS,
                    Map.of("reserved", r.reservedQty(), "consumed", r.consumedQty(),
                           "released", r.releasedQty(), "requested", qty, "phase", "CONSUME"));
        }
        repository.decreaseReserved(bucket, qty);
    }

    /**
     * 释放未消耗的预占（取消订单等）。
     *
     * <p>释放量受 {@code consumed + released + qty <= reserved} 约束：
     * 超额释放会把 {@code reserved} 扣成负数，进而让可用量虚高、造成超卖。
     */
    @Transactional
    public void release(InventoryBucket bucket, String sourceDocType,
                        String sourceDocId, String sourceLineId, BigDecimal qty) {
        InventoryRepository.Reservation r = require(bucket.tenantId(), sourceDocType, sourceDocId, sourceLineId);
        if (!repository.releaseReservation(r.id(), qty)) {
            throw new DomainException(InventoryErrorCode.RELEASE_EXCEEDS,
                    Map.of("reserved", r.reservedQty(), "consumed", r.consumedQty(),
                           "released", r.releasedQty(), "requested", qty, "phase", "RELEASE"));
        }
        repository.decreaseReserved(bucket, qty);
    }

    /** 释放全部未消耗的预占（取消订单）。 */
    @Transactional
    public BigDecimal releaseRemaining(InventoryBucket bucket, String sourceDocType,
                                       String sourceDocId, String sourceLineId) {
        InventoryRepository.Reservation r = require(bucket.tenantId(), sourceDocType, sourceDocId, sourceLineId);
        BigDecimal remaining = r.remaining();
        if (remaining.signum() > 0) {
            release(bucket, sourceDocType, sourceDocId, sourceLineId, remaining);
        }
        return remaining;
    }

    public InventoryRepository.Reservation find(long tenantId, String sourceDocType,
                                                String sourceDocId, String sourceLineId) {
        return repository.findReservation(tenantId, sourceDocType, sourceDocId, sourceLineId);
    }

    private InventoryRepository.Reservation require(long tenantId, String sourceDocType,
                                                    String sourceDocId, String sourceLineId) {
        InventoryRepository.Reservation r =
                repository.findReservation(tenantId, sourceDocType, sourceDocId, sourceLineId);
        if (r == null) {
            throw new DomainException(InventoryErrorCode.RESERVATION_NOT_FOUND,
                    Map.of("sourceDocId", sourceDocId, "sourceLineId", sourceLineId));
        }
        return r;
    }
}
