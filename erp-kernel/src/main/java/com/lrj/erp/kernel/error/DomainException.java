package com.lrj.erp.kernel.error;

import java.util.Map;

/**
 * 领域异常：业务规则不满足。与系统异常严格分开（开发规范 §设计 10）。
 *
 * <p>领域异常映射为 4xx 并携带错误码；系统异常统一 5xx 且不得把堆栈或 SQL 泄漏给调用方。
 * 把两者混成一个异常类型，最终结果一定是要么泄漏内部细节，要么把业务拒绝报成系统故障。
 */
public class DomainException extends RuntimeException {

    private final transient ErrorCode errorCode;
    /** 结构化补充信息；调用方按字段编程处理，不要去解析 message。 */
    private final transient Map<String, Object> details;

    public DomainException(ErrorCode errorCode) {
        this(errorCode, Map.of());
    }

    public DomainException(ErrorCode errorCode, Map<String, Object> details) {
        super(errorCode.code() + " " + errorCode.message());
        this.errorCode = errorCode;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public ErrorCode errorCode() { return errorCode; }

    public Map<String, Object> details() { return details; }
}
