package com.lrj.erp.inventory.domain;
/** 持久化使用稳定名称，与 SQL 约束对应。 */
public enum StockOperationKind {
    TRANSFER("TRANSFER"), COUNT("COUNT"), ADJUST("ADJUST");
    private final String code;
    StockOperationKind(String code) { this.code=code; }
    public String code() { return code; }
}
