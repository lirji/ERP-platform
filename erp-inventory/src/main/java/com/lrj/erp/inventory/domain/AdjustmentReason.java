package com.lrj.erp.inventory.domain;
/** 可检索的调整原因码；详细说明单独保存，避免报表依赖自由文本分类。 */
public enum AdjustmentReason {
    DAMAGE("DAMAGE"), FOUND("FOUND"), CORRECTION("CORRECTION");
    private final String code;
    AdjustmentReason(String code) { this.code=code; }
    public String code() { return code; }
}
