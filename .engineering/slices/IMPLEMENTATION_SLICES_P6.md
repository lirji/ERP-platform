# P6 — AR / AP / Settlement

- 稳定 ID：P6，沿用已批准 ROADMAP；用户 2026-09-22 明确授权做到 MVP 闭环。
- needs：P4 已交付、P5 已交付（main edd85db）。
- 状态：DONE（本地验收通过，Git 交付待完成）。
- owner：backend-implementation；验证 implementation-validation；交付 task-git-delivery。
- 契约：API_P6_FINANCE.md、CONTRACTS.md、EVENT_CATALOG.md、DOMAIN_MAP / DATA_ARCHITECTURE。
- 范围：finance 模块，kernel 事件 DTO/可靠投递，采购/销售事件财务载荷，masterdata 本位币查询，app 调度与真实数据库测试。
- Runtime：复用已运行 PostgreSQL 16，不新增中间件，不部署生产。
- 验收：ROADMAP P6 七条出口及 API_P6_FINANCE 的故障/并发要求。
- Git：feat/p6-finance-settlement；完成必要验证后提交、正常合并推送 main。
- 仍开放：BLOCK-P1-01 生产 OIDC；历史 v1 缺财务载荷不得臆造补账。

- 验证：GATE-P6-20260922 / TEST_RESULT-P6.json，120 项全部通过。
