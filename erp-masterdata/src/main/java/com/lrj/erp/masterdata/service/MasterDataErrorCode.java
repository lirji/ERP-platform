package com.lrj.erp.masterdata.service;

import com.lrj.erp.kernel.error.ErrorCode;

/** 主数据错误码（ERP-MD-*）。与 contracts/API_P2_MASTER_DATA.md §4 一一对应。 */
public enum MasterDataErrorCode implements ErrorCode {

    VALIDATION_FAILED("ERP-MD-1001", "主数据字段校验失败",               400),
    CODE_DUPLICATED  ("ERP-MD-2001", "编码已存在",                       409),
    DISABLED         ("ERP-MD-3001", "主数据已停用，不能被新单据引用",     422),
    KEY_FIELD_LOCKED ("ERP-MD-3002", "主数据已被引用，关键字段不可修改",   422),
    NOT_FOUND        ("ERP-MD-3003", "引用的主数据不存在",                422),
    IMPORT_REJECTED  ("ERP-MD-3004", "存在校验失败行，整批未写入",         422);

    private final String code;
    private final String message;
    private final int httpStatus;

    MasterDataErrorCode(String code, String message, int httpStatus) {
        this.code = code; this.message = message; this.httpStatus = httpStatus;
    }

    @Override public String code() { return code; }
    @Override public String message() { return message; }
    @Override public int httpStatus() { return httpStatus; }
}
