package com.lrj.erp.inventory.domain;

import java.math.BigDecimal;

/**
 * 库存余额。
 *
 * <p>注意<b>没有</b> {@code available} 字段——它是 {@link #available()} 算出来的。
 * 一旦把可用量落成列，就要在每条写路径上同步维护它；漏掉任何一处都会让可用量
 * 与事实不符，而这种不一致不会自愈，也很难被发现。
 */
public record InventoryBalance(long id, InventoryBucket bucket,
                               BigDecimal onHand, BigDecimal reserved,
                               BigDecimal locked, BigDecimal inTransit, long version) {

    /** 可用量 = 在库 - 预占 - 锁定。<b>算出来的，不是存出来的。</b> */
    public BigDecimal available() {
        return onHand.subtract(reserved).subtract(locked);
    }
}
