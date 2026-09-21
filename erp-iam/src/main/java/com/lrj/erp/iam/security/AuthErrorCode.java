package com.lrj.erp.iam.security;

import com.lrj.erp.kernel.error.ErrorCode;

/** 认证与授权错误码（ERP-AUTH-*）。与 contracts/ERROR_CODES.md 的 AUTH 段一一对应。 */
public enum AuthErrorCode implements ErrorCode {

    NO_CREDENTIAL      ("ERP-AUTH-0001", "请先登录",               401),
    INVALID_CREDENTIAL ("ERP-AUTH-0002", "登录已失效，请重新登录",   401),
    PERMISSION_DENIED  ("ERP-AUTH-4001", "没有该操作的权限",         403),
    /**
     * 资源不在数据权限范围内时刻意返回 404 而不是 403：
     * 403 会泄漏"这个 ID 存在"，在多租户系统里等于跨租户存在性探测。
     * 见 contracts/CONTRACTS.md §2.2。
     */
    OUT_OF_DATA_SCOPE  ("ERP-AUTH-4002", "资源不存在",             404),
    TENANT_DISABLED    ("ERP-AUTH-4003", "租户已停用",             403),
    USER_DISABLED      ("ERP-AUTH-4004", "账号已停用",             403);

    private final String code;
    private final String message;
    private final int httpStatus;

    AuthErrorCode(String code, String message, int httpStatus) {
        this.code = code; this.message = message; this.httpStatus = httpStatus;
    }

    @Override public String code() { return code; }
    @Override public String message() { return message; }
    @Override public int httpStatus() { return httpStatus; }
}
