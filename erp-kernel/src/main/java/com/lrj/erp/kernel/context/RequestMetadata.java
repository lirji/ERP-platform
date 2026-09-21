package com.lrj.erp.kernel.context;

/**
 * 请求的来源信息。与 {@link AccessContext} 分开，因为二者生命周期与来源不同：
 * AccessContext 来自已验证的身份，RequestMetadata 来自传输层，且在<b>未登录</b>时也存在。
 *
 * @param sourceIp  来源 IP（审计要求"来源IP"）
 * @param userAgent 来源终端（审计要求"来源终端"）
 */
public record RequestMetadata(String sourceIp, String userAgent) {

    public static final RequestMetadata UNKNOWN = new RequestMetadata(null, null);
}
