-- =====================================================================
-- 审批中心（CAP-P10）—— MVP 为内置顺序审批，不引入 BPMN 引擎。
--
-- 补齐说明：同 V32，CAP-P10 列在 P1 能力集但无对应出口条件，P1 未落地。
-- P4 的采购订单需要 APPROVING 态才能流转，在此按 MVP 范围补齐。
--
-- 架构预留：业务模块通过 businessType + businessId 接入，审批中心不认识任何
-- 具体业务模块；P8 触发时可换成 workflow-platform 适配器而不动业务代码。
-- =====================================================================

CREATE TABLE apr_instance (
    id            BIGSERIAL    PRIMARY KEY,
    tenant_id     BIGINT       NOT NULL,
    business_type VARCHAR(48)  NOT NULL,
    business_id   VARCHAR(64)  NOT NULL,
    document_no   VARCHAR(64),
    status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    submitted_by  BIGINT       NOT NULL,
    decided_by    BIGINT,
    decided_at    TIMESTAMPTZ,
    reject_reason VARCHAR(512),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_apr_instance_status CHECK (status IN ('PENDING','APPROVED','REJECTED','WITHDRAWN'))
);

COMMENT ON TABLE  apr_instance IS '审批实例：业务模块通过 businessType+businessId 接入，审批中心不认识具体业务模块';
COMMENT ON COLUMN apr_instance.id            IS '主键，自增';
COMMENT ON COLUMN apr_instance.tenant_id     IS '租户 ID';
COMMENT ON COLUMN apr_instance.business_type IS '业务类型，如 PURCHASE_ORDER；与 business_id 共同定位被审批单据';
COMMENT ON COLUMN apr_instance.business_id   IS '业务主键';
COMMENT ON COLUMN apr_instance.document_no   IS '单据号，冗余存放便于待办列表展示';
COMMENT ON COLUMN apr_instance.status        IS '审批状态：PENDING 待审 / APPROVED 通过 / REJECTED 驳回 / WITHDRAWN 撤回';
COMMENT ON COLUMN apr_instance.submitted_by  IS '提交人用户 ID';
COMMENT ON COLUMN apr_instance.decided_by    IS '审批人用户 ID；未决时为空';
COMMENT ON COLUMN apr_instance.decided_at    IS '审批时间；未决时为空';
COMMENT ON COLUMN apr_instance.reject_reason IS '驳回原因；驳回时必填，便于提交人据此修改';
COMMENT ON COLUMN apr_instance.created_at    IS '提交时间';

-- 同一业务单据同时只允许一个未结束的审批实例，避免重复提交产生两条待办
CREATE UNIQUE INDEX uk_apr_instance_pending
    ON apr_instance (tenant_id, business_type, business_id)
    WHERE status = 'PENDING';
CREATE INDEX idx_apr_instance_lookup ON apr_instance (tenant_id, business_type, business_id);
