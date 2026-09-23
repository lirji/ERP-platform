-- 外部组织与本地租户显式绑定，不能把可伪造请求头或可变用户名当登录映射。
ALTER TABLE iam_tenant ADD COLUMN oidc_issuer VARCHAR(512);
ALTER TABLE iam_tenant ADD COLUMN oidc_owner VARCHAR(128);
COMMENT ON COLUMN iam_tenant.oidc_issuer IS '受信任 OIDC 签发方，与应用配置及已验证令牌 iss 精确一致';
COMMENT ON COLUMN iam_tenant.oidc_owner IS 'Casdoor 外部组织 owner，仅用于身份到 ERP 租户的显式映射';
ALTER TABLE iam_tenant ADD CONSTRAINT ck_iam_tenant_oidc_binding CHECK (
    (oidc_issuer IS NULL AND oidc_owner IS NULL) OR
    (oidc_issuer IS NOT NULL AND length(trim(oidc_issuer)) > 0
     AND oidc_owner IS NOT NULL AND length(trim(oidc_owner)) > 0));
CREATE UNIQUE INDEX uk_iam_tenant_oidc ON iam_tenant (oidc_issuer, oidc_owner)
    WHERE oidc_issuer IS NOT NULL;
