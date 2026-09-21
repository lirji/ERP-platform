-- =====================================================================
-- 单据关系图（CAP-P08）。
--
-- 补齐说明：ROADMAP 把 CAP-P08 列在 P1 能力集内，但 P1 的七条出口条件都不涉及它，
-- 因此 P1 只落地了状态流转与审计，关系图未建。P4 的出口条件 ⑤ 要求单据双向可追溯，
-- 在此补齐。该差异已记入 GATE-P4 的能力缺口说明。
-- =====================================================================

CREATE TABLE doc_relation (
    id              BIGSERIAL    PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL,
    parent_type     VARCHAR(48)  NOT NULL,
    parent_id       VARCHAR(64)  NOT NULL,
    parent_no       VARCHAR(64),
    child_type      VARCHAR(48)  NOT NULL,
    child_id        VARCHAR(64)  NOT NULL,
    child_no        VARCHAR(64),
    relation_type   VARCHAR(32)  NOT NULL DEFAULT 'DERIVED_FROM',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

COMMENT ON TABLE  doc_relation IS '单据关系图：记录上下游单据的派生关系，使系统能回答「这张单来自哪里」与「它产生了哪些单」';
COMMENT ON COLUMN doc_relation.id            IS '主键，自增';
COMMENT ON COLUMN doc_relation.tenant_id     IS '租户 ID';
COMMENT ON COLUMN doc_relation.parent_type   IS '上游单据类型，如 PURCHASE_ORDER';
COMMENT ON COLUMN doc_relation.parent_id     IS '上游单据主键';
COMMENT ON COLUMN doc_relation.parent_no     IS '上游单据号，冗余存放便于按单号直接检索，避免为展示再回查上游表';
COMMENT ON COLUMN doc_relation.child_type    IS '下游单据类型，如 PURCHASE_RECEIPT';
COMMENT ON COLUMN doc_relation.child_id      IS '下游单据主键';
COMMENT ON COLUMN doc_relation.child_no      IS '下游单据号，冗余存放，理由同 parent_no';
COMMENT ON COLUMN doc_relation.relation_type IS '关系类型：DERIVED_FROM 派生 / REVERSED_BY 红冲 / SETTLED_BY 结算';
COMMENT ON COLUMN doc_relation.created_at    IS '关系建立时间';

-- 同一对父子单据的同一种关系只登记一次；重复建立不应产生多行
CREATE UNIQUE INDEX uk_doc_relation
    ON doc_relation (tenant_id, parent_type, parent_id, child_type, child_id, relation_type);
-- 正查：从上游找下游
CREATE INDEX idx_doc_relation_parent ON doc_relation (tenant_id, parent_type, parent_id);
-- 反查：从下游找上游。两个方向都要有索引，否则「这张单来自哪里」会退化成全表扫描
CREATE INDEX idx_doc_relation_child  ON doc_relation (tenant_id, child_type, child_id);
