-- =====================================================================
-- 编号中心（CAP-P06）。拥有 num_* 表。
--
-- 迁移版本段位约定（避免多模块 Flyway 版本冲突，各模块脚本在 classpath 合并）：
--   V1      平台基线（erp-app）
--   V10-19  erp-iam
--   V20-29  erp-numbering
--   V30-39  erp-document
--   V40-49  erp-approval
--   V50+    各业务模块（P2 起分配）
-- =====================================================================

CREATE TABLE num_sequence (
    tenant_id       BIGINT       NOT NULL,
    business_type   VARCHAR(32)  NOT NULL,
    biz_date        DATE         NOT NULL,
    current_value   BIGINT       NOT NULL DEFAULT 0,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_num_sequence PRIMARY KEY (tenant_id, business_type, biz_date)
);

COMMENT ON TABLE  num_sequence IS '单据流水号计数器：按 租户+业务类型+业务日期 维度自增，单语句原子递增保证并发下不重号不空洞';
COMMENT ON COLUMN num_sequence.tenant_id     IS '租户 ID；编号在租户内唯一，不同租户互不影响';
COMMENT ON COLUMN num_sequence.business_type IS '业务类型，如 PO 采购订单 / SO 销售订单 / IN 入库；对应编号前缀';
COMMENT ON COLUMN num_sequence.biz_date      IS '业务日期（租户时区下的日期，无时区）；每日流水从 1 重新开始';
COMMENT ON COLUMN num_sequence.current_value IS '当前已分配到的最大流水值；取号即 +1 并返回新值';
COMMENT ON COLUMN num_sequence.updated_at    IS '最后一次取号时间，供排障与用量观察';

CREATE TABLE num_rule (
    tenant_id       BIGINT       NOT NULL,
    business_type   VARCHAR(32)  NOT NULL,
    prefix          VARCHAR(8)   NOT NULL,
    seq_width       SMALLINT     NOT NULL DEFAULT 6,
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_num_rule PRIMARY KEY (tenant_id, business_type),
    CONSTRAINT ck_num_rule_seq_width CHECK (seq_width BETWEEN 4 AND 10)
);

COMMENT ON TABLE  num_rule IS '编号规则：定义各业务类型的单据号前缀与流水位数，业务人员可维护';
COMMENT ON COLUMN num_rule.tenant_id     IS '租户 ID';
COMMENT ON COLUMN num_rule.business_type IS '业务类型，与 num_sequence.business_type 对应';
COMMENT ON COLUMN num_rule.prefix        IS '单据号前缀，如 PO / SO / IN；与日期和流水拼成完整单据号';
COMMENT ON COLUMN num_rule.seq_width     IS '流水号位数，左侧补零；已启用后不得调小，否则会与历史单号冲突';
COMMENT ON COLUMN num_rule.enabled       IS '是否启用；停用后该业务类型无法取号';
COMMENT ON COLUMN num_rule.created_at    IS '创建时间';
