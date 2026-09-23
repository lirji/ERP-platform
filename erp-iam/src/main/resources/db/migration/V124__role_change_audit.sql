CREATE TABLE iam_role_change_audit (
 id BIGSERIAL PRIMARY KEY,
 tenant_id BIGINT NOT NULL,
 role_id BIGINT NOT NULL,
 actor_id BIGINT NOT NULL,
 action VARCHAR(32) NOT NULL,
 before_json JSONB,
 after_json JSONB NOT NULL,
 trace_id VARCHAR(128) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON TABLE iam_role_change_audit IS '角色配置变更前后事实，与业务事务同提交；不含认证凭据';
COMMENT ON COLUMN iam_role_change_audit.id IS '审计记录主键';
COMMENT ON COLUMN iam_role_change_audit.tenant_id IS '租户归属';
COMMENT ON COLUMN iam_role_change_audit.role_id IS '受影响角色';
COMMENT ON COLUMN iam_role_change_audit.actor_id IS '操作人';
COMMENT ON COLUMN iam_role_change_audit.action IS 'CREATE/UPDATE/PERMISSIONS等稳定动作码';
COMMENT ON COLUMN iam_role_change_audit.before_json IS '修改前角色配置，新建时为空';
COMMENT ON COLUMN iam_role_change_audit.after_json IS '修改后角色配置，不存token或身份凭据';
COMMENT ON COLUMN iam_role_change_audit.trace_id IS '请求关联号';
COMMENT ON COLUMN iam_role_change_audit.created_at IS '事实记录时间';
CREATE INDEX idx_role_change_audit_role ON iam_role_change_audit(tenant_id,role_id,id);
