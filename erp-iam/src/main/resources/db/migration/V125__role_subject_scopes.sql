CREATE UNIQUE INDEX uk_iam_role_tenant_id ON iam_role(tenant_id,id);
CREATE TABLE iam_role_scope_org (
 tenant_id BIGINT NOT NULL,
 role_id BIGINT NOT NULL,
 org_id BIGINT NOT NULL,
 PRIMARY KEY(tenant_id,role_id,org_id),
 FOREIGN KEY(tenant_id,role_id) REFERENCES iam_role(tenant_id,id),
 FOREIGN KEY(tenant_id,org_id) REFERENCES iam_org(tenant_id,id)
);
COMMENT ON TABLE iam_role_scope_org IS '角色指定组织子树，空集合不授予数据访问';
COMMENT ON COLUMN iam_role_scope_org.tenant_id IS '租户归属，与角色及组织共同外键隔离';
COMMENT ON COLUMN iam_role_scope_org.role_id IS '所属角色';
COMMENT ON COLUMN iam_role_scope_org.org_id IS '授权子树根组织';
CREATE TABLE iam_role_scope_company (
 tenant_id BIGINT NOT NULL,
 role_id BIGINT NOT NULL,
 company_id BIGINT NOT NULL,
 PRIMARY KEY(tenant_id,role_id,company_id),
 FOREIGN KEY(tenant_id,role_id) REFERENCES iam_role(tenant_id,id),
 FOREIGN KEY(tenant_id,company_id) REFERENCES iam_org(tenant_id,id)
);
COMMENT ON TABLE iam_role_scope_company IS '角色指定法人公司集合，写入口验证公司类型及启用状态';
COMMENT ON COLUMN iam_role_scope_company.tenant_id IS '租户归属，与角色及公司共同外键隔离';
COMMENT ON COLUMN iam_role_scope_company.role_id IS '所属角色';
COMMENT ON COLUMN iam_role_scope_company.company_id IS '被授权公司组织';
