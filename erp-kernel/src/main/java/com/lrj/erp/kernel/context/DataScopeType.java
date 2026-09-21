package com.lrj.erp.kernel.context;

/**
 * 数据范围类型。语义与 SQL 下推方式见 contracts/API_P1_PLATFORM_KERNEL.md §1.1。
 *
 * <p>持久化与接口使用**显式稳定的 code**，禁止依赖 ordinal（开发规范 §设计 4）：
 * 用 ordinal 意味着在枚举中间插入一个值就会静默改变已落库数据的含义。
 */
public enum DataScopeType {

    SELF("SELF"),
    DEPT("DEPT"),
    DEPT_AND_BELOW("DEPT_AND_BELOW"),
    SPECIFIED_ORG("SPECIFIED_ORG"),
    SPECIFIED_COMPANY("SPECIFIED_COMPANY"),
    ALL("ALL");

    private final String code;

    DataScopeType(String code) { this.code = code; }

    public String code() { return code; }

    /** 未知值显式失败而不是静默回落到 ALL —— 回落到最宽范围是越权。 */
    public static DataScopeType of(String code) {
        for (DataScopeType t : values()) {
            if (t.code.equals(code)) return t;
        }
        throw new IllegalArgumentException("未知的数据范围类型: " + code);
    }
}
