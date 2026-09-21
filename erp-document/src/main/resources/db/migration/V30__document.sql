-- =====================================================================
-- 单据状态流转留痕与业务审计（CAP-P07 留痕部分、CAP-P09）。拥有 doc_* 表。
--
-- 刻意不建"万能单据表"：RISK_REGISTER「过度设计」第 1 条明确把
-- 一张 business_document + document_item 万能表列为要阻止的样子。
-- 单据的业务字段留在各自模块的表里；这里只存**流转事实**与**审计事实**。
-- =====================================================================

CREATE TABLE doc_state_transition (
    id             BIGSERIAL    PRIMARY KEY,
    tenant_id      BIGINT       NOT NULL,
    business_type  VARCHAR(48)  NOT NULL,
    business_id    VARCHAR(64)  NOT NULL,
    document_no    VARCHAR(64),
    from_state     VARCHAR(24)  NOT NULL,
    to_state       VARCHAR(24)  NOT NULL,
    event          VARCHAR(24)  NOT NULL,
    from_version   BIGINT       NOT NULL,
    operator_id    BIGINT       NOT NULL,
    trace_id       VARCHAR(64),
    occurred_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

COMMENT ON TABLE  doc_state_transition IS '单据状态流转日志：记录每一次合法迁移，既是审计证据，也是并发迁移的串行化点';
COMMENT ON COLUMN doc_state_transition.id            IS '主键，自增';
COMMENT ON COLUMN doc_state_transition.tenant_id     IS '租户 ID';
COMMENT ON COLUMN doc_state_transition.business_type IS '业务类型，如 PURCHASE_ORDER / SALES_ORDER；与 business_id 共同定位单据';
COMMENT ON COLUMN doc_state_transition.business_id   IS '业务主键，字符串以兼容各模块不同的主键形态';
COMMENT ON COLUMN doc_state_transition.document_no   IS '单据号，冗余存放便于按单号直接检索流转历史';
COMMENT ON COLUMN doc_state_transition.from_state    IS '迁移前状态';
COMMENT ON COLUMN doc_state_transition.to_state      IS '迁移后状态';
COMMENT ON COLUMN doc_state_transition.event         IS '驱动本次迁移的事件';
COMMENT ON COLUMN doc_state_transition.from_version  IS '迁移前的单据版本号；与唯一索引共同保证同一版本只能迁移一次';
COMMENT ON COLUMN doc_state_transition.operator_id   IS '操作人用户 ID';
COMMENT ON COLUMN doc_state_transition.trace_id      IS '请求追踪标识，可关联同一次请求的全部日志';
COMMENT ON COLUMN doc_state_transition.occurred_at   IS '迁移发生时间';

-- 并发控制的核心：同一单据的同一个版本只允许迁移一次。
-- 50 个线程同时基于 version=0 发起迁移时，数据库保证只有一条能插入成功，
-- 其余撞唯一约束失败 —— 这让"并发状态修改只有一个成功"由数据库而非应用层保证。
CREATE UNIQUE INDEX uk_doc_transition_version
    ON doc_state_transition (tenant_id, business_type, business_id, from_version);

CREATE INDEX idx_doc_transition_lookup
    ON doc_state_transition (tenant_id, business_type, business_id, occurred_at DESC);

CREATE TABLE doc_audit_log (
    id             BIGSERIAL    PRIMARY KEY,
    tenant_id      BIGINT       NOT NULL,
    user_id        BIGINT       NOT NULL,
    business_type  VARCHAR(48)  NOT NULL,
    business_id    VARCHAR(64),
    document_no    VARCHAR(64),
    action         VARCHAR(24)  NOT NULL,
    before_value   JSONB,
    after_value    JSONB,
    source_ip      VARCHAR(45),
    user_agent     VARCHAR(256),
    trace_id       VARCHAR(64),
    occurred_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

COMMENT ON TABLE  doc_audit_log IS '业务审计日志：谁在什么时候对哪张单据做了什么、改动前后是什么、来源 IP 与终端';
COMMENT ON COLUMN doc_audit_log.id            IS '主键，自增';
COMMENT ON COLUMN doc_audit_log.tenant_id     IS '租户 ID';
COMMENT ON COLUMN doc_audit_log.user_id       IS '操作人用户 ID（谁）';
COMMENT ON COLUMN doc_audit_log.business_type IS '业务类型（操作了什么）';
COMMENT ON COLUMN doc_audit_log.business_id   IS '业务主键；非单据类操作可为空';
COMMENT ON COLUMN doc_audit_log.document_no   IS '单据号，便于业务人员按单号追溯';
COMMENT ON COLUMN doc_audit_log.action        IS '动作：CREATE/UPDATE/SUBMIT/APPROVE/CANCEL/CLOSE 等';
COMMENT ON COLUMN doc_audit_log.before_value  IS '修改前的值（JSONB）；敏感字段已按脱敏规则处理后落库';
COMMENT ON COLUMN doc_audit_log.after_value   IS '修改后的值（JSONB）；新建时为空的是 before 而非 after';
COMMENT ON COLUMN doc_audit_log.source_ip     IS '来源 IP，长度按 IPv6 最大形态预留 45 位';
COMMENT ON COLUMN doc_audit_log.user_agent    IS '来源终端标识';
COMMENT ON COLUMN doc_audit_log.trace_id      IS '请求追踪标识';
COMMENT ON COLUMN doc_audit_log.occurred_at   IS '操作发生时间（什么时候）';

CREATE INDEX idx_doc_audit_business
    ON doc_audit_log (tenant_id, business_type, business_id, occurred_at DESC);
