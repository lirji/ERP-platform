package com.lrj.erp.kernel.context;

/**
 * 当前请求的来源信息持有者。
 *
 * <p>与 {@link AccessContextHolder} 一样必须在请求结束时清理——线程池复用会把上一个
 * 请求的来源 IP 带给下一个，让审计记录指向错误的来源，而审计一旦记错就失去了证据价值。
 *
 * <p>取不到时返回 {@link RequestMetadata#UNKNOWN} 而不是抛异常：非 HTTP 入口
 * （定时任务、消息消费）本来就没有来源 IP，审计仍应照常记录。
 */
public final class RequestMetadataHolder {

    private static final ThreadLocal<RequestMetadata> CURRENT = new ThreadLocal<>();

    private RequestMetadataHolder() { }

    public static void set(RequestMetadata metadata) { CURRENT.set(metadata); }

    public static RequestMetadata current() {
        RequestMetadata m = CURRENT.get();
        return m == null ? RequestMetadata.UNKNOWN : m;
    }

    public static void clear() { CURRENT.remove(); }
}
