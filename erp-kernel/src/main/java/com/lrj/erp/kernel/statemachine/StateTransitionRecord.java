package com.lrj.erp.kernel.statemachine;

/**
 * 一次状态迁移的事实。
 *
 * @param fromVersion 迁移前的单据版本；与唯一索引共同保证同一版本只能迁移一次
 */
public record StateTransitionRecord(
        long tenantId,
        String businessType,
        String businessId,
        String documentNo,
        String fromState,
        String toState,
        String event,
        long fromVersion,
        long operatorId,
        String traceId) { }
