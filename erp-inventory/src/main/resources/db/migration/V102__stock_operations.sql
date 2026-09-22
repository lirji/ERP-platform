CREATE TABLE inv_stock_operation (
    id bigserial PRIMARY KEY,
    tenant_id bigint NOT NULL,
    company_id bigint NOT NULL,
    document_no varchar(64) NOT NULL,
    kind varchar(16) NOT NULL CHECK (kind IN ('TRANSFER','COUNT','ADJUST')),
    warehouse_id bigint NOT NULL,
    location_id bigint NOT NULL,
    sku_id bigint NOT NULL,
    batch_no varchar(64) NOT NULL,
    target_warehouse_id bigint,
    target_location_id bigint,
    target_batch_no varchar(64),
    quantity numeric(18,6),
    snapshot_quantity numeric(18,6) NOT NULL,
    inventory_value numeric(24,6),
    reason varchar(256) NOT NULL,
    state varchar(32) NOT NULL DEFAULT 'DRAFT',
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id,document_no),
    CHECK (kind <> 'TRANSFER' OR (quantity > 0 AND target_warehouse_id IS NOT NULL AND target_location_id IS NOT NULL AND target_batch_no IS NOT NULL)),
    CHECK (inventory_value >= 0)
);
COMMENT ON TABLE inv_stock_operation IS '调拨、盘点与调整的审批及执行事实';
COMMENT ON COLUMN inv_stock_operation.id IS '作业主键';
COMMENT ON COLUMN inv_stock_operation.tenant_id IS '租户';
COMMENT ON COLUMN inv_stock_operation.company_id IS '公司';
COMMENT ON COLUMN inv_stock_operation.document_no IS '作业编号';
COMMENT ON COLUMN inv_stock_operation.kind IS '作业类型';
COMMENT ON COLUMN inv_stock_operation.warehouse_id IS '来源仓库';
COMMENT ON COLUMN inv_stock_operation.location_id IS '来源库位';
COMMENT ON COLUMN inv_stock_operation.sku_id IS 'SKU';
COMMENT ON COLUMN inv_stock_operation.batch_no IS '来源批次';
COMMENT ON COLUMN inv_stock_operation.target_warehouse_id IS '调拨目标仓库';
COMMENT ON COLUMN inv_stock_operation.target_location_id IS '调拨目标库位';
COMMENT ON COLUMN inv_stock_operation.target_batch_no IS '调拨目标批次';
COMMENT ON COLUMN inv_stock_operation.quantity IS '调拨数量或盘点调整差异；盘点未录入时为空';
COMMENT ON COLUMN inv_stock_operation.snapshot_quantity IS '建单时来源在库快照';
COMMENT ON COLUMN inv_stock_operation.inventory_value IS '调整入库或在途总成本；NULL 表示未知';
COMMENT ON COLUMN inv_stock_operation.reason IS '作业原因';
COMMENT ON COLUMN inv_stock_operation.state IS '单据状态';
COMMENT ON COLUMN inv_stock_operation.version IS '状态版本';
COMMENT ON COLUMN inv_stock_operation.created_at IS '创建时间';
ALTER TABLE inv_balance ADD COLUMN count_document_id bigint;
COMMENT ON COLUMN inv_balance.count_document_id IS '冻结本桶的盘点单；取消或差异过账后释放';
CREATE INDEX idx_inv_operation_tenant_state ON inv_stock_operation(tenant_id,state,id);

-- 旧版应用也不能绕过盘点冻结；新版先在同事务持锁解冻再过账。
CREATE FUNCTION inv_guard_count_freeze() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.count_document_id IS NOT NULL AND NEW.on_hand IS DISTINCT FROM OLD.on_hand THEN
        RAISE EXCEPTION '库存桶正在盘点';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER inv_count_freeze_guard BEFORE UPDATE OF on_hand ON inv_balance
FOR EACH ROW EXECUTE FUNCTION inv_guard_count_freeze();
