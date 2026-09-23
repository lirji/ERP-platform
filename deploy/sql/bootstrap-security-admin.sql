-- 受控本地/运维入口：psql -v ON_ERROR_STOP=1 -v tenant_code=... -v username=... -f 本文件。
-- 不创建身份、不接受密码；必须已有OIDC租户及绑定用户。无匿名HTTP bootstrap。
BEGIN;
SELECT id FROM iam_tenant WHERE code=:'tenant_code' AND enabled FOR UPDATE;
CREATE TEMP TABLE fbl_admin_target ON COMMIT DROP AS
 SELECT t.id tenant_id,u.id user_id FROM iam_tenant t JOIN iam_user u ON u.tenant_id=t.id
 WHERE t.code=:'tenant_code' AND u.username=:'username' AND t.enabled AND u.enabled
 AND t.oidc_issuer IS NOT NULL AND t.oidc_owner IS NOT NULL AND u.external_id IS NOT NULL AND btrim(u.external_id)<>'';
COMMENT ON TABLE fbl_admin_target IS '本事务内显式指定的既有OIDC绑定目标';
COMMENT ON COLUMN fbl_admin_target.tenant_id IS '已验证租户';
COMMENT ON COLUMN fbl_admin_target.user_id IS '已验证身份';
DO $$ BEGIN
 IF (SELECT count(*) FROM fbl_admin_target) <> 1 THEN RAISE EXCEPTION '需要唯一有效的OIDC绑定身份'; END IF;
 IF EXISTS(SELECT 1 FROM iam_role r JOIN fbl_admin_target t ON t.tenant_id=r.tenant_id WHERE r.code='SECURITY_ADMIN' AND (NOT r.protected OR NOT r.enabled))
 THEN RAISE EXCEPTION '同名普通角色冲突，拒绝自动提升'; END IF;
END $$;
INSERT INTO iam_role(tenant_id,code,name,protected) SELECT tenant_id,'SECURITY_ADMIN','安全管理员',TRUE FROM fbl_admin_target ON CONFLICT(tenant_id,code) DO NOTHING;
INSERT INTO iam_role_permission(tenant_id,role_id,permission)
 SELECT r.tenant_id,r.id,p.permission FROM iam_role r JOIN fbl_admin_target t ON t.tenant_id=r.tenant_id
 CROSS JOIN (VALUES ('iam:security:admin'),('iam:role:read'),('iam:role:write'),('iam:user:read'),('iam:user:assign'),('iam:org:read')) p(permission)
 WHERE r.code='SECURITY_ADMIN' AND r.protected ON CONFLICT DO NOTHING;
INSERT INTO iam_user_role(tenant_id,user_id,role_id)
 SELECT t.tenant_id,t.user_id,r.id FROM fbl_admin_target t JOIN iam_role r ON r.tenant_id=t.tenant_id AND r.code='SECURITY_ADMIN' AND r.protected
 ON CONFLICT DO NOTHING;
COMMIT;
