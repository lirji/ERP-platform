CREATE TABLE fin_account_receivable (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    partner_id BIGINT NOT NULL,
    document_no VARCHAR(64) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    amount NUMERIC(18,4) NOT NULL,
    written_off_amount NUMERIC(18,4) NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    org_path VARCHAR(512) NOT NULL,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    source_doc_type VARCHAR(40) NOT NULL,
    source_doc_id VARCHAR(64) NOT NULL,
    source_doc_no VARCHAR(64) NOT NULL,
    order_id VARCHAR(64) NOT NULL,
    paid_amount NUMERIC(18,4) NOT NULL DEFAULT 0,
    CONSTRAINT ck_fin_account_receivable_amount CHECK (amount >= 0 AND written_off_amount >= 0 AND written_off_amount <= paid_amount AND paid_amount <= amount),
    UNIQUE (tenant_id, source_doc_type, source_doc_id),
    UNIQUE (tenant_id, document_no),
    UNIQUE (tenant_id, id)
);
COMMENT ON TABLE fin_account_receivable IS '应收单：销售出库事件派生';
COMMENT ON COLUMN fin_account_receivable.id IS '主键';
COMMENT ON COLUMN fin_account_receivable.tenant_id IS '租户 ID';
COMMENT ON COLUMN fin_account_receivable.company_id IS '法人主体';
COMMENT ON COLUMN fin_account_receivable.partner_id IS '客户或供应商 ID';
COMMENT ON COLUMN fin_account_receivable.document_no IS '财务单号';
COMMENT ON COLUMN fin_account_receivable.currency IS 'ISO 4217 币种；MVP 单一本位币';
COMMENT ON COLUMN fin_account_receivable.amount IS '原始金额，四位精度';
COMMENT ON COLUMN fin_account_receivable.written_off_amount IS '已核销金额，反核销时回退';
COMMENT ON COLUMN fin_account_receivable.version IS '并发版本';
COMMENT ON COLUMN fin_account_receivable.org_path IS '组织数据范围';
COMMENT ON COLUMN fin_account_receivable.created_by IS '来源操作人';
COMMENT ON COLUMN fin_account_receivable.created_at IS '创建时刻';
COMMENT ON COLUMN fin_account_receivable.source_doc_type IS '来源单据类型';
COMMENT ON COLUMN fin_account_receivable.source_doc_id IS '来源单据 ID';
COMMENT ON COLUMN fin_account_receivable.source_doc_no IS '来源单号';
COMMENT ON COLUMN fin_account_receivable.order_id IS '源头采购或销售订单 ID';
COMMENT ON COLUMN fin_account_receivable.paid_amount IS '已登记收付款金额；反核销不撤销现金事实';
CREATE TABLE fin_account_payable (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    partner_id BIGINT NOT NULL,
    document_no VARCHAR(64) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    amount NUMERIC(18,4) NOT NULL,
    written_off_amount NUMERIC(18,4) NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    org_path VARCHAR(512) NOT NULL,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    source_doc_type VARCHAR(40) NOT NULL,
    source_doc_id VARCHAR(64) NOT NULL,
    source_doc_no VARCHAR(64) NOT NULL,
    order_id VARCHAR(64) NOT NULL,
    paid_amount NUMERIC(18,4) NOT NULL DEFAULT 0,
    CONSTRAINT ck_fin_account_payable_amount CHECK (amount >= 0 AND written_off_amount >= 0 AND written_off_amount <= paid_amount AND paid_amount <= amount),
    UNIQUE (tenant_id, source_doc_type, source_doc_id),
    UNIQUE (tenant_id, document_no),
    UNIQUE (tenant_id, id)
);
COMMENT ON TABLE fin_account_payable IS '应付单：采购入库事件派生';
COMMENT ON COLUMN fin_account_payable.id IS '主键';
COMMENT ON COLUMN fin_account_payable.tenant_id IS '租户 ID';
COMMENT ON COLUMN fin_account_payable.company_id IS '法人主体';
COMMENT ON COLUMN fin_account_payable.partner_id IS '客户或供应商 ID';
COMMENT ON COLUMN fin_account_payable.document_no IS '财务单号';
COMMENT ON COLUMN fin_account_payable.currency IS 'ISO 4217 币种；MVP 单一本位币';
COMMENT ON COLUMN fin_account_payable.amount IS '原始金额，四位精度';
COMMENT ON COLUMN fin_account_payable.written_off_amount IS '已核销金额，反核销时回退';
COMMENT ON COLUMN fin_account_payable.version IS '并发版本';
COMMENT ON COLUMN fin_account_payable.org_path IS '组织数据范围';
COMMENT ON COLUMN fin_account_payable.created_by IS '来源操作人';
COMMENT ON COLUMN fin_account_payable.created_at IS '创建时刻';
COMMENT ON COLUMN fin_account_payable.source_doc_type IS '来源单据类型';
COMMENT ON COLUMN fin_account_payable.source_doc_id IS '来源单据 ID';
COMMENT ON COLUMN fin_account_payable.source_doc_no IS '来源单号';
COMMENT ON COLUMN fin_account_payable.order_id IS '源头采购或销售订单 ID';
COMMENT ON COLUMN fin_account_payable.paid_amount IS '已登记收付款金额；反核销不撤销现金事实';
CREATE TABLE fin_receipt (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    partner_id BIGINT NOT NULL,
    document_no VARCHAR(64) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    amount NUMERIC(18,4) NOT NULL,
    written_off_amount NUMERIC(18,4) NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    org_path VARCHAR(512) NOT NULL,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    bill_id BIGINT NOT NULL,
    command_id VARCHAR(64) NOT NULL,
    CONSTRAINT ck_fin_receipt_amount CHECK (amount > 0 AND written_off_amount >= 0 AND written_off_amount <= amount),
    UNIQUE (tenant_id, command_id),
    UNIQUE (tenant_id, document_no),
    FOREIGN KEY (tenant_id, bill_id) REFERENCES fin_account_receivable (tenant_id, id)
);
COMMENT ON TABLE fin_receipt IS '收款单：记录实际收款，不代表发起银行扣款';
COMMENT ON COLUMN fin_receipt.id IS '主键';
COMMENT ON COLUMN fin_receipt.tenant_id IS '租户 ID';
COMMENT ON COLUMN fin_receipt.company_id IS '法人主体';
COMMENT ON COLUMN fin_receipt.partner_id IS '客户或供应商 ID';
COMMENT ON COLUMN fin_receipt.document_no IS '财务单号';
COMMENT ON COLUMN fin_receipt.currency IS 'ISO 4217 币种；MVP 单一本位币';
COMMENT ON COLUMN fin_receipt.amount IS '原始金额，四位精度';
COMMENT ON COLUMN fin_receipt.written_off_amount IS '已核销金额，反核销时回退';
COMMENT ON COLUMN fin_receipt.version IS '并发版本';
COMMENT ON COLUMN fin_receipt.org_path IS '组织数据范围';
COMMENT ON COLUMN fin_receipt.created_by IS '来源操作人';
COMMENT ON COLUMN fin_receipt.created_at IS '创建时刻';
COMMENT ON COLUMN fin_receipt.bill_id IS '本次收付款归属的应收或应付单';
COMMENT ON COLUMN fin_receipt.command_id IS '业务幂等键，相同键不得变更内容';
CREATE TABLE fin_payment (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    partner_id BIGINT NOT NULL,
    document_no VARCHAR(64) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    amount NUMERIC(18,4) NOT NULL,
    written_off_amount NUMERIC(18,4) NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    org_path VARCHAR(512) NOT NULL,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    bill_id BIGINT NOT NULL,
    command_id VARCHAR(64) NOT NULL,
    CONSTRAINT ck_fin_payment_amount CHECK (amount > 0 AND written_off_amount >= 0 AND written_off_amount <= amount),
    UNIQUE (tenant_id, command_id),
    UNIQUE (tenant_id, document_no),
    FOREIGN KEY (tenant_id, bill_id) REFERENCES fin_account_payable (tenant_id, id)
);
COMMENT ON TABLE fin_payment IS '付款单：记录实际付款，不代表发起银行转账';
COMMENT ON COLUMN fin_payment.id IS '主键';
COMMENT ON COLUMN fin_payment.tenant_id IS '租户 ID';
COMMENT ON COLUMN fin_payment.company_id IS '法人主体';
COMMENT ON COLUMN fin_payment.partner_id IS '客户或供应商 ID';
COMMENT ON COLUMN fin_payment.document_no IS '财务单号';
COMMENT ON COLUMN fin_payment.currency IS 'ISO 4217 币种；MVP 单一本位币';
COMMENT ON COLUMN fin_payment.amount IS '原始金额，四位精度';
COMMENT ON COLUMN fin_payment.written_off_amount IS '已核销金额，反核销时回退';
COMMENT ON COLUMN fin_payment.version IS '并发版本';
COMMENT ON COLUMN fin_payment.org_path IS '组织数据范围';
COMMENT ON COLUMN fin_payment.created_by IS '来源操作人';
COMMENT ON COLUMN fin_payment.created_at IS '创建时刻';
COMMENT ON COLUMN fin_payment.bill_id IS '本次收付款归属的应收或应付单';
COMMENT ON COLUMN fin_payment.command_id IS '业务幂等键，相同键不得变更内容';
CREATE TABLE fin_settlement_record (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    bill_type VARCHAR(2) NOT NULL,
    bill_id BIGINT NOT NULL,
    cash_id BIGINT NOT NULL,
    amount NUMERIC(18,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    reversal_of BIGINT,
    reversed BOOLEAN NOT NULL DEFAULT FALSE,
    created_by BIGINT NOT NULL,
    reason VARCHAR(512),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (bill_type IN ('AR','AP')),
    CHECK ((reversal_of IS NULL AND amount > 0) OR (reversal_of IS NOT NULL AND amount < 0)),
    UNIQUE (tenant_id, reversal_of)
);
COMMENT ON TABLE fin_settlement_record IS '核销及反核销流水：反核销新增负金额行，保留原始记录';
COMMENT ON COLUMN fin_settlement_record.id IS '主键';
COMMENT ON COLUMN fin_settlement_record.tenant_id IS '租户 ID';
COMMENT ON COLUMN fin_settlement_record.bill_type IS 'AR 应收或 AP 应付';
COMMENT ON COLUMN fin_settlement_record.bill_id IS '往来单 ID';
COMMENT ON COLUMN fin_settlement_record.cash_id IS '对应收付款单 ID';
COMMENT ON COLUMN fin_settlement_record.amount IS '正数核销，负数反核销';
COMMENT ON COLUMN fin_settlement_record.currency IS '币种';
COMMENT ON COLUMN fin_settlement_record.reversal_of IS '被反核销的原记录 ID';
COMMENT ON COLUMN fin_settlement_record.reversed IS '原记录是否已反核销，仅作并发标记，原金额不变';
COMMENT ON COLUMN fin_settlement_record.created_by IS '操作人';
COMMENT ON COLUMN fin_settlement_record.reason IS '反核销原因';
COMMENT ON COLUMN fin_settlement_record.created_at IS '业务提交时刻';
CREATE UNIQUE INDEX uk_fin_active_pair ON fin_settlement_record (tenant_id,bill_type,bill_id,cash_id) WHERE reversal_of IS NULL AND reversed = FALSE;
CREATE INDEX idx_fin_settlement_bill ON fin_settlement_record (tenant_id,bill_type,bill_id);
