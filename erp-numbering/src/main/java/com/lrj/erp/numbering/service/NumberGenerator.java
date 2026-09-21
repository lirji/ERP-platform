package com.lrj.erp.numbering.service;

import java.time.LocalDate;

/**
 * 编号中心对内接口（CAP-P06）。模块间通过它取单据号，不直接访问 num_* 表。
 *
 * <p>契约：同一 {@code (tenantId, businessType, businessDate)} 下取出的流水
 * <b>严格递增、不重复、不空洞</b>。
 *
 * <p>「不空洞」的边界：本接口保证<b>分配</b>不空洞。若调用方所在事务随后回滚，
 * 该号即被消耗而不会被复用——这是有意的取舍。让号段随业务事务回滚，需要把取号
 * 纳入业务事务并持有锁到事务结束，会把编号变成全局串行点。ERP 的单号允许存在
 * 极少量因回滚产生的缺号，但绝不允许重号。
 */
public interface NumberGenerator {

    /**
     * 取一个完整单据号，形如 {@code PO20260921000001}。
     *
     * @param tenantId     租户
     * @param businessType 业务类型，须已在 num_rule 中配置且启用
     * @param businessDate 业务日期（租户时区下的日期）
     */
    String next(long tenantId, String businessType, LocalDate businessDate);
}
