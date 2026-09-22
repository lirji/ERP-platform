package com.lrj.erp.approval.service;

/**
 * 审批端口（CAP-P10）。业务模块通过 {@code businessType + businessId} 接入，
 * <b>审批中心不认识任何具体业务模块</b>——这是 erp-approval 不依赖业务模块的前提
 * （MODULE_SERVICE_MAP §2；反过来依赖会立刻被 ArchUnit 拦截）。
 *
 * <p>MVP 为内置顺序审批。P8 触发时换成 workflow-platform 适配器，
 * 业务模块代码不动——端口先行正是为了这次切换。
 */
public interface ApprovalPort {

    /** 提交审批。同一业务单据存在未结束实例时返回该实例（幂等）。 */
    long submit(long tenantId, String businessType, String businessId,
                String documentNo, long submittedBy);

    /** 通过。 */
    void approve(long tenantId, long instanceId, long approvedBy);

    /** 驳回，必须给原因——没有原因的驳回让提交人无从修改。 */
    void reject(long tenantId, long instanceId, long rejectedBy, String reason);

    /** 校验指定实例的业务归属与状态，防止把其他单据的审批结果用于本单。 */
    boolean matches(long tenantId, long instanceId, String businessType, String businessId, String status);

    /** 当前状态：PENDING / APPROVED / REJECTED / WITHDRAWN；无实例返回 null。 */
    String statusOf(long tenantId, String businessType, String businessId);
}
