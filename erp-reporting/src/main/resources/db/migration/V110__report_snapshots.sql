CREATE TABLE rpt_snapshot_job (
    id bigserial PRIMARY KEY,
    tenant_id bigint NOT NULL,
    kind varchar(16) NOT NULL,
    fingerprint varchar(256) NOT NULL,
    cursor_id bigint NOT NULL DEFAULT 0,
    state varchar(16) NOT NULL DEFAULT 'BUILDING' CHECK (state IN ('BUILDING','PUBLISHED','STALE')),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    finished_at timestamptz,
    UNIQUE (tenant_id,id)
);
COMMENT ON TABLE rpt_snapshot_job IS '可恢复的分批报表快照重建任务';
COMMENT ON COLUMN rpt_snapshot_job.id IS '任务主键即快照代号';
COMMENT ON COLUMN rpt_snapshot_job.tenant_id IS '租户';
COMMENT ON COLUMN rpt_snapshot_job.kind IS '报表类型';
COMMENT ON COLUMN rpt_snapshot_job.fingerprint IS '权威来源起始指纹';
COMMENT ON COLUMN rpt_snapshot_job.cursor_id IS '最后已提交来源主键';
COMMENT ON COLUMN rpt_snapshot_job.state IS '构建中、已发布或来源变动失效';
COMMENT ON COLUMN rpt_snapshot_job.created_at IS '快照一致性区间起点';
COMMENT ON COLUMN rpt_snapshot_job.finished_at IS '完成时间';
CREATE UNIQUE INDEX uk_rpt_building ON rpt_snapshot_job(tenant_id,kind) WHERE state='BUILDING';
CREATE TABLE rpt_snapshot_current (
    tenant_id bigint NOT NULL,
    kind varchar(16) NOT NULL,
    job_id bigint NOT NULL,
    PRIMARY KEY (tenant_id,kind),
    FOREIGN KEY (tenant_id,job_id) REFERENCES rpt_snapshot_job(tenant_id,id)
);
COMMENT ON TABLE rpt_snapshot_current IS '每租户每类报表已发布快照指针';
COMMENT ON COLUMN rpt_snapshot_current.tenant_id IS '租户';
COMMENT ON COLUMN rpt_snapshot_current.kind IS '报表类型';
COMMENT ON COLUMN rpt_snapshot_current.job_id IS '当前快照任务';
CREATE TABLE rpt_snapshot_fact (
    tenant_id bigint NOT NULL,
    job_id bigint NOT NULL,
    source_id bigint NOT NULL,
    company_id bigint NOT NULL,
    partner_id bigint NOT NULL,
    warehouse_id bigint NOT NULL,
    sku_id bigint NOT NULL,
    batch_no varchar(64),
    business_date date,
    quantity numeric(24,6) NOT NULL,
    reserved numeric(24,6) NOT NULL,
    locked numeric(24,6) NOT NULL,
    in_transit numeric(24,6) NOT NULL,
    amount numeric(24,6),
    reduction numeric(24,6) NOT NULL,
    settled numeric(24,6) NOT NULL,
    paid numeric(24,6) NOT NULL,
    PRIMARY KEY (tenant_id,job_id,source_id),
    FOREIGN KEY (tenant_id,job_id) REFERENCES rpt_snapshot_job(tenant_id,id)
);
COMMENT ON TABLE rpt_snapshot_fact IS '报表查询专用投影；禁止查询业务表';
COMMENT ON COLUMN rpt_snapshot_fact.tenant_id IS '租户';
COMMENT ON COLUMN rpt_snapshot_fact.job_id IS '所属候选或已发布快照';
COMMENT ON COLUMN rpt_snapshot_fact.source_id IS '权威模块来源主键';
COMMENT ON COLUMN rpt_snapshot_fact.company_id IS '公司';
COMMENT ON COLUMN rpt_snapshot_fact.partner_id IS '往来方；不适用为零';
COMMENT ON COLUMN rpt_snapshot_fact.warehouse_id IS '仓库；不适用为零';
COMMENT ON COLUMN rpt_snapshot_fact.sku_id IS 'SKU；不适用为零';
COMMENT ON COLUMN rpt_snapshot_fact.batch_no IS '库存批次';
COMMENT ON COLUMN rpt_snapshot_fact.business_date IS '原业务单据日期；库存不适用';
COMMENT ON COLUMN rpt_snapshot_fact.quantity IS '在库或扣除退货后的交易数量';
COMMENT ON COLUMN rpt_snapshot_fact.reserved IS '预占数量';
COMMENT ON COLUMN rpt_snapshot_fact.locked IS '锁定数量';
COMMENT ON COLUMN rpt_snapshot_fact.in_transit IS '在途数量';
COMMENT ON COLUMN rpt_snapshot_fact.amount IS '账面成本或原单金额；NULL 为未知';
COMMENT ON COLUMN rpt_snapshot_fact.reduction IS '累计退货或红字绝对额';
COMMENT ON COLUMN rpt_snapshot_fact.settled IS '累计净核销';
COMMENT ON COLUMN rpt_snapshot_fact.paid IS '累计实收付';
CREATE INDEX idx_rpt_inventory_lookup ON rpt_snapshot_fact(tenant_id,job_id,company_id,warehouse_id,sku_id,source_id);
CREATE INDEX idx_rpt_partner_date ON rpt_snapshot_fact(tenant_id,job_id,company_id,partner_id,business_date);
