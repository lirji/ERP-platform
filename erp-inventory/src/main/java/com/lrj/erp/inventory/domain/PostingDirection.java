package com.lrj.erp.inventory.domain;

/** 过账方向。流水的 quantity 恒为正，方向由本枚举表达。 */
public enum PostingDirection {

    IN("IN", 1),
    OUT("OUT", -1);

    private final String code;
    private final int sign;

    PostingDirection(String code, int sign) { this.code = code; this.sign = sign; }

    public String code() { return code; }

    /** 用于计算带符号数量，使 INV-01 的对账可以直接 SUM。 */
    public int sign() { return sign; }
}
