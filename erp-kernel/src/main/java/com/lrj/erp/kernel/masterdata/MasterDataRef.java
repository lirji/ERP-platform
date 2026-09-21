package com.lrj.erp.kernel.masterdata;

/**
 * 主数据引用快照。单据引用主数据时，把当时的展示字段一并存下来。
 *
 * <p><b>为什么不是冗余</b>：一张 2026 年的采购单，显示的必须是下单当时的商品名称。
 * 靠 JOIN 取实时值，会让历史单据随主数据改名而"改写自己"——那是在篡改已发生的业务事实。
 *
 * <p>需要「当前值」的场景（主数据管理页、当前可用量）按 {@link #id()} 读实时表；
 * 单据展示一律读快照，不 JOIN。
 *
 * @param id       主数据 ID，用于需要实时值时回查
 * @param code     引用时的编码（关键字段，被引用后即不可改，故快照与实时恒等）
 * @param name     引用时的名称（描述字段，之后可能改变——这正是快照存在的理由）
 * @param unitName 引用时的单位名称
 */
public record MasterDataRef(long id, String code, String name, String unitName) {

    public MasterDataRef {
        if (id <= 0) {
            throw new IllegalArgumentException("主数据引用必须带有效 id");
        }
        if (code == null || code.isBlank()) {
            // 没有 code 的快照在单据上无法显示，也无法与主数据对账
            throw new IllegalArgumentException("主数据引用必须带 code 快照");
        }
    }
}
