package com.lrj.erp.inventory.domain;

import java.math.BigDecimal;

/**
 * 一次过账请求。
 *
 * <p>来源单据三元组是<b>必填</b>：没有来源的库存变化不允许存在——
 * 那正是「账实不符且无法追溯」的根源。它同时是 INV-04 的幂等键。
 */
public record PostingRequest(InventoryBucket bucket,
                             PostingDirection direction,
                             BigDecimal quantity,
                             String bizType,
                             String sourceDocType,
                             String sourceDocId,
                             String sourceLineId,
                             long operatorId,
                             BigDecimal inboundValue) {
    /** 兼容既有无成本调用；未知成本不能伪装为零成本。 */
    public PostingRequest(InventoryBucket bucket, PostingDirection direction, BigDecimal quantity,
                          String bizType, String sourceDocType, String sourceDocId,
                          String sourceLineId, long operatorId) {
        this(bucket, direction, quantity, bizType, sourceDocType, sourceDocId, sourceLineId, operatorId, null);
    }
}
