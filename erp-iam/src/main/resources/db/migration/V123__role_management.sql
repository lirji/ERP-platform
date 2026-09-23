ALTER TABLE iam_role ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE iam_role ADD COLUMN protected BOOLEAN NOT NULL DEFAULT FALSE;
COMMENT ON COLUMN iam_role.version IS '角色配置乐观锁版本；所有写入成功递增';
COMMENT ON COLUMN iam_role.protected IS '受控初始化的安全管理员角色，普通管理接口禁止修改或分配';
