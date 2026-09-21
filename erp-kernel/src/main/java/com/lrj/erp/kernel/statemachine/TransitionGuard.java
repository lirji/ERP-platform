package com.lrj.erp.kernel.statemachine;

/**
 * 迁移守卫：状态允许迁移之后，业务条件是否也允许。
 *
 * <p>把守卫与迁移表分开，是为了让"什么状态能做什么"（结构，各单据一致）
 * 与"这单现在能不能做"（业务，各单据不同）不互相污染。
 * 例如销售订单的 APPROVE 需要检查信用额度，采购订单不需要——
 * 差异写在守卫里，迁移表保持统一。
 */
@FunctionalInterface
public interface TransitionGuard<T> {

    /**
     * @param subject 被迁移的单据
     * @return 不满足时返回拒绝原因；满足返回 null
     */
    String reject(T subject);

    /** 恒通过的守卫。 */
    static <T> TransitionGuard<T> alwaysAllow() {
        return subject -> null;
    }
}
