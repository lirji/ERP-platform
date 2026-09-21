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
