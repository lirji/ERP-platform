package com.lrj.erp.kernel.statemachine;

import com.lrj.erp.kernel.error.ErrorCode;

/** 单据与状态机错误码（ERP-DOC-*）。与 contracts/ERROR_CODES.md 的 DOC 段一一对应。 */
public enum DocumentErrorCode implements ErrorCode {

    ILLEGAL_TRANSITION("ERP-DOC-2001", "当前状态不允许该操作",             409),
    CONCURRENT_MODIFY ("ERP-DOC-2002", "单据已被他人修改，请刷新后重试",   409),
    GUARD_FAILED      ("ERP-DOC-3001", "业务条件不满足，无法继续",         422),
    DOCUMENT_CLOSED   ("ERP-DOC-3002", "单据已关闭，不接受后续操作",       422);

    private final String code;
    private final String message;
    private final int httpStatus;

    DocumentErrorCode(String code, String message, int httpStatus) {
        this.code = code; this.message = message; this.httpStatus = httpStatus;
    }

    @Override public String code() { return code; }
    @Override public String message() { return message; }
    @Override public int httpStatus() { return httpStatus; }
}
