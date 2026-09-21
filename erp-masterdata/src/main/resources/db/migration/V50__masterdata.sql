-- =====================================================================
-- 主数据中心（CAP-P05）。拥有 md_* 表。
-- 共同规则见 contracts/API_P2_MASTER_DATA.md §1：
--   编码租户内唯一 · 只停用不删除 · 被引用后关键字段锁定 · 引用登记
-- =====================================================================

-- ---------------------------------------------------------- 计量单位
CREATE TABLE md_unit (
    id         BIGSERIAL    PRIMARY KEY,
    tenant_id  BIGINT       NOT NULL,
    code       VARCHAR(32)  NOT NULL,
    name       VARCHAR(64)  NOT NULL,
    enabled    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
COMMENT ON TABLE  md_unit IS '计量单位：SKU 的基本单位来源。基本单位被引用后不可更改，否则历史库存数量的含义会整体改变';
COMMENT ON COLUMN md_unit.id         IS '主键，自增';
COMMENT ON COLUMN md_unit.tenant_id  IS '租户 ID';
COMMENT ON COLUMN md_unit.code       IS '单位编码，租户内唯一，如 PCS/BOX/KG';
COMMENT ON COLUMN md_unit.name       IS '单位名称，如 个/箱/千克';
COMMENT ON COLUMN md_unit.enabled    IS '是否启用；停用后不能被新 SKU 引用，已有 SKU 不受影响';
COMMENT ON COLUMN md_unit.created_at IS '创建时间';
CREATE UNIQUE INDEX uk_md_unit_code ON md_unit (tenant_id, code);

-- ---------------------------------------------------------- 商品分类
CREATE TABLE md_category (
    id         BIGSERIAL    PRIMARY KEY,
    tenant_id  BIGINT       NOT NULL,
    parent_id  BIGINT,
    code       VARCHAR(32)  NOT NULL,
    name       VARCHAR(64)  NOT NULL,
    enabled    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
COMMENT ON TABLE  md_category IS '商品分类：树形结构，用于商品归类与报表维度';
COMMENT ON COLUMN md_category.id         IS '主键，自增';
COMMENT ON COLUMN md_category.tenant_id  IS '租户 ID';
COMMENT ON COLUMN md_category.parent_id  IS '上级分类 ID；根分类为空';
COMMENT ON COLUMN md_category.code       IS '分类编码，租户内唯一';
COMMENT ON COLUMN md_category.name       IS '分类名称';
COMMENT ON COLUMN md_category.enabled    IS '是否启用';
COMMENT ON COLUMN md_category.created_at IS '创建时间';
CREATE UNIQUE INDEX uk_md_category_code ON md_category (tenant_id, code);

-- ---------------------------------------------------------- 商品 SPU
CREATE TABLE md_product (
    id          BIGSERIAL    PRIMARY KEY,
    tenant_id   BIGINT       NOT NULL,
    category_id BIGINT,
    code        VARCHAR(64)  NOT NULL,
    name        VARCHAR(256) NOT NULL,
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
COMMENT ON TABLE  md_product IS '商品 SPU：同一商品的抽象，具体可售卖/可入库的最小单位是 SKU';
COMMENT ON COLUMN md_product.id          IS '主键，自增';
COMMENT ON COLUMN md_product.tenant_id   IS '租户 ID';
COMMENT ON COLUMN md_product.category_id IS '所属分类 ID';
COMMENT ON COLUMN md_product.code        IS '商品编码，租户内唯一';
COMMENT ON COLUMN md_product.name        IS '商品名称';
COMMENT ON COLUMN md_product.enabled     IS '是否启用';
COMMENT ON COLUMN md_product.created_at  IS '创建时间';
CREATE UNIQUE INDEX uk_md_product_code ON md_product (tenant_id, code);

-- ---------------------------------------------------------- 商品 SKU
CREATE TABLE md_sku (
    id           BIGSERIAL    PRIMARY KEY,
    tenant_id    BIGINT       NOT NULL,
    product_id   BIGINT       NOT NULL,
    base_unit_id BIGINT       NOT NULL,
    code         VARCHAR(64)  NOT NULL,
    name         VARCHAR(256) NOT NULL,
    spec         VARCHAR(256),
    barcode      VARCHAR(64),
    batch_managed BOOLEAN     NOT NULL DEFAULT FALSE,
    enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    version      BIGINT       NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
COMMENT ON TABLE  md_sku IS '商品 SKU：库存与单据的最小引用单位。code 与 base_unit_id 是关键字段，被单据引用后不可修改';
COMMENT ON COLUMN md_sku.id            IS '主键，自增';
COMMENT ON COLUMN md_sku.tenant_id     IS '租户 ID';
COMMENT ON COLUMN md_sku.product_id    IS '所属 SPU';
COMMENT ON COLUMN md_sku.base_unit_id  IS '基本单位；库存数量以它为准。被引用后不可修改——改了会让历史库存数量的含义整体改变';
COMMENT ON COLUMN md_sku.code          IS 'SKU 编码，租户内唯一；单据上的业务标识，被引用后不可修改';
COMMENT ON COLUMN md_sku.name          IS 'SKU 名称；属描述字段，可修改，历史单据看到的是引用时的快照值';
COMMENT ON COLUMN md_sku.spec          IS '规格描述；可修改';
COMMENT ON COLUMN md_sku.barcode       IS '条码';
COMMENT ON COLUMN md_sku.batch_managed IS '是否启用批次管理；批次是库存台账主键的一部分，故此开关影响 P3 的过账维度';
COMMENT ON COLUMN md_sku.enabled       IS '是否启用；停用后不能被新单据引用，已有单据不受影响';
COMMENT ON COLUMN md_sku.version       IS '乐观锁版本号；更新须带版本条件并检查影响行数';
COMMENT ON COLUMN md_sku.created_at    IS '创建时间';
COMMENT ON COLUMN md_sku.updated_at    IS '最后修改时间';
CREATE UNIQUE INDEX uk_md_sku_code ON md_sku (tenant_id, code);
CREATE INDEX idx_md_sku_product ON md_sku (tenant_id, product_id);

-- ---------------------------------------------------------- 供应商
CREATE TABLE md_supplier (
    id            BIGSERIAL    PRIMARY KEY,
    tenant_id     BIGINT       NOT NULL,
    code          VARCHAR(64)  NOT NULL,
    name          VARCHAR(256) NOT NULL,
    contact       VARCHAR(64),
    phone         VARCHAR(32),
    settlement_method_id BIGINT,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
COMMENT ON TABLE  md_supplier IS '供应商：采购单与应付的往来单位';
COMMENT ON COLUMN md_supplier.id                   IS '主键，自增';
COMMENT ON COLUMN md_supplier.tenant_id            IS '租户 ID';
COMMENT ON COLUMN md_supplier.code                 IS '供应商编码，租户内唯一；被引用后不可修改';
COMMENT ON COLUMN md_supplier.name                 IS '供应商名称；可修改，历史单据看快照';
COMMENT ON COLUMN md_supplier.contact              IS '联系人';
COMMENT ON COLUMN md_supplier.phone                IS '联系电话；属敏感信息，进审计日志前需脱敏';
COMMENT ON COLUMN md_supplier.settlement_method_id IS '默认结算方式';
COMMENT ON COLUMN md_supplier.enabled              IS '是否启用';
COMMENT ON COLUMN md_supplier.version              IS '乐观锁版本号';
COMMENT ON COLUMN md_supplier.created_at           IS '创建时间';
CREATE UNIQUE INDEX uk_md_supplier_code ON md_supplier (tenant_id, code);

-- ---------------------------------------------------------- 客户
CREATE TABLE md_customer (
    id           BIGSERIAL    PRIMARY KEY,
    tenant_id    BIGINT       NOT NULL,
    code         VARCHAR(64)  NOT NULL,
    name         VARCHAR(256) NOT NULL,
    contact      VARCHAR(64),
    phone        VARCHAR(32),
    credit_limit NUMERIC(18,4) NOT NULL DEFAULT 0,
    enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    version      BIGINT       NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
COMMENT ON TABLE  md_customer IS '客户：销售单与应收的往来单位';
COMMENT ON COLUMN md_customer.id           IS '主键，自增';
COMMENT ON COLUMN md_customer.tenant_id    IS '租户 ID';
COMMENT ON COLUMN md_customer.code         IS '客户编码，租户内唯一；被引用后不可修改';
COMMENT ON COLUMN md_customer.name         IS '客户名称；可修改，历史单据看快照';
COMMENT ON COLUMN md_customer.contact      IS '联系人';
COMMENT ON COLUMN md_customer.phone        IS '联系电话；属敏感信息，进审计日志前需脱敏';
COMMENT ON COLUMN md_customer.credit_limit IS '信用额度，精确十进制；销售订单审批的守卫条件之一（P5）';
COMMENT ON COLUMN md_customer.enabled      IS '是否启用';
COMMENT ON COLUMN md_customer.version      IS '乐观锁版本号';
COMMENT ON COLUMN md_customer.created_at   IS '创建时间';
CREATE UNIQUE INDEX uk_md_customer_code ON md_customer (tenant_id, code);

-- ---------------------------------------------------------- 仓库与库位
CREATE TABLE md_warehouse (
    id         BIGSERIAL    PRIMARY KEY,
    tenant_id  BIGINT       NOT NULL,
    company_id BIGINT       NOT NULL,
    code       VARCHAR(64)  NOT NULL,
    name       VARCHAR(128) NOT NULL,
    enabled    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
COMMENT ON TABLE  md_warehouse IS '仓库：库存台账的维度之一，隶属于某个法人主体';
COMMENT ON COLUMN md_warehouse.id         IS '主键，自增';
COMMENT ON COLUMN md_warehouse.tenant_id  IS '租户 ID';
COMMENT ON COLUMN md_warehouse.company_id IS '所属法人主体；跨公司调拨需要区分归属';
COMMENT ON COLUMN md_warehouse.code       IS '仓库编码，租户内唯一；被引用后不可修改';
COMMENT ON COLUMN md_warehouse.name       IS '仓库名称';
COMMENT ON COLUMN md_warehouse.enabled    IS '是否启用';
COMMENT ON COLUMN md_warehouse.created_at IS '创建时间';
CREATE UNIQUE INDEX uk_md_warehouse_code ON md_warehouse (tenant_id, code);

CREATE TABLE md_warehouse_location (
    id           BIGSERIAL    PRIMARY KEY,
    tenant_id    BIGINT       NOT NULL,
    warehouse_id BIGINT       NOT NULL,
    code         VARCHAR(64)  NOT NULL,
    name         VARCHAR(128) NOT NULL,
    enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
COMMENT ON TABLE  md_warehouse_location IS '库位：库存台账的可选维度。按假设 A-11，MVP 不启用库位，台账用哨兵值占位，但维度先建好以免日后改主键';
COMMENT ON COLUMN md_warehouse_location.id           IS '主键，自增';
COMMENT ON COLUMN md_warehouse_location.tenant_id    IS '租户 ID';
COMMENT ON COLUMN md_warehouse_location.warehouse_id IS '所属仓库';
COMMENT ON COLUMN md_warehouse_location.code         IS '库位编码，仓库内唯一';
COMMENT ON COLUMN md_warehouse_location.name         IS '库位名称';
COMMENT ON COLUMN md_warehouse_location.enabled      IS '是否启用';
COMMENT ON COLUMN md_warehouse_location.created_at   IS '创建时间';
CREATE UNIQUE INDEX uk_md_location_code ON md_warehouse_location (tenant_id, warehouse_id, code);

-- ---------------------------------------------------------- 财务基础数据
CREATE TABLE md_currency (
    id         BIGSERIAL    PRIMARY KEY,
    tenant_id  BIGINT       NOT NULL,
    code       VARCHAR(8)   NOT NULL,
    name       VARCHAR(64)  NOT NULL,
    scale      SMALLINT     NOT NULL DEFAULT 2,
    is_base    BOOLEAN      NOT NULL DEFAULT FALSE,
    enabled    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
COMMENT ON TABLE  md_currency IS '币种：MVP 单一本位币（假设 A-08），字段先留以便多币种为加法变更';
COMMENT ON COLUMN md_currency.id         IS '主键，自增';
COMMENT ON COLUMN md_currency.tenant_id  IS '租户 ID';
COMMENT ON COLUMN md_currency.code       IS '币种代码，ISO 4217，如 CNY/USD；租户内唯一';
COMMENT ON COLUMN md_currency.name       IS '币种名称';
COMMENT ON COLUMN md_currency.scale      IS '展示小数位；金额本身按 NUMERIC(18,4) 存储，展示位数由前端按此格式化';
COMMENT ON COLUMN md_currency.is_base    IS '是否本位币；租户内至多一个';
COMMENT ON COLUMN md_currency.enabled    IS '是否启用';
COMMENT ON COLUMN md_currency.created_at IS '创建时间';
CREATE UNIQUE INDEX uk_md_currency_code ON md_currency (tenant_id, code);
CREATE UNIQUE INDEX uk_md_currency_base ON md_currency (tenant_id) WHERE is_base;

CREATE TABLE md_tax_rate (
    id         BIGSERIAL    PRIMARY KEY,
    tenant_id  BIGINT       NOT NULL,
    code       VARCHAR(32)  NOT NULL,
    name       VARCHAR(64)  NOT NULL,
    rate       NUMERIC(9,6) NOT NULL,
    enabled    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_md_tax_rate_range CHECK (rate >= 0 AND rate <= 1)
);
COMMENT ON TABLE  md_tax_rate IS '税率：采购与销售单据的税额计算依据';
COMMENT ON COLUMN md_tax_rate.id         IS '主键，自增';
COMMENT ON COLUMN md_tax_rate.tenant_id  IS '租户 ID';
COMMENT ON COLUMN md_tax_rate.code       IS '税率编码，租户内唯一';
COMMENT ON COLUMN md_tax_rate.name       IS '税率名称，如 增值税13%';
COMMENT ON COLUMN md_tax_rate.rate       IS '税率值，小数形式（0.13 表示 13%）；精确十进制，约束在 [0,1]';
COMMENT ON COLUMN md_tax_rate.enabled    IS '是否启用';
COMMENT ON COLUMN md_tax_rate.created_at IS '创建时间';
CREATE UNIQUE INDEX uk_md_tax_rate_code ON md_tax_rate (tenant_id, code);

CREATE TABLE md_settlement_method (
    id         BIGSERIAL    PRIMARY KEY,
    tenant_id  BIGINT       NOT NULL,
    code       VARCHAR(32)  NOT NULL,
    name       VARCHAR(64)  NOT NULL,
    enabled    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
COMMENT ON TABLE  md_settlement_method IS '结算方式：现金/转账/承兑等，供往来结算引用';
COMMENT ON COLUMN md_settlement_method.id         IS '主键，自增';
COMMENT ON COLUMN md_settlement_method.tenant_id  IS '租户 ID';
COMMENT ON COLUMN md_settlement_method.code       IS '结算方式编码，租户内唯一';
COMMENT ON COLUMN md_settlement_method.name       IS '结算方式名称';
COMMENT ON COLUMN md_settlement_method.enabled    IS '是否启用';
COMMENT ON COLUMN md_settlement_method.created_at IS '创建时间';
CREATE UNIQUE INDEX uk_md_settlement_code ON md_settlement_method (tenant_id, code);

-- ---------------------------------------------------------- 引用登记
CREATE TABLE md_reference (
    id              BIGSERIAL    PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL,
    md_type         VARCHAR(32)  NOT NULL,
    md_id           BIGINT       NOT NULL,
    business_type   VARCHAR(48)  NOT NULL,
    business_id     VARCHAR(64)  NOT NULL,
    referenced_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
COMMENT ON TABLE  md_reference IS '主数据引用登记：记录哪张单据引用了哪条主数据。它回答「这条主数据能否改关键字段」与「谁在用它」';
COMMENT ON COLUMN md_reference.id            IS '主键，自增';
COMMENT ON COLUMN md_reference.tenant_id     IS '租户 ID';
COMMENT ON COLUMN md_reference.md_type       IS '主数据类型：SKU/SUPPLIER/CUSTOMER/WAREHOUSE/UNIT 等';
COMMENT ON COLUMN md_reference.md_id         IS '主数据 ID';
COMMENT ON COLUMN md_reference.business_type IS '引用方业务类型，如 PURCHASE_ORDER';
COMMENT ON COLUMN md_reference.business_id   IS '引用方业务主键';
COMMENT ON COLUMN md_reference.referenced_at IS '引用发生时间';
-- 同一张单据对同一条主数据只登记一次；重复引用不应产生多行
CREATE UNIQUE INDEX uk_md_reference ON md_reference (tenant_id, md_type, md_id, business_type, business_id);
CREATE INDEX idx_md_reference_lookup ON md_reference (tenant_id, md_type, md_id);
