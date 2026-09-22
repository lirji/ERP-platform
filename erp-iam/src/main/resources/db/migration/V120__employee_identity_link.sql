-- P11 发现原计划员工链路缺失；增量扩展，不改变现有用户认证边界。
CREATE UNIQUE INDEX uk_iam_org_tenant_id ON iam_org(tenant_id,id);
CREATE UNIQUE INDEX uk_iam_user_tenant_id ON iam_user(tenant_id,id);
CREATE TABLE iam_employee (
    id bigserial PRIMARY KEY,
    tenant_id bigint NOT NULL REFERENCES iam_tenant(id),
    company_id bigint NOT NULL,
    org_id bigint NOT NULL,
    employee_no varchar(64) NOT NULL,
    name varchar(128) NOT NULL,
    user_id bigint,
    status varchar(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','LEFT')),
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    left_at timestamptz,
    UNIQUE (tenant_id,employee_no),
    UNIQUE (tenant_id,user_id),
    FOREIGN KEY (tenant_id,company_id) REFERENCES iam_org(tenant_id,id),
    FOREIGN KEY (tenant_id,org_id) REFERENCES iam_org(tenant_id,id),
    FOREIGN KEY (tenant_id,user_id) REFERENCES iam_user(tenant_id,id),
    CHECK ((status='ACTIVE' AND left_at IS NULL) OR (status='LEFT' AND left_at IS NOT NULL))
);
COMMENT ON TABLE iam_employee IS '员工入职及用户关联；离职与禁用关联用户同事务';
COMMENT ON COLUMN iam_employee.id IS '员工主键';
COMMENT ON COLUMN iam_employee.tenant_id IS '租户';
COMMENT ON COLUMN iam_employee.company_id IS '所属公司组织';
COMMENT ON COLUMN iam_employee.org_id IS '所属部门';
COMMENT ON COLUMN iam_employee.employee_no IS '租户内唯一工号';
COMMENT ON COLUMN iam_employee.name IS '员工显示姓名，演示数据使用虚构姓名';
COMMENT ON COLUMN iam_employee.user_id IS '关联ERP用户，尚未开通时为空';
COMMENT ON COLUMN iam_employee.status IS 'ACTIVE在职，LEFT离职；不删除历史身份';
COMMENT ON COLUMN iam_employee.version IS '生命周期版本';
COMMENT ON COLUMN iam_employee.created_at IS '入职记录时间';
COMMENT ON COLUMN iam_employee.left_at IS '离职记录时间';
