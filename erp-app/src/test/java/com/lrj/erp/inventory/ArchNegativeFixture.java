package com.lrj.erp.inventory;

import com.lrj.erp.sales.SalesModule;

/**
 * 故意违规的夹具：库存反向依赖销售。
 *
 * <p><b>这是测试资产，不是产品代码。</b>它存在的唯一理由，是让
 * {@link com.lrj.erp.architecture.ArchRuleEnforcementNegativeTest} 能够证明
 * 「库存不得依赖采购/销售」这条规则确实会拦截，而不是因为当前没有违规而空跑成假绿。
 *
 * <p>提示词禁止事项第 22 条要求架构测试必须真正执行；ROADMAP 的 P0 出口条件 ②
 * 进一步要求提供负向证明。本类即该证明的被检对象。
 *
 * <p><b>实现注意</b>：这里必须用 {@code SalesModule.class} 类字面量，
 * 不能用 {@code SalesModule.MODULE} 这类 {@code static final String} 常量——
 * 编译期常量会被 javac 内联成字面量，字节码中不留下任何对 SalesModule 的引用，
 * ArchUnit 就看不到这次依赖，负向证明会假性通过。这个坑本身值得记住。
 */
@SuppressWarnings("unused")
public final class ArchNegativeFixture {

    /** 类字面量引用会在常量池留下 SalesModule 的类型引用，ArchUnit 可见。 */
    public static Class<?> violatingReference() {
        return SalesModule.class;
    }

    private ArchNegativeFixture() {
    }
}
