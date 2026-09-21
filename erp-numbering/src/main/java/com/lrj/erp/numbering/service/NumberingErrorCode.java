package com.lrj.erp.numbering.service;

import com.lrj.erp.kernel.error.ErrorCode;

/** 编号中心错误码（ERP-NUM-*）。与 contracts/ERROR_CODES.md 的 NUM 段一一对应。 */
public enum NumberingErrorCode implements ErrorCode {

    RULE_IN_USE       ("ERP-NUM-2001", "编号规则已被使用，不能修改流水位数", 409),
    SEQUENCE_EXHAUSTED("ERP-NUM-3001", "当日单据号已达上限",               422),
    RULE_NOT_FOUND    ("ERP-NUM-3002", "未配置该业务类型的编号规则",         422);

    private final String code;
    private final String message;
    private final int httpStatus;

    NumberingErrorCode(String code, String message, int httpStatus) {
        this.code = code; this.message = message; this.httpStatus = httpStatus;
    }

    @Override public String code() { return code; }
    @Override public String message() { return message; }
    @Override public int httpStatus() { return httpStatus; }
}
