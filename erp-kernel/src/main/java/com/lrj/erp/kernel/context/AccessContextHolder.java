package com.lrj.erp.kernel.context;

import java.util.Optional;

/**
 * 当前请求的 {@link AccessContext} 持有者。
 *
 * <p>用 {@code ThreadLocal} 而非方法参数逐层传递：判权与数据权限下推发生在持久层拦截器里，
 * 让每个 Mapper 方法都多带一个上下文参数是不现实的。
 *
 * <p><b>必须清理</b>。线程池复用会让下一个请求继承上一个的身份——在多租户系统里
 * 这等于跨租户数据泄漏，且极难排查。清理由 Filter 的 finally 保证。
 *
 * <p>注意：异步线程不会继承本上下文。跨线程使用必须显式传递并在目标线程 set/clear
 * （开发规范 §设计 11：异步线程明确租户、追踪与事务上下文传播及清理）。
 */
public final class AccessContextHolder {

    private static final ThreadLocal<AccessContext> CURRENT = new ThreadLocal<>();

    private AccessContextHolder() { }

    public static void set(AccessContext context) {
        CURRENT.set(context);
    }

    public static Optional<AccessContext> find() {
        return Optional.ofNullable(CURRENT.get());
    }

    /**
     * 取当前上下文；不存在即抛异常。
     *
     * <p>刻意不提供"返回 null 或默认上下文"的重载：默认上下文必然要为 tenantId 取一个值，
     * 而任何默认租户都是错的。缺上下文是编程错误，应当立刻暴露。
     */
    public static AccessContext require() {
        AccessContext ctx = CURRENT.get();
        if (ctx == null) {
            throw new IllegalStateException(
                    "当前线程没有 AccessContext。若在异步线程中使用，必须显式传递并设置。");
        }
        return ctx;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
