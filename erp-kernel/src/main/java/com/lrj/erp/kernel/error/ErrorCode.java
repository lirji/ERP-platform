package com.lrj.erp.kernel.error;

/**
 * 错误码契约。实现见各上下文的枚举；完整码表是 docs/design/erp-platform/contracts/ERROR_CODES.md。
 *
 * <p>为什么用接口而不是一个全局大枚举：全局枚举会让每个上下文的错误码都挤进 kernel，
 * 共享内核随之膨胀——而共享内核的代价是耦合发布（ADR-004）。各上下文自带枚举，
 * kernel 只定义形状。
 */
public interface ErrorCode {

    /** 形如 ERP-INV-1001，一经发布语义不可更改。 */
    String code();

    /** 面向最终用户的中文描述；不得包含表名、SQL、类名、堆栈。 */
    String message();

    /** 映射到的 HTTP 状态码，见 CONTRACTS.md §2.2。 */
    int httpStatus();
}
