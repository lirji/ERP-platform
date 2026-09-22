-- 仅空库使用的累计基线：V1 → V10 → V20 → V30 → V2（按真实依赖顺序）。
-- 历史 V2 版本低于其依赖 V30；保留原文件/校验值，避免改写已有环境的迁移历史。
-- Flyway 已有历史环境忽略 B30；空库执行此完整基线后从 V31 继续。
-- 下列内容复制自不可变历史脚本，不引入额外表/默认数据。验证见 FreshSchemaMigrationIT。

-- SOURCE: erp-app/src/main/resources/db/migration/V1__baseline.sql
-- =====================================================================
-- V1 基线：仅建立平台级公共结构，不含任何业务表。
-- 业务表由各模块在 P1 起按 V2xx__<module>_*.sql 顺序加入。
--
-- 开发规范 §一：任何 CREATE TABLE 必须同时写表注释与每一个字段注释。
-- 本文件是该规范在本项目的第一个落地样例，后续迁移一律照此执行。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 事务性 Outbox：与业务写入同库同事务落地，投递由独立调度器完成。
-- 为什么需要它：MVP 不引入 MQ（TECH_SELECTION 否决 Kafka/RabbitMQ），
-- 但"提交后可靠触发下游"仍需保证。Outbox 让事件与业务数据共享同一个本地事务，
-- 避免"业务提交成功但事件丢失"或"事件已发但业务回滚"。
-- ---------------------------------------------------------------------
CREATE TABLE erp_outbox_message (
    id              BIGSERIAL   PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL,
    aggregate_type  VARCHAR(64)  NOT NULL,
    aggregate_id    VARCHAR(64)  NOT NULL,
    event_type      VARCHAR(128) NOT NULL,
    payload         JSONB        NOT NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    retry_count     INT          NOT NULL DEFAULT 0,
    next_retry_at   TIMESTAMPTZ,
    last_error      TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ
);

COMMENT ON TABLE  erp_outbox_message IS '事务性 Outbox 消息表：与业务写入同事务落库，由调度器异步投递，保证提交后可靠触发下游';
COMMENT ON COLUMN erp_outbox_message.id             IS '主键，自增';
COMMENT ON COLUMN erp_outbox_message.tenant_id      IS '租户 ID；多租户共享库 + 强制过滤（假设 A-10）';
COMMENT ON COLUMN erp_outbox_message.aggregate_type IS '聚合类型，如 PurchaseReceipt / Shipment；用于消费方路由';
COMMENT ON COLUMN erp_outbox_message.aggregate_id   IS '聚合实例 ID；与 aggregate_type 共同定位来源业务对象';
COMMENT ON COLUMN erp_outbox_message.event_type     IS '事件类型，如 PurchaseReceiptPosted；对应 Event Catalog 中的契约名';
COMMENT ON COLUMN erp_outbox_message.payload        IS '事件载荷 JSON；用 JSONB 以便按字段检索与排障';
COMMENT ON COLUMN erp_outbox_message.status         IS '投递状态：PENDING 待投递 / PUBLISHED 已投递 / DEAD 进入死信';
COMMENT ON COLUMN erp_outbox_message.retry_count    IS '已重试次数；配合 next_retry_at 实现有上限的退避重试';
COMMENT ON COLUMN erp_outbox_message.next_retry_at  IS '下次重试时间；NULL 表示可立即投递';
COMMENT ON COLUMN erp_outbox_message.last_error     IS '最近一次投递失败的错误摘要，供排障';
COMMENT ON COLUMN erp_outbox_message.created_at     IS '创建时间（入库即业务提交时间）';
COMMENT ON COLUMN erp_outbox_message.published_at   IS '成功投递时间；未投递为 NULL';

-- 投递扫描的主路径：只关心待投递且到期的消息，按创建顺序取
CREATE INDEX idx_outbox_pending ON erp_outbox_message (status, next_retry_at, created_at)
    WHERE status = 'PENDING';

-- SOURCE: erp-iam/src/main/resources/db/migration/V10__iam.sql
-- =====================================================================
-- 身份与访问（CAP-P01..P04）。拥有 iam_* 表。
-- ERP 只做授权；认证外包给 auth-platform（Casdoor/OIDC），
-- 组织、角色、权限、数据范围全部留在 ERP（SECURITY_ARCHITECTURE §边界）。
-- =====================================================================

CREATE TABLE iam_tenant (
    id          BIGSERIAL    PRIMARY KEY,
    code        VARCHAR(32)  NOT NULL,
    name        VARCHAR(128) NOT NULL,
    timezone    VARCHAR(64)  NOT NULL DEFAULT 'Asia/Shanghai',
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
COMMENT ON TABLE  iam_tenant IS '租户：多租户体系的顶层，一个租户下含多公司、多组织、多仓库、多用户';
COMMENT ON COLUMN iam_tenant.id         IS '主键，自增；即各业务表的 tenant_id';
COMMENT ON COLUMN iam_tenant.code       IS '租户编码，全局唯一，用于登录域名或标识';
COMMENT ON COLUMN iam_tenant.name       IS '租户名称（企业名）';
COMMENT ON COLUMN iam_tenant.timezone   IS '租户时区（IANA 名）；业务日期归属按它计算，不按服务器时区';
COMMENT ON COLUMN iam_tenant.enabled    IS '是否启用；停用后该租户全部用户不可登录';
COMMENT ON COLUMN iam_tenant.created_at IS '创建时间';
CREATE UNIQUE INDEX uk_iam_tenant_code ON iam_tenant (code);

CREATE TABLE iam_org (
    id          BIGSERIAL    PRIMARY KEY,
    tenant_id   BIGINT       NOT NULL,
    parent_id   BIGINT,
    org_path    VARCHAR(512) NOT NULL,
    code        VARCHAR(64)  NOT NULL,
    name        VARCHAR(128) NOT NULL,
    org_type    VARCHAR(16)  NOT NULL DEFAULT 'DEPT',
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_iam_org_path_format CHECK (org_path LIKE '/%/' )
);
COMMENT ON TABLE  iam_org IS '组织单元：公司/部门的树形结构，用物化路径 org_path 支持数据权限前缀下推';
COMMENT ON COLUMN iam_org.id         IS '主键，自增';
COMMENT ON COLUMN iam_org.tenant_id  IS '租户 ID';
COMMENT ON COLUMN iam_org.parent_id  IS '上级组织 ID；根节点为空';
COMMENT ON COLUMN iam_org.org_path   IS '物化路径，形如 /1/23/456/，首尾都带斜杠。缺尾斜杠时 /1/2/ 会前缀匹配到 /1/23/，把兄弟部门误判为子部门，故由 CHECK 约束保证形态';
COMMENT ON COLUMN iam_org.code       IS '组织编码，租户内唯一';
COMMENT ON COLUMN iam_org.name       IS '组织名称';
COMMENT ON COLUMN iam_org.org_type   IS '组织类型：COMPANY 法人主体 / DEPT 部门';
COMMENT ON COLUMN iam_org.enabled    IS '是否启用；有下级或在职员工时不允许停用';
COMMENT ON COLUMN iam_org.created_at IS '创建时间';
CREATE UNIQUE INDEX uk_iam_org_code ON iam_org (tenant_id, code);
-- text_pattern_ops 让 org_path LIKE '/1/23/%' 能走索引，这是选择 PostgreSQL 的直接理由之一
CREATE INDEX idx_iam_org_path ON iam_org (tenant_id, org_path text_pattern_ops);

CREATE TABLE iam_user (
    id           BIGSERIAL    PRIMARY KEY,
    tenant_id    BIGINT       NOT NULL,
    company_id   BIGINT       NOT NULL,
    org_id       BIGINT       NOT NULL,
    external_id  VARCHAR(128),
    username     VARCHAR(64)  NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
COMMENT ON TABLE  iam_user IS '用户：ERP 侧的身份投影，认证由 auth-platform 完成，此处只存授权所需信息';
COMMENT ON COLUMN iam_user.id           IS '主键，自增';
COMMENT ON COLUMN iam_user.tenant_id    IS '租户 ID';
COMMENT ON COLUMN iam_user.company_id   IS '所属法人主体（公司）组织 ID';
COMMENT ON COLUMN iam_user.org_id       IS '所属部门组织 ID；其 org_path 决定该用户的数据范围基点';
COMMENT ON COLUMN iam_user.external_id  IS '外部身份标识（Casdoor subject）；用于把 OIDC 令牌映射到本地用户';
COMMENT ON COLUMN iam_user.username     IS '登录名，租户内唯一';
COMMENT ON COLUMN iam_user.display_name IS '显示名';
COMMENT ON COLUMN iam_user.enabled      IS '是否启用；停用后判权直接拒绝';
COMMENT ON COLUMN iam_user.created_at   IS '创建时间';
CREATE UNIQUE INDEX uk_iam_user_username ON iam_user (tenant_id, username);
CREATE UNIQUE INDEX uk_iam_user_external ON iam_user (external_id) WHERE external_id IS NOT NULL;

CREATE TABLE iam_role (
    id              BIGSERIAL    PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL,
    code            VARCHAR(64)  NOT NULL,
    name            VARCHAR(128) NOT NULL,
    data_scope_type VARCHAR(24)  NOT NULL DEFAULT 'SELF',
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
COMMENT ON TABLE  iam_role IS '角色：功能权限与数据范围的载体，用户通过角色获得权限';
COMMENT ON COLUMN iam_role.id              IS '主键，自增';
COMMENT ON COLUMN iam_role.tenant_id       IS '租户 ID';
COMMENT ON COLUMN iam_role.code            IS '角色编码，租户内唯一';
COMMENT ON COLUMN iam_role.name            IS '角色名称';
COMMENT ON COLUMN iam_role.data_scope_type IS '数据范围类型：SELF/DEPT/DEPT_AND_BELOW/SPECIFIED_ORG/SPECIFIED_COMPANY/ALL；存显式 code 而非序号，避免枚举顺序变化改变历史数据含义';
COMMENT ON COLUMN iam_role.enabled         IS '是否启用';
COMMENT ON COLUMN iam_role.created_at      IS '创建时间';
CREATE UNIQUE INDEX uk_iam_role_code ON iam_role (tenant_id, code);

CREATE TABLE iam_user_role (
    tenant_id BIGINT NOT NULL,
    user_id   BIGINT NOT NULL,
    role_id   BIGINT NOT NULL,
    CONSTRAINT pk_iam_user_role PRIMARY KEY (tenant_id, user_id, role_id)
);
COMMENT ON TABLE  iam_user_role IS '用户与角色的关联：一个用户可持有多个角色，取并集';
COMMENT ON COLUMN iam_user_role.tenant_id IS '租户 ID';
COMMENT ON COLUMN iam_user_role.user_id   IS '用户 ID';
COMMENT ON COLUMN iam_user_role.role_id   IS '角色 ID';

CREATE TABLE iam_role_permission (
    tenant_id  BIGINT       NOT NULL,
    role_id    BIGINT       NOT NULL,
    permission VARCHAR(128) NOT NULL,
    CONSTRAINT pk_iam_role_permission PRIMARY KEY (tenant_id, role_id, permission)
);
COMMENT ON TABLE  iam_role_permission IS '角色持有的功能权限点';
COMMENT ON COLUMN iam_role_permission.tenant_id  IS '租户 ID';
COMMENT ON COLUMN iam_role_permission.role_id    IS '角色 ID';
COMMENT ON COLUMN iam_role_permission.permission IS '权限点，形如 purchase:order:approve（context:resource:action）';

-- SOURCE: erp-numbering/src/main/resources/db/migration/V20__numbering.sql
-- =====================================================================
-- 编号中心（CAP-P06）。拥有 num_* 表。
--
-- 迁移版本段位约定（避免多模块 Flyway 版本冲突，各模块脚本在 classpath 合并）：
--   V1      平台基线（erp-app）
--   V10-19  erp-iam
--   V20-29  erp-numbering
--   V30-39  erp-document
--   V40-49  erp-approval
--   V50+    各业务模块（P2 起分配）
-- =====================================================================

CREATE TABLE num_sequence (
    tenant_id       BIGINT       NOT NULL,
    business_type   VARCHAR(32)  NOT NULL,
    biz_date        DATE         NOT NULL,
    current_value   BIGINT       NOT NULL DEFAULT 0,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_num_sequence PRIMARY KEY (tenant_id, business_type, biz_date)
);

COMMENT ON TABLE  num_sequence IS '单据流水号计数器：按 租户+业务类型+业务日期 维度自增，单语句原子递增保证并发下不重号不空洞';
COMMENT ON COLUMN num_sequence.tenant_id     IS '租户 ID；编号在租户内唯一，不同租户互不影响';
COMMENT ON COLUMN num_sequence.business_type IS '业务类型，如 PO 采购订单 / SO 销售订单 / IN 入库；对应编号前缀';
COMMENT ON COLUMN num_sequence.biz_date      IS '业务日期（租户时区下的日期，无时区）；每日流水从 1 重新开始';
COMMENT ON COLUMN num_sequence.current_value IS '当前已分配到的最大流水值；取号即 +1 并返回新值';
COMMENT ON COLUMN num_sequence.updated_at    IS '最后一次取号时间，供排障与用量观察';

CREATE TABLE num_rule (
    tenant_id       BIGINT       NOT NULL,
    business_type   VARCHAR(32)  NOT NULL,
    prefix          VARCHAR(8)   NOT NULL,
    seq_width       SMALLINT     NOT NULL DEFAULT 6,
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_num_rule PRIMARY KEY (tenant_id, business_type),
    CONSTRAINT ck_num_rule_seq_width CHECK (seq_width BETWEEN 4 AND 10)
);

COMMENT ON TABLE  num_rule IS '编号规则：定义各业务类型的单据号前缀与流水位数，业务人员可维护';
COMMENT ON COLUMN num_rule.tenant_id     IS '租户 ID';
COMMENT ON COLUMN num_rule.business_type IS '业务类型，与 num_sequence.business_type 对应';
COMMENT ON COLUMN num_rule.prefix        IS '单据号前缀，如 PO / SO / IN；与日期和流水拼成完整单据号';
COMMENT ON COLUMN num_rule.seq_width     IS '流水号位数，左侧补零；已启用后不得调小，否则会与历史单号冲突';
COMMENT ON COLUMN num_rule.enabled       IS '是否启用；停用后该业务类型无法取号';
COMMENT ON COLUMN num_rule.created_at    IS '创建时间';

-- SOURCE: erp-document/src/main/resources/db/migration/V30__document.sql
-- =====================================================================
-- 单据状态流转留痕与业务审计（CAP-P07 留痕部分、CAP-P09）。拥有 doc_* 表。
--
-- 刻意不建"万能单据表"：RISK_REGISTER「过度设计」第 1 条明确把
-- 一张 business_document + document_item 万能表列为要阻止的样子。
-- 单据的业务字段留在各自模块的表里；这里只存**流转事实**与**审计事实**。
-- =====================================================================

CREATE TABLE doc_state_transition (
    id             BIGSERIAL    PRIMARY KEY,
    tenant_id      BIGINT       NOT NULL,
    business_type  VARCHAR(48)  NOT NULL,
    business_id    VARCHAR(64)  NOT NULL,
    document_no    VARCHAR(64),
    from_state     VARCHAR(24)  NOT NULL,
    to_state       VARCHAR(24)  NOT NULL,
    event          VARCHAR(24)  NOT NULL,
    from_version   BIGINT       NOT NULL,
    operator_id    BIGINT       NOT NULL,
    trace_id       VARCHAR(64),
    occurred_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

COMMENT ON TABLE  doc_state_transition IS '单据状态流转日志：记录每一次合法迁移，既是审计证据，也是并发迁移的串行化点';
COMMENT ON COLUMN doc_state_transition.id            IS '主键，自增';
COMMENT ON COLUMN doc_state_transition.tenant_id     IS '租户 ID';
COMMENT ON COLUMN doc_state_transition.business_type IS '业务类型，如 PURCHASE_ORDER / SALES_ORDER；与 business_id 共同定位单据';
COMMENT ON COLUMN doc_state_transition.business_id   IS '业务主键，字符串以兼容各模块不同的主键形态';
COMMENT ON COLUMN doc_state_transition.document_no   IS '单据号，冗余存放便于按单号直接检索流转历史';
COMMENT ON COLUMN doc_state_transition.from_state    IS '迁移前状态';
COMMENT ON COLUMN doc_state_transition.to_state      IS '迁移后状态';
COMMENT ON COLUMN doc_state_transition.event         IS '驱动本次迁移的事件';
COMMENT ON COLUMN doc_state_transition.from_version  IS '迁移前的单据版本号；与唯一索引共同保证同一版本只能迁移一次';
COMMENT ON COLUMN doc_state_transition.operator_id   IS '操作人用户 ID';
COMMENT ON COLUMN doc_state_transition.trace_id      IS '请求追踪标识，可关联同一次请求的全部日志';
COMMENT ON COLUMN doc_state_transition.occurred_at   IS '迁移发生时间';

-- 并发控制的核心：同一单据的同一个版本只允许迁移一次。
-- 50 个线程同时基于 version=0 发起迁移时，数据库保证只有一条能插入成功，
-- 其余撞唯一约束失败 —— 这让"并发状态修改只有一个成功"由数据库而非应用层保证。
CREATE UNIQUE INDEX uk_doc_transition_version
    ON doc_state_transition (tenant_id, business_type, business_id, from_version);

CREATE INDEX idx_doc_transition_lookup
    ON doc_state_transition (tenant_id, business_type, business_id, occurred_at DESC);

CREATE TABLE doc_audit_log (
    id             BIGSERIAL    PRIMARY KEY,
    tenant_id      BIGINT       NOT NULL,
    user_id        BIGINT       NOT NULL,
    business_type  VARCHAR(48)  NOT NULL,
    business_id    VARCHAR(64),
    document_no    VARCHAR(64),
    action         VARCHAR(24)  NOT NULL,
    before_value   JSONB,
    after_value    JSONB,
    source_ip      VARCHAR(45),
    user_agent     VARCHAR(256),
    trace_id       VARCHAR(64),
    occurred_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

COMMENT ON TABLE  doc_audit_log IS '业务审计日志：谁在什么时候对哪张单据做了什么、改动前后是什么、来源 IP 与终端';
COMMENT ON COLUMN doc_audit_log.id            IS '主键，自增';
COMMENT ON COLUMN doc_audit_log.tenant_id     IS '租户 ID';
COMMENT ON COLUMN doc_audit_log.user_id       IS '操作人用户 ID（谁）';
COMMENT ON COLUMN doc_audit_log.business_type IS '业务类型（操作了什么）';
COMMENT ON COLUMN doc_audit_log.business_id   IS '业务主键；非单据类操作可为空';
COMMENT ON COLUMN doc_audit_log.document_no   IS '单据号，便于业务人员按单号追溯';
COMMENT ON COLUMN doc_audit_log.action        IS '动作：CREATE/UPDATE/SUBMIT/APPROVE/CANCEL/CLOSE 等';
COMMENT ON COLUMN doc_audit_log.before_value  IS '修改前的值（JSONB）；敏感字段已按脱敏规则处理后落库';
COMMENT ON COLUMN doc_audit_log.after_value   IS '修改后的值（JSONB）；新建时为空的是 before 而非 after';
COMMENT ON COLUMN doc_audit_log.source_ip     IS '来源 IP，长度按 IPv6 最大形态预留 45 位';
COMMENT ON COLUMN doc_audit_log.user_agent    IS '来源终端标识';
COMMENT ON COLUMN doc_audit_log.trace_id      IS '请求追踪标识';
COMMENT ON COLUMN doc_audit_log.occurred_at   IS '操作发生时间（什么时候）';

CREATE INDEX idx_doc_audit_business
    ON doc_audit_log (tenant_id, business_type, business_id, occurred_at DESC);

-- SOURCE: erp-kernel/src/main/resources/db/migration/V2__state_transition_to_kernel.sql
-- =====================================================================
-- 把状态流转日志从 erp-document 移交 erp-kernel。
--
-- 为什么移：doc_state_transition 承载的是 CAP-P07（统一单据模型与状态机）的
-- 持久化，而 MODULE_SERVICE_MAP 把「状态机机制」归在 erp-kernel，
-- 把 erp-document 限定为关系图与审计（CAP-P08/P09）且入向依赖只有 app。
-- 业务模块必须**同步**调用状态迁移（并发控制依赖那次插入的唯一索引），
-- 而同步调用 erp-document 会违反已批准的依赖矩阵。
-- 因此正确的归属是内核平台表（erp_ 前缀），与 erp_outbox_message 同类。
--
-- 用 RENAME 而不是新建+搬数据：保留既有数据与索引，也不修改已执行的 V30。
-- =====================================================================

ALTER TABLE doc_state_transition RENAME TO erp_state_transition;

COMMENT ON TABLE erp_state_transition IS
    '单据状态流转日志（平台机制，内核所有）：记录每一次合法迁移，既是审计证据，也是并发迁移的串行化点。同一单据的同一版本只能迁移一次，由唯一索引保证';
