-- =====================================================================
-- 采购（CAP-C05、CAP-S09）。拥有 pur_* 表。
--
-- 业务决策（2026-09-21，用户确认）：**不允许超收**。
-- 因此不设超收比例配置项——为一条"不允许"的规则建配置开关是多余的表面。
-- 该决策取代 ROADMAP P4 出口条件 ② 中"配置放开后需审批通过"的假设。
-- =====================================================================

CREATE TABLE pur_request (
    id            BIGSERIAL    PRIMARY KEY,
    tenant_id     BIGINT       NOT NULL,
    company_id    BIGINT       NOT NULL,
    request_no    VARCHAR(64)  NOT NULL,
    supplier_id   BIGINT,
    state         VARCHAR(24)  NOT NULL DEFAULT 'DRAFT',
    version       BIGINT       NOT NULL DEFAULT 0,
    org_path      VARCHAR(512) NOT NULL,
    created_by    BIGINT       NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
COMMENT ON TABLE  pur_request IS '采购申请（CAP-S09）：企业内控通常要求先申请后下单，但不是下单的必要前置';
COMMENT ON COLUMN pur_request.id          IS '主键，自增';
COMMENT ON COLUMN pur_request.tenant_id   IS '租户 ID';
COMMENT ON COLUMN pur_request.company_id  IS '法人主体 ID；数据权限 SPECIFIED_COMPANY 按此列过滤';
COMMENT ON COLUMN pur_request.request_no  IS '申请单号，由编号中心生成，租户内唯一';
COMMENT ON COLUMN pur_request.supplier_id IS '建议供应商；申请阶段可为空';
COMMENT ON COLUMN pur_request.state       IS '单据状态，取值见统一状态机 DocumentState';
COMMENT ON COLUMN pur_request.version     IS '乐观锁版本号；状态迁移以它为并发控制依据';
COMMENT ON COLUMN pur_request.org_path    IS '创建人所属组织物化路径；数据权限前缀过滤用';
COMMENT ON COLUMN pur_request.created_by  IS '创建人用户 ID；数据权限 SELF 按此列过滤';
COMMENT ON COLUMN pur_request.created_at  IS '创建时间';
CREATE UNIQUE INDEX uk_pur_request_no ON pur_request (tenant_id, request_no);

CREATE TABLE pur_order (
    id            BIGSERIAL    PRIMARY KEY,
    tenant_id     BIGINT       NOT NULL,
    company_id    BIGINT       NOT NULL,
    order_no      VARCHAR(64)  NOT NULL,
    supplier_id   BIGINT       NOT NULL,
    supplier_ref  JSONB,
    warehouse_id  BIGINT       NOT NULL,
    state         VARCHAR(24)  NOT NULL DEFAULT 'DRAFT',
    version       BIGINT       NOT NULL DEFAULT 0,
    org_path      VARCHAR(512) NOT NULL,
    created_by    BIGINT       NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
COMMENT ON TABLE  pur_order IS '采购订单：P2P 链路的主单据。收货与入库以它为来源，应付由入库事件派生';
COMMENT ON COLUMN pur_order.id           IS '主键，自增';
COMMENT ON COLUMN pur_order.tenant_id    IS '租户 ID';
COMMENT ON COLUMN pur_order.company_id   IS '法人主体 ID';
COMMENT ON COLUMN pur_order.order_no     IS '采购订单号，由编号中心生成（PO+日期+流水），租户内唯一';
COMMENT ON COLUMN pur_order.supplier_id  IS '供应商 ID';
COMMENT ON COLUMN pur_order.supplier_ref IS '供应商引用快照（MasterDataRef）；供应商改名后本单仍显示下单当时的名称';
COMMENT ON COLUMN pur_order.warehouse_id IS '收货仓库 ID';
COMMENT ON COLUMN pur_order.state        IS '单据状态；PROCESSING 之后不允许取消，只能关闭剩余';
COMMENT ON COLUMN pur_order.version      IS '乐观锁版本号';
COMMENT ON COLUMN pur_order.org_path     IS '创建人所属组织物化路径；数据权限前缀过滤用';
COMMENT ON COLUMN pur_order.created_by   IS '创建人用户 ID';
COMMENT ON COLUMN pur_order.created_at   IS '创建时间';
CREATE UNIQUE INDEX uk_pur_order_no ON pur_order (tenant_id, order_no) WHERE state <> 'CANCELLED';
CREATE INDEX idx_pur_order_scope ON pur_order (tenant_id, org_path text_pattern_ops);

CREATE TABLE pur_order_line (
    id            BIGSERIAL     PRIMARY KEY,
    tenant_id     BIGINT        NOT NULL,
    order_id      BIGINT        NOT NULL,
    line_no       INT           NOT NULL,
    sku_id        BIGINT        NOT NULL,
    sku_ref       JSONB,
    ordered_qty   NUMERIC(18,6) NOT NULL,
    received_qty  NUMERIC(18,6) NOT NULL DEFAULT 0,
    unit_price    NUMERIC(18,6) NOT NULL DEFAULT 0,
    closed        BOOLEAN       NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_pur_line_qty CHECK (ordered_qty > 0 AND received_qty >= 0),
    -- 不允许超收（用户决策）：由 CHECK 约束兜底，应用层的校验只负责给出友好错误
    CONSTRAINT ck_pur_line_no_over_receipt CHECK (received_qty <= ordered_qty)
);
COMMENT ON TABLE  pur_order_line IS '采购订单行。received_qty <= ordered_qty 由 CHECK 约束硬性保证——不允许超收是业务规则，不是配置项';
COMMENT ON COLUMN pur_order_line.id           IS '主键，自增';
COMMENT ON COLUMN pur_order_line.tenant_id    IS '租户 ID';
COMMENT ON COLUMN pur_order_line.order_id     IS '所属采购订单';
COMMENT ON COLUMN pur_order_line.line_no      IS '行号，订单内唯一，供用户按行沟通';
COMMENT ON COLUMN pur_order_line.sku_id       IS 'SKU ID';
COMMENT ON COLUMN pur_order_line.sku_ref      IS 'SKU 引用快照（MasterDataRef）；SKU 改名后本行仍显示下单当时的名称与单位';
COMMENT ON COLUMN pur_order_line.ordered_qty  IS '订购数量，精确十进制';
COMMENT ON COLUMN pur_order_line.received_qty IS '累计已收数量；分批收货时逐次累加，不得超过订购数量';
COMMENT ON COLUMN pur_order_line.unit_price   IS '采购单价；精度高于金额，避免单价×数量先舍入再相乘';
COMMENT ON COLUMN pur_order_line.closed       IS '该行是否已关闭；少收后关闭剩余，已收部分保留';
CREATE UNIQUE INDEX uk_pur_order_line ON pur_order_line (tenant_id, order_id, line_no);

CREATE TABLE pur_receipt (
    id           BIGSERIAL    PRIMARY KEY,
    tenant_id    BIGINT       NOT NULL,
    company_id   BIGINT       NOT NULL,
    receipt_no   VARCHAR(64)  NOT NULL,
    order_id     BIGINT       NOT NULL,
    warehouse_id BIGINT       NOT NULL,
    state        VARCHAR(24)  NOT NULL DEFAULT 'FINISHED',
    org_path     VARCHAR(512) NOT NULL,
    created_by   BIGINT       NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
COMMENT ON TABLE  pur_receipt IS '采购收货单（即入库单）：一张采购订单可分多次收货，每次一张';
COMMENT ON COLUMN pur_receipt.id           IS '主键，自增';
COMMENT ON COLUMN pur_receipt.tenant_id    IS '租户 ID';
COMMENT ON COLUMN pur_receipt.company_id   IS '法人主体 ID';
COMMENT ON COLUMN pur_receipt.receipt_no   IS '收货单号，由编号中心生成，租户内唯一';
COMMENT ON COLUMN pur_receipt.order_id     IS '来源采购订单；单据关系图据此建立父子关系';
COMMENT ON COLUMN pur_receipt.warehouse_id IS '入库仓库 ID';
COMMENT ON COLUMN pur_receipt.state        IS '收货单状态；收货即完成，故默认 FINISHED';
COMMENT ON COLUMN pur_receipt.org_path     IS '创建人所属组织物化路径';
COMMENT ON COLUMN pur_receipt.created_by   IS '创建人用户 ID';
COMMENT ON COLUMN pur_receipt.created_at   IS '收货时间';
CREATE UNIQUE INDEX uk_pur_receipt_no ON pur_receipt (tenant_id, receipt_no);
CREATE INDEX idx_pur_receipt_order ON pur_receipt (tenant_id, order_id);

CREATE TABLE pur_receipt_line (
    id            BIGSERIAL     PRIMARY KEY,
    tenant_id     BIGINT        NOT NULL,
    receipt_id    BIGINT        NOT NULL,
    order_line_id BIGINT        NOT NULL,
    sku_id        BIGINT        NOT NULL,
    batch_no      VARCHAR(64)   NOT NULL DEFAULT '-',
    received_qty  NUMERIC(18,6) NOT NULL,
    CONSTRAINT ck_pur_receipt_line_qty CHECK (received_qty > 0)
);
COMMENT ON TABLE  pur_receipt_line IS '收货单行：每行对应一条采购订单行的一次收货，是库存过账的来源行';
COMMENT ON COLUMN pur_receipt_line.id            IS '主键，自增；作为库存过账的 source_line_id，是 INV-04 幂等键的一部分';
COMMENT ON COLUMN pur_receipt_line.tenant_id     IS '租户 ID';
COMMENT ON COLUMN pur_receipt_line.receipt_id    IS '所属收货单';
COMMENT ON COLUMN pur_receipt_line.order_line_id IS '对应的采购订单行；累计收货量回写到该行';
COMMENT ON COLUMN pur_receipt_line.sku_id        IS 'SKU ID';
COMMENT ON COLUMN pur_receipt_line.batch_no      IS '批次号；批次管理的 SKU 必填，否则用哨兵 ''-''';
COMMENT ON COLUMN pur_receipt_line.received_qty  IS '本次收货数量，必须为正';
