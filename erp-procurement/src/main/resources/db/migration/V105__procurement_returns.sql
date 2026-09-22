ALTER TABLE pur_receipt_line ADD COLUMN posted_amount numeric(18,4) CHECK (posted_amount >= 0);
ALTER TABLE pur_receipt_line ADD COLUMN currency varchar(3);
ALTER TABLE pur_receipt_line ADD COLUMN returned_qty numeric(18,6) NOT NULL DEFAULT 0 CHECK (returned_qty >= 0 AND returned_qty <= received_qty);
COMMENT ON COLUMN pur_receipt_line.posted_amount IS '本次原收发货行入账金额；旧行缺失不猜测补算';
COMMENT ON COLUMN pur_receipt_line.currency IS '原收发货本位币快照，退货不得取后来配置的币种';
COMMENT ON COLUMN pur_receipt_line.returned_qty IS '已过账退货数量，不能超过本行实际收发货量';
CREATE TABLE pur_return (
    id bigserial PRIMARY KEY,
    tenant_id bigint NOT NULL,
    document_no varchar(64) NOT NULL,
    source_line_id bigint NOT NULL,
    quantity numeric(18,6) NOT NULL CHECK (quantity > 0),
    batch_no varchar(64) NOT NULL,
    command_id varchar(128) NOT NULL,
    reason varchar(256) NOT NULL,
    amount numeric(18,4),
    state varchar(24) NOT NULL DEFAULT 'DRAFT',
    version bigint NOT NULL DEFAULT 0,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id,document_no),
    UNIQUE (tenant_id,command_id)
);
COMMENT ON TABLE pur_return IS '独立退货单，不修改原收发货事实';
COMMENT ON COLUMN pur_return.id IS '退货主键';
COMMENT ON COLUMN pur_return.tenant_id IS '租户';
COMMENT ON COLUMN pur_return.document_no IS '退货单号';
COMMENT ON COLUMN pur_return.source_line_id IS '原收发货行';
COMMENT ON COLUMN pur_return.quantity IS '退货数量';
COMMENT ON COLUMN pur_return.batch_no IS '退货库存批次，销售须显式指定';
COMMENT ON COLUMN pur_return.command_id IS '建单幂等键';
COMMENT ON COLUMN pur_return.reason IS '退货原因';
COMMENT ON COLUMN pur_return.amount IS '执行时按原行金额累计比例计算的冲红绝对值';
COMMENT ON COLUMN pur_return.state IS '标准单据状态';
COMMENT ON COLUMN pur_return.version IS '状态版本';
COMMENT ON COLUMN pur_return.created_by IS '创建人';
COMMENT ON COLUMN pur_return.created_at IS '创建时间';
CREATE INDEX idx_pur_return_source ON pur_return(tenant_id,source_line_id);
