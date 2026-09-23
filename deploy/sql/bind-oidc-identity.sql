-- 仅由受控运维执行的显式 crosswalk。不会按 JWT 用户名自动注册或覆盖已有身份。
-- psql -v tenant_code=... -v username=... -v issuer=... -v owner=... -v subject=... -f 本文件
\set ON_ERROR_STOP on
BEGIN;
SELECT set_config('erp.bind.tenant_code', :'tenant_code', true),
       set_config('erp.bind.username', :'username', true),
       set_config('erp.bind.issuer', :'issuer', true),
       set_config('erp.bind.owner', :'owner', true),
       set_config('erp.bind.subject', :'subject', true) \gset
DO $$
DECLARE t iam_tenant%ROWTYPE; u iam_user%ROWTYPE;
BEGIN
    IF length(trim(current_setting('erp.bind.subject'))) = 0 THEN
        RAISE EXCEPTION 'subject 不能为空';
    END IF;
    SELECT * INTO STRICT t FROM iam_tenant
      WHERE code = current_setting('erp.bind.tenant_code') FOR UPDATE;
    IF NOT t.enabled THEN RAISE EXCEPTION '租户未启用'; END IF;
    IF t.oidc_issuer IS NOT NULL AND (t.oidc_issuer <> current_setting('erp.bind.issuer')
       OR t.oidc_owner <> current_setting('erp.bind.owner')) THEN
        RAISE EXCEPTION '已有租户身份绑定冲突，拒绝覆盖';
    END IF;
    SELECT * INTO STRICT u FROM iam_user WHERE tenant_id=t.id
      AND username=current_setting('erp.bind.username') FOR UPDATE;
    IF NOT u.enabled THEN RAISE EXCEPTION '用户未启用'; END IF;
    IF u.external_id IS NOT NULL AND u.external_id <> current_setting('erp.bind.subject') THEN
        RAISE EXCEPTION '已有用户身份绑定冲突，拒绝覆盖';
    END IF;
    UPDATE iam_tenant SET oidc_issuer=current_setting('erp.bind.issuer'),
      oidc_owner=current_setting('erp.bind.owner') WHERE id=t.id;
    UPDATE iam_user SET external_id=current_setting('erp.bind.subject') WHERE id=u.id AND tenant_id=t.id;
END $$;
COMMIT;
