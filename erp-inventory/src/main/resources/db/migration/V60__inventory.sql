-- =====================================================================
-- 库存内核（CAP-C01..C04）。拥有 inv_* 表。本项目最关键的领域。
--
-- 设计要点（见 contracts/API_P3_INVENTORY.md）：
--   · 台账主键是六元组，不是 sku_id + stock
--   · available 没有对应列，是 on_hand - reserved - locked 算出来的
--   · 任何库存变化都有不可变流水，且能反查来源单据行
-- =====================================================================

-- ------------------------------------------------------------- 余额台账
CREATE TABLE inv_balance (
    id           BIGSERIAL     PRIMARY KEY,
    tenant_id    BIGINT        NOT NULL,
    company_id   BIGINT        NOT NULL,
    warehouse_id BIGINT        NOT NULL,
    location_id  BIGINT        NOT NULL DEFAULT 0,
    sku_id       BIGINT        NOT NULL,
    batch_no     VARCHAR(64)   NOT NULL DEFAULT '-',
    on_hand      NUMERIC(18,6) NOT NULL DEFAULT 0,
    reserved     NUMERIC(18,6) NOT NULL DEFAULT 0,
    locked       NUMERIC(18,6) NOT NULL DEFAULT 0,
    in_transit   NUMERIC(18,6) NOT NULL DEFAULT 0,
    version      BIGINT        NOT NULL DEFAULT 0,
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_inv_balance_nonneg CHECK (on_hand >= 0 AND reserved >= 0 AND locked >= 0 AND in_transit >= 0),
    CONSTRAINT ck_inv_balance_reserved CHECK (reserved + locked <= on_hand)
);

COMMENT ON TABLE  inv_balance IS '库存余额台账。主键维度为 租户+公司+仓库+库位+SKU+批次 六元组；available 不设列，由 on_hand-reserved-locked 算出';
COMMENT ON COLUMN inv_balance.id           IS '主键，自增';
COMMENT ON COLUMN inv_balance.tenant_id    IS '租户 ID';
COMMENT ON COLUMN inv_balance.company_id   IS '法人主体 ID；跨公司调拨需要区分归属';
COMMENT ON COLUMN inv_balance.warehouse_id IS '仓库 ID';
COMMENT ON COLUMN inv_balance.location_id  IS '库位 ID；MVP 按假设 A-11 不启用，用哨兵 0 占位。维度先进主键，启用时只需填值而不改主键结构';
COMMENT ON COLUMN inv_balance.sku_id       IS 'SKU ID';
COMMENT ON COLUMN inv_balance.batch_no     IS '批次号；非批次管理的 SKU 用哨兵 ''-''。批次是台账主键的一部分，后加维度等于全量数据迁移';
COMMENT ON COLUMN inv_balance.on_hand      IS '实际在库数量，精确十进制';
COMMENT ON COLUMN inv_balance.reserved     IS '已被销售订单预占的数量';
COMMENT ON COLUMN inv_balance.locked       IS '质检/冻结等锁定的数量';
COMMENT ON COLUMN inv_balance.in_transit   IS '调拨在途数量；企业维度数量守恒（INV-08）需要它';
COMMENT ON COLUMN inv_balance.version      IS '乐观锁版本号；更新须检查影响行数，为 0 即冲突，不得当作成功';
COMMENT ON COLUMN inv_balance.updated_at   IS '最后变动时间';

-- 台账唯一性即桶的定义；并发首次过账时由它保证只建一行
CREATE UNIQUE INDEX uk_inv_balance_bucket
    ON inv_balance (tenant_id, company_id, warehouse_id, location_id, sku_id, batch_no);

-- ------------------------------------------------------------- 不可变流水
CREATE TABLE inv_transaction (
    id              BIGSERIAL     PRIMARY KEY,
    tenant_id       BIGINT        NOT NULL,
    company_id      BIGINT        NOT NULL,
    warehouse_id    BIGINT        NOT NULL,
    location_id     BIGINT        NOT NULL DEFAULT 0,
    sku_id          BIGINT        NOT NULL,
    batch_no        VARCHAR(64)   NOT NULL DEFAULT '-',
    direction       VARCHAR(8)    NOT NULL,
    biz_type        VARCHAR(32)   NOT NULL,
    quantity        NUMERIC(18,6) NOT NULL,
    signed_quantity NUMERIC(18,6) NOT NULL,
    source_doc_type VARCHAR(48)   NOT NULL,
    source_doc_id   VARCHAR(64)   NOT NULL,
    source_line_id  VARCHAR(64)   NOT NULL,
    operator_id     BIGINT        NOT NULL,
    trace_id        VARCHAR(64),
    posted_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_inv_txn_qty_positive CHECK (quantity > 0),
    CONSTRAINT ck_inv_txn_direction CHECK (direction IN ('IN', 'OUT'))
);

COMMENT ON TABLE  inv_transaction IS '库存流水（不可变台账）：任何库存变化都在此留痕且可反查来源单据行。余额必须等于该桶全部流水的代数和（INV-01）';
COMMENT ON COLUMN inv_transaction.id              IS '主键，自增';
COMMENT ON COLUMN inv_transaction.tenant_id       IS '租户 ID';
COMMENT ON COLUMN inv_transaction.company_id      IS '法人主体 ID';
COMMENT ON COLUMN inv_transaction.warehouse_id    IS '仓库 ID';
COMMENT ON COLUMN inv_transaction.location_id     IS '库位 ID；与余额台账同维度';
COMMENT ON COLUMN inv_transaction.sku_id          IS 'SKU ID';
COMMENT ON COLUMN inv_transaction.batch_no        IS '批次号；与余额台账同维度';
COMMENT ON COLUMN inv_transaction.direction       IS '方向：IN 入库 / OUT 出库';
COMMENT ON COLUMN inv_transaction.biz_type        IS '业务类型：PURCHASE_IN/SALES_OUT/TRANSFER_IN/TRANSFER_OUT/ADJUST/COUNT 等';
COMMENT ON COLUMN inv_transaction.quantity        IS '数量绝对值，恒为正；方向由 direction 表达';
COMMENT ON COLUMN inv_transaction.signed_quantity IS '带符号数量（入为正、出为负）。冗余存放是为了让 INV-01 的对账可以直接 SUM，而不必在查询里按方向做 CASE';
COMMENT ON COLUMN inv_transaction.source_doc_type IS '来源单据类型；没有来源的库存变化不允许存在';
COMMENT ON COLUMN inv_transaction.source_doc_id   IS '来源单据主键';
COMMENT ON COLUMN inv_transaction.source_line_id  IS '来源单据行主键；幂等键的一部分';
COMMENT ON COLUMN inv_transaction.operator_id     IS '操作人用户 ID';
COMMENT ON COLUMN inv_transaction.trace_id        IS '请求追踪标识，可串联同一次过账的全部日志';
COMMENT ON COLUMN inv_transaction.posted_at       IS '过账时间';

-- INV-04 幂等：同一来源行同一方向只能过账一次。由唯一索引保证，不靠应用层判重
CREATE UNIQUE INDEX uk_inv_txn_source
    ON inv_transaction (tenant_id, source_doc_type, source_doc_id, source_line_id, direction);
-- 对账与流水查询：按桶取全部流水
CREATE INDEX idx_inv_txn_bucket
    ON inv_transaction (tenant_id, company_id, warehouse_id, location_id, sku_id, batch_no, posted_at);

-- ------------------------------------------------------------- 预占
CREATE TABLE inv_reservation (
    id            BIGSERIAL     PRIMARY KEY,
    tenant_id     BIGINT        NOT NULL,
    company_id    BIGINT        NOT NULL,
    warehouse_id  BIGINT        NOT NULL,
    location_id   BIGINT        NOT NULL DEFAULT 0,
    sku_id        BIGINT        NOT NULL,
    batch_no      VARCHAR(64)   NOT NULL DEFAULT '-',
    source_doc_type VARCHAR(48) NOT NULL,
    source_doc_id VARCHAR(64)   NOT NULL,
    source_line_id VARCHAR(64)  NOT NULL,
    reserved_qty  NUMERIC(18,6) NOT NULL,
    consumed_qty  NUMERIC(18,6) NOT NULL DEFAULT 0,
    released_qty  NUMERIC(18,6) NOT NULL DEFAULT 0,
    version       BIGINT        NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_inv_resv_positive CHECK (reserved_qty > 0 AND consumed_qty >= 0 AND released_qty >= 0),
    CONSTRAINT ck_inv_resv_balance CHECK (consumed_qty + released_qty <= reserved_qty)
);

COMMENT ON TABLE  inv_reservation IS '库存预占：销售订单占用可用量。消耗+释放不得超过预占总量，由 CHECK 约束兜底';
COMMENT ON COLUMN inv_reservation.id              IS '主键，自增';
COMMENT ON COLUMN inv_reservation.tenant_id       IS '租户 ID';
COMMENT ON COLUMN inv_reservation.company_id      IS '法人主体 ID';
COMMENT ON COLUMN inv_reservation.warehouse_id    IS '仓库 ID';
COMMENT ON COLUMN inv_reservation.location_id     IS '库位 ID';
COMMENT ON COLUMN inv_reservation.sku_id          IS 'SKU ID';
COMMENT ON COLUMN inv_reservation.batch_no        IS '批次号';
COMMENT ON COLUMN inv_reservation.source_doc_type IS '预占来源单据类型，如 SALES_ORDER';
COMMENT ON COLUMN inv_reservation.source_doc_id   IS '预占来源单据主键';
COMMENT ON COLUMN inv_reservation.source_line_id  IS '预占来源单据行主键';
COMMENT ON COLUMN inv_reservation.reserved_qty    IS '预占总量';
COMMENT ON COLUMN inv_reservation.consumed_qty    IS '已被出库消耗的量';
COMMENT ON COLUMN inv_reservation.released_qty    IS '已释放的量；释放不得超过 reserved_qty - consumed_qty，否则 reserved 会被扣成负数';
COMMENT ON COLUMN inv_reservation.version         IS '乐观锁版本号';
COMMENT ON COLUMN inv_reservation.created_at      IS '创建时间';

-- 同一来源行只允许一条预占记录，避免重复预占
CREATE UNIQUE INDEX uk_inv_resv_source
    ON inv_reservation (tenant_id, source_doc_type, source_doc_id, source_line_id);
