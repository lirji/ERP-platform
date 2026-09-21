package com.lrj.erp.sales;

/**
 * 销售 模块标记。
 *
 * <p>作用有三：
 * <ol>
 *   <li>以代码形式声明本模块的 <b>Data Ownership</b>：拥有的表前缀为 sal_；</li>
 *   <li>为架构测试提供可见的锚点——空包在字节码中不存在，会让 ArchUnit 规则空跑成假绿；</li>
 *   <li>为未来的物理提取标注边界：本模块当前与其他模块同进程运行，
 *       但 Domain / Module / Logical Service 边界已独立（见 ARCHITECTURE_EVOLUTION.md）。</li>
 * </ol>
 *
 * <p>本接口不应被实现，也不承载任何行为。
 */
public interface SalesModule {

    /** Maven 模块名。 */
    String MODULE = "erp-sales";

    /** 本模块拥有的数据库表前缀；跨模块访问由 TableOwnershipArchitectureTest 拦截。 */
    String TABLE_PREFIX = "sal_";
}
