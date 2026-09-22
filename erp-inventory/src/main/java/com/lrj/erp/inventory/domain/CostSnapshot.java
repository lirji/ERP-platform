package com.lrj.erp.inventory.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 桶级成本快照；NULL 总成本表示历史依据不足，不能作为零价参与利润核算。 */
public record CostSnapshot(BigDecimal quantity, BigDecimal inventoryValue) {
    /** 清仓带走全部尾差，保证数量归零时成本也归零。调用方持有桶行锁。 */
    public BigDecimal outgoingValue(BigDecimal outgoingQuantity) {
        if (inventoryValue == null) return null;
        if (quantity.compareTo(outgoingQuantity) == 0) return inventoryValue;
        return inventoryValue.multiply(outgoingQuantity).divide(quantity, 6, RoundingMode.HALF_UP);
    }
}
