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
