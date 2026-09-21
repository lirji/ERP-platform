package com.lrj.erp.kernel.error;

/** 系统级错误码（ERP-SYS-*）。与 ERROR_CODES.md 的 SYS 段一一对应。 */
public enum SystemErrorCode implements ErrorCode {

    UNEXPECTED          ("ERP-SYS-0001", "系统繁忙，请稍后重试",             500),
    DB_CONNECTION_TIMEOUT("ERP-SYS-0002", "系统繁忙，请稍后重试",            500),
    DB_STATEMENT_TIMEOUT ("ERP-SYS-0003", "查询超时，请缩小查询范围后重试",   500),
    EXTERNAL_UNAVAILABLE ("ERP-SYS-0004", "依赖的外部系统暂不可用",          502),
    OPTIMISTIC_LOCK      ("ERP-SYS-0005", "数据已被他人修改，请刷新后重试",   409),
    UNIQUE_VIOLATION     ("ERP-SYS-0006", "数据已存在",                     409),
    MALFORMED_BODY       ("ERP-SYS-0007", "请求格式错误",                   400),
    INVALID_PAGINATION   ("ERP-SYS-0008", "分页参数非法",                   400),
    INVALID_SORT_FIELD   ("ERP-SYS-0009", "排序字段不被允许",               400),
    IDEMPOTENCY_CONFLICT ("ERP-SYS-0010", "重复请求的内容与首次不一致",       409);

    private final String code;
    private final String message;
    private final int httpStatus;

    SystemErrorCode(String code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    @Override public String code() { return code; }
    @Override public String message() { return message; }
    @Override public int httpStatus() { return httpStatus; }
}
