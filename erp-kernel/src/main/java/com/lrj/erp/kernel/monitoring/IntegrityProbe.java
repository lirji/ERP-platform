package com.lrj.erp.kernel.monitoring;
/** 只读、有界对账端口；模块自行查询权威账及流水，监控侧不跨表。 */
public interface IntegrityProbe {
    record Batch(long cursor,long rows,long mismatches) {}
    /** 固定低基数标签，不把租户或单据ID放入指标标签。 */
    String name();
    /** 一次最多1000个聚合，单次SQL快照避免读取提交中的半笔业务。 */
    Batch scan(long afterId,int limit);
}
