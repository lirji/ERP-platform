package com.lrj.erp.approval.service;

import com.lrj.erp.approval.repository.ApprovalInstanceMapper;
import com.lrj.erp.kernel.error.DomainException;
import com.lrj.erp.kernel.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 内置顺序审批（CAP-P10 的 MVP 实现）。
 *
 * <p>刻意保持很小：MVP 的审批需求是"提交—通过/驳回"，会签、并签、加签、转交
 * 都没有真实场景。提示词第十一章明确要求「不要在缺乏必要性的情况下过度引入复杂 BPM 平台」。
 * 需求出现时按 ROADMAP P8 切换到 workflow-platform 适配器，本实现保留为回落。
 */
@Service
public class BuiltInApprovalService implements ApprovalPort {

    /** 审批错误码（ERP-APR-*）。 */
    public enum ApprovalErrorCode implements ErrorCode {
        INSTANCE_FINISHED("ERP-APR-2001", "审批实例已结束，不能再操作", 409),
        REASON_REQUIRED  ("ERP-APR-3005", "驳回必须填写原因",           422);

        private final String code; private final String message; private final int httpStatus;
        ApprovalErrorCode(String c, String m, int h) { code = c; message = m; httpStatus = h; }
        @Override public String code() { return code; }
        @Override public String message() { return message; }
        @Override public int httpStatus() { return httpStatus; }
    }

    private final ApprovalInstanceMapper mapper;

    public BuiltInApprovalService(ApprovalInstanceMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public long submit(long tenantId, String businessType, String businessId,
                       String documentNo, long submittedBy) {
        mapper.insertPending(tenantId, businessType, businessId, documentNo, submittedBy);
        // 已存在 PENDING 时上面不插入，这里取回既有实例 —— 重复提交是幂等的，
        // 而不是产生两条待办让审批人看到同一张单两次
        return mapper.findPendingId(tenantId, businessType, businessId);
    }

    @Override
    @Transactional
    public void approve(long tenantId, long instanceId, long approvedBy) {
        if (mapper.decide(tenantId, instanceId, "APPROVED", approvedBy, null) == 0) {
            throw new DomainException(ApprovalErrorCode.INSTANCE_FINISHED,
                    Map.of("instanceId", instanceId));
        }
    }

    @Override
    @Transactional
    public void reject(long tenantId, long instanceId, long rejectedBy, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new DomainException(ApprovalErrorCode.REASON_REQUIRED,
                    Map.of("instanceId", instanceId));
        }
        if (mapper.decide(tenantId, instanceId, "REJECTED", rejectedBy, reason) == 0) {
            throw new DomainException(ApprovalErrorCode.INSTANCE_FINISHED,
                    Map.of("instanceId", instanceId));
        }
    }

    /** 精确匹配实例，不能仅凭“某次审批通过”放行业务。 */
    @Override
    public boolean matches(long tenantId, long instanceId, String businessType, String businessId, String status) {
        return mapper.matches(tenantId, instanceId, businessType, businessId, status);
    }

    @Override
    public String statusOf(long tenantId, String businessType, String businessId) {
        return mapper.findLatestStatus(tenantId, businessType, businessId);
    }
}
