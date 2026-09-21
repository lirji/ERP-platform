-- =====================================================================
-- 给审计日志补齐数据权限所需的列。
--
-- 为什么新增迁移而不是改 V30：V30 已在本地与测试库执行过，
-- 修改已执行的迁移会破坏 Flyway 校验和，等于伪造历史
-- （开发规范 §设计 8、工程约定 §7）。修正一律走新迁移。
--
-- 数据权限列约定（凡进入数据权限的表必须具备）：
--   tenant_id   租户（硬隔离，由租户拦截器注入）
--   company_id  法人主体（SPECIFIED_COMPANY 范围用）
--   org_path    创建者所属组织的物化路径（DEPT / DEPT_AND_BELOW / SPECIFIED_ORG 范围用）
--   created_by  创建人（SELF 范围用）
-- =====================================================================

ALTER TABLE doc_audit_log ADD COLUMN company_id BIGINT;
ALTER TABLE doc_audit_log ADD COLUMN org_path   VARCHAR(512);
ALTER TABLE doc_audit_log ADD COLUMN created_by BIGINT;

COMMENT ON COLUMN doc_audit_log.company_id IS '操作人所属法人主体 ID；SPECIFIED_COMPANY 数据范围按此列过滤';
COMMENT ON COLUMN doc_audit_log.org_path   IS '操作人所属组织的物化路径；DEPT/DEPT_AND_BELOW/SPECIFIED_ORG 数据范围按此列前缀过滤';
COMMENT ON COLUMN doc_audit_log.created_by IS '审计记录创建人（即操作人）；SELF 数据范围按此列过滤。与 user_id 语义相同，独立成列是为了让全部受控表具备统一的数据权限列名';

-- 数据权限前缀查询走索引；text_pattern_ops 让 LIKE '/1/23/%' 可用索引
CREATE INDEX idx_doc_audit_scope
    ON doc_audit_log (tenant_id, org_path text_pattern_ops);
