ALTER TABLE fin_account_receivable ADD COLUMN credited_amount numeric(18,4) NOT NULL DEFAULT 0 CHECK (credited_amount >= 0 AND credited_amount <= amount);
COMMENT ON COLUMN fin_account_receivable.credited_amount IS '独立红字单金额绝对值累计投影；不改写原金额和收付核销事实';
ALTER TABLE fin_account_payable ADD COLUMN credited_amount numeric(18,4) NOT NULL DEFAULT 0 CHECK (credited_amount >= 0 AND credited_amount <= amount);
COMMENT ON COLUMN fin_account_payable.credited_amount IS '独立红字单金额绝对值累计投影；不改写原金额和收付核销事实';
CREATE TABLE fin_credit_adjustment (
    id bigserial PRIMARY KEY,
    tenant_id bigint NOT NULL,
    bill_type varchar(2) NOT NULL CHECK (bill_type IN ('AR','AP')),
    bill_id bigint NOT NULL,
    document_no varchar(64) NOT NULL,
    amount numeric(18,4) NOT NULL CHECK (amount <= 0),
    currency varchar(3) NOT NULL,
    return_type varchar(32) NOT NULL,
    return_id varchar(64) NOT NULL,
    return_no varchar(64) NOT NULL,
    refund_due numeric(18,4) NOT NULL CHECK (refund_due >= 0 AND refund_due <= -amount),
    refunded_amount numeric(18,4) NOT NULL DEFAULT 0 CHECK (refunded_amount >= 0 AND refunded_amount <= refund_due),
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id,return_type,return_id),
    UNIQUE (tenant_id,id),
    UNIQUE (tenant_id,document_no)
);
COMMENT ON TABLE fin_credit_adjustment IS '退货红字调整及待退款义务';
COMMENT ON COLUMN fin_credit_adjustment.id IS '主键';
COMMENT ON COLUMN fin_credit_adjustment.tenant_id IS '租户';
COMMENT ON COLUMN fin_credit_adjustment.bill_type IS '应收或应付';
COMMENT ON COLUMN fin_credit_adjustment.bill_id IS '原往来单主键';
COMMENT ON COLUMN fin_credit_adjustment.document_no IS '红字单号';
COMMENT ON COLUMN fin_credit_adjustment.amount IS '红字非正金额；零价退货为零';
COMMENT ON COLUMN fin_credit_adjustment.currency IS '本位币';
COMMENT ON COLUMN fin_credit_adjustment.return_type IS '退货单类型';
COMMENT ON COLUMN fin_credit_adjustment.return_id IS '退货单主键';
COMMENT ON COLUMN fin_credit_adjustment.return_no IS '退货单编号';
COMMENT ON COLUMN fin_credit_adjustment.refund_due IS '本次退货产生的待退款义务；AR 退客户，AP 收供应商退款';
COMMENT ON COLUMN fin_credit_adjustment.refunded_amount IS '已确认退款累计';
COMMENT ON COLUMN fin_credit_adjustment.created_by IS '事件操作人';
COMMENT ON COLUMN fin_credit_adjustment.created_at IS '入账时间';
CREATE TABLE fin_refund_record (
    id bigserial PRIMARY KEY,
    tenant_id bigint NOT NULL,
    adjustment_id bigint NOT NULL,
    amount numeric(18,4) NOT NULL CHECK (amount > 0),
    currency varchar(3) NOT NULL,
    command_id varchar(128) NOT NULL,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id,command_id),
    FOREIGN KEY (tenant_id,adjustment_id) REFERENCES fin_credit_adjustment(tenant_id,id)
);
COMMENT ON TABLE fin_refund_record IS '人工确认退款到账或付出的独立事实，不改写原现金记录';
COMMENT ON COLUMN fin_refund_record.id IS '主键';
COMMENT ON COLUMN fin_refund_record.tenant_id IS '租户';
COMMENT ON COLUMN fin_refund_record.adjustment_id IS '对应红字义务';
COMMENT ON COLUMN fin_refund_record.amount IS '本次退款金额';
COMMENT ON COLUMN fin_refund_record.currency IS '币种';
COMMENT ON COLUMN fin_refund_record.command_id IS '客户端退款幂等键';
COMMENT ON COLUMN fin_refund_record.created_by IS '确认退款操作人';
COMMENT ON COLUMN fin_refund_record.created_at IS '确认时间';
CREATE INDEX idx_fin_credit_bill ON fin_credit_adjustment(tenant_id,bill_type,bill_id);
-- 兼容旧应用的数量条件，数据库禁止冲红后继续超额收付或新增核销。
CREATE FUNCTION fin_guard_credit_capacity() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.paid_amount > OLD.paid_amount AND NEW.paid_amount > NEW.amount - NEW.credited_amount THEN
        RAISE EXCEPTION '冲红后收付款额度不足';
    END IF;
    IF NEW.written_off_amount > OLD.written_off_amount AND NEW.written_off_amount > NEW.amount - NEW.credited_amount THEN
        RAISE EXCEPTION '冲红后核销额度不足';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER fin_account_receivable_credit_guard BEFORE UPDATE ON fin_account_receivable FOR EACH ROW EXECUTE FUNCTION fin_guard_credit_capacity();
CREATE TRIGGER fin_account_payable_credit_guard BEFORE UPDATE ON fin_account_payable FOR EACH ROW EXECUTE FUNCTION fin_guard_credit_capacity();
