-- =====================================================================
-- 销售（CAP-C06、CAP-S06）。拥有 sal_* 表。
--
-- O2C 前半链路：销售订单 → 审批 → 库存预占 → 出库 → 发货 → 签收。
-- 预占复用 P3 的 inv_reservation，销售侧只记录自己的单据与信用占用。
-- =====================================================================

CREATE TABLE sal_order (
    id            BIGSERIAL     PRIMARY KEY,
    tenant_id     BIGINT        NOT NULL,
    company_id    BIGINT        NOT NULL,
    order_no      VARCHAR(64)   NOT NULL,
    customer_id   BIGINT        NOT NULL,
    customer_ref  JSONB,
    warehouse_id  BIGINT        NOT NULL,
    total_amount  NUMERIC(18,4) NOT NULL DEFAULT 0,
    state         VARCHAR(24)   NOT NULL DEFAULT 'DRAFT',
    reserved      BOOLEAN       NOT NULL DEFAULT FALSE,
    version       BIGINT        NOT NULL DEFAULT 0,
    org_path      VARCHAR(512)  NOT NULL,
    created_by    BIGINT        NOT NULL,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);
COMMENT ON TABLE  sal_order IS '销售订单：O2C 链路的主单据。出库以它为来源，应收由出库事件派生';
COMMENT ON COLUMN sal_order.id           IS '主键，自增';
COMMENT ON COLUMN sal_order.tenant_id    IS '租户 ID';
COMMENT ON COLUMN sal_order.company_id   IS '法人主体 ID';
COMMENT ON COLUMN sal_order.order_no     IS '销售订单号，由编号中心生成（SO+日期+流水），租户内唯一';
COMMENT ON COLUMN sal_order.customer_id  IS '客户 ID';
COMMENT ON COLUMN sal_order.customer_ref IS '客户引用快照（MasterDataRef）；客户改名后本单仍显示下单当时的名称';
COMMENT ON COLUMN sal_order.warehouse_id IS '发货仓库 ID';
COMMENT ON COLUMN sal_order.total_amount IS '订单总金额，精确十进制；信用占用以它为准';
COMMENT ON COLUMN sal_order.state        IS '单据状态；PROCESSING 之后不允许取消，只能关闭剩余';
COMMENT ON COLUMN sal_order.reserved     IS '是否已完成库存预占；用于避免重复预占，也用于取消时判断是否需要释放';
COMMENT ON COLUMN sal_order.version      IS '乐观锁版本号';
COMMENT ON COLUMN sal_order.org_path     IS '创建人所属组织物化路径；数据权限前缀过滤用';
COMMENT ON COLUMN sal_order.created_by   IS '创建人用户 ID；数据权限 SELF 按此列过滤';
COMMENT ON COLUMN sal_order.created_at   IS '创建时间';
CREATE UNIQUE INDEX uk_sal_order_no ON sal_order (tenant_id, order_no) WHERE state <> 'CANCELLED';
CREATE INDEX idx_sal_order_scope ON sal_order (tenant_id, org_path text_pattern_ops);

CREATE TABLE sal_order_line (
    id           BIGSERIAL     PRIMARY KEY,
    tenant_id    BIGINT        NOT NULL,
    order_id     BIGINT        NOT NULL,
    line_no      INT           NOT NULL,
    sku_id       BIGINT        NOT NULL,
    sku_ref      JSONB,
    batch_no     VARCHAR(64)   NOT NULL DEFAULT '-',
    ordered_qty  NUMERIC(18,6) NOT NULL,
    shipped_qty  NUMERIC(18,6) NOT NULL DEFAULT 0,
    unit_price   NUMERIC(18,6) NOT NULL DEFAULT 0,
    CONSTRAINT ck_sal_line_qty CHECK (ordered_qty > 0 AND shipped_qty >= 0),
    -- 出库不得超过订购量；与采购侧不允许超收同理，由约束兜底而非应用层判断
    CONSTRAINT ck_sal_line_no_over_ship CHECK (shipped_qty <= ordered_qty)
);
COMMENT ON TABLE  sal_order_line IS '销售订单行。shipped_qty <= ordered_qty 由 CHECK 约束保证，防止分批出库时并发超发';
COMMENT ON COLUMN sal_order_line.id          IS '主键，自增';
COMMENT ON COLUMN sal_order_line.tenant_id   IS '租户 ID';
COMMENT ON COLUMN sal_order_line.order_id    IS '所属销售订单';
COMMENT ON COLUMN sal_order_line.line_no     IS '行号，订单内唯一';
COMMENT ON COLUMN sal_order_line.sku_id      IS 'SKU ID';
COMMENT ON COLUMN sal_order_line.sku_ref     IS 'SKU 引用快照；SKU 改名后本行仍显示下单当时的名称与单位';
COMMENT ON COLUMN sal_order_line.batch_no    IS '指定批次；不指定批次时用哨兵 ''-''，与库存桶维度对齐';
COMMENT ON COLUMN sal_order_line.ordered_qty IS '订购数量';
COMMENT ON COLUMN sal_order_line.shipped_qty IS '累计已出库数量；分批发货时逐次累加';
COMMENT ON COLUMN sal_order_line.unit_price  IS '销售单价；精度高于金额，避免单价×数量先舍入再相乘';
CREATE UNIQUE INDEX uk_sal_order_line ON sal_order_line (tenant_id, order_id, line_no);

CREATE TABLE sal_shipment (
    id           BIGSERIAL    PRIMARY KEY,
    tenant_id    BIGINT       NOT NULL,
    company_id   BIGINT       NOT NULL,
    shipment_no  VARCHAR(64)  NOT NULL,
    order_id     BIGINT       NOT NULL,
    warehouse_id BIGINT       NOT NULL,
    state        VARCHAR(24)  NOT NULL DEFAULT 'PROCESSING',
    delivered_at TIMESTAMPTZ,
    signed_at    TIMESTAMPTZ,
    org_path     VARCHAR(512) NOT NULL,
    created_by   BIGINT       NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
COMMENT ON TABLE  sal_shipment IS '发货单（即出库单）：一张销售订单可分多批发货，每批一张。签收后才算履约完成';
COMMENT ON COLUMN sal_shipment.id           IS '主键，自增';
COMMENT ON COLUMN sal_shipment.tenant_id    IS '租户 ID';
COMMENT ON COLUMN sal_shipment.company_id   IS '法人主体 ID';
COMMENT ON COLUMN sal_shipment.shipment_no  IS '发货单号，由编号中心生成，租户内唯一';
COMMENT ON COLUMN sal_shipment.order_id     IS '来源销售订单；单据关系图据此建立父子关系';
COMMENT ON COLUMN sal_shipment.warehouse_id IS '出库仓库 ID';
COMMENT ON COLUMN sal_shipment.state        IS '发货单状态：PROCESSING 已出库待发运 / FINISHED 已签收';
COMMENT ON COLUMN sal_shipment.delivered_at IS '发运时间；出库与发运是两件事，出库后可能暂存待发';
COMMENT ON COLUMN sal_shipment.signed_at    IS '客户签收时间；签收是应收确认的业务依据';
COMMENT ON COLUMN sal_shipment.org_path     IS '创建人所属组织物化路径';
COMMENT ON COLUMN sal_shipment.created_by   IS '创建人用户 ID';
COMMENT ON COLUMN sal_shipment.created_at   IS '出库时间';
CREATE UNIQUE INDEX uk_sal_shipment_no ON sal_shipment (tenant_id, shipment_no);
CREATE INDEX idx_sal_shipment_order ON sal_shipment (tenant_id, order_id);

CREATE TABLE sal_shipment_line (
    id            BIGSERIAL     PRIMARY KEY,
    tenant_id     BIGINT        NOT NULL,
    shipment_id   BIGINT        NOT NULL,
    order_line_id BIGINT        NOT NULL,
    sku_id        BIGINT        NOT NULL,
    batch_no      VARCHAR(64)   NOT NULL DEFAULT '-',
    shipped_qty   NUMERIC(18,6) NOT NULL,
    CONSTRAINT ck_sal_shipment_line_qty CHECK (shipped_qty > 0)
);
COMMENT ON TABLE  sal_shipment_line IS '发货单行：每行对应一条销售订单行的一次出库，是库存过账的来源行';
COMMENT ON COLUMN sal_shipment_line.id            IS '主键，自增；作为库存过账的 source_line_id，是 INV-04 幂等键的一部分';
COMMENT ON COLUMN sal_shipment_line.tenant_id     IS '租户 ID';
COMMENT ON COLUMN sal_shipment_line.shipment_id   IS '所属发货单';
COMMENT ON COLUMN sal_shipment_line.order_line_id IS '对应的销售订单行；累计出库量回写到该行';
COMMENT ON COLUMN sal_shipment_line.sku_id        IS 'SKU ID';
COMMENT ON COLUMN sal_shipment_line.batch_no      IS '批次号，与库存桶维度对齐';
COMMENT ON COLUMN sal_shipment_line.shipped_qty   IS '本次出库数量，必须为正';

CREATE TABLE sal_credit_account (
    id          BIGSERIAL     PRIMARY KEY,
    tenant_id   BIGINT        NOT NULL,
    customer_id BIGINT        NOT NULL,
    used_amount NUMERIC(18,4) NOT NULL DEFAULT 0,
    version     BIGINT        NOT NULL DEFAULT 0,
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_sal_credit_nonneg CHECK (used_amount >= 0)
);
COMMENT ON TABLE  sal_credit_account IS '客户信用占用（CAP-S06）。只存已占用额度；授信上限来自 md_customer.credit_limit，不在此冗余——两处存同一个上限必然出现不一致';
COMMENT ON COLUMN sal_credit_account.id          IS '主键，自增';
COMMENT ON COLUMN sal_credit_account.tenant_id   IS '租户 ID';
COMMENT ON COLUMN sal_credit_account.customer_id IS '客户 ID';
COMMENT ON COLUMN sal_credit_account.used_amount IS '已占用信用额度；下单时占用，取消订单时释放。不得为负，由 CHECK 约束保证';
COMMENT ON COLUMN sal_credit_account.version     IS '乐观锁版本号';
COMMENT ON COLUMN sal_credit_account.updated_at  IS '最后变动时间';
CREATE UNIQUE INDEX uk_sal_credit_customer ON sal_credit_account (tenant_id, customer_id);
