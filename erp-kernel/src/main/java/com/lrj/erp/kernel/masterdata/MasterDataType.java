package com.lrj.erp.kernel.masterdata;

/** 主数据类型。持久化使用显式 code，禁止依赖 ordinal。 */
public enum MasterDataType {

    SKU("SKU"),
    SUPPLIER("SUPPLIER"),
    CUSTOMER("CUSTOMER"),
    WAREHOUSE("WAREHOUSE"),
    UNIT("UNIT");

    private final String code;

    MasterDataType(String code) { this.code = code; }

    public String code() { return code; }

    public static MasterDataType of(String code) {
        for (MasterDataType t : values()) {
            if (t.code.equals(code)) return t;
        }
        throw new IllegalArgumentException("未知的主数据类型: " + code);
    }
}
