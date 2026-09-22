# Codex Progress

## 任务目标

接手 Claude 会话 `6ce8fae5-f198-48db-ae23-48bd5dbc80c8` 中断的 ERP 交付，从 P5 销售到出库继续。
用户已明确：完成 P5 后继续 P6，做到两条 MVP 业务闭环。

## 已完成

- 确认 P4 已合并，基线 `f8587a9`；复用 `feat/p5-order-to-ship`。
- 接管 Claude 留下的 7 个未提交销售文件。
- 补订单聚合锁、状态/行归属校验、真实信用审批绑定及一次使用约束、签收前置条件、数量精度校验。
- 新增 12 项 P5 PostgreSQL 集成测试；全量回归 104 项通过，首轮 Mapper 参数错误已修复。

## 已修改文件

- `erp-sales/src/main/`（销售服务、领域端口、Mapper、V80/V81 迁移）
- `erp-approval/src/main/`（审批实例归属与状态校验端口）
- `erp-app/src/test/java/com/lrj/erp/it/OrderToShipE2EIT.java`
- `CODEX_PROGRESS.md`

## 未完成

- P5 提交合并推送；全量回归 104 项已通过，Gate 和内部服务边界说明已写。
- P6 尚未开始，用户已授权继续直到 MVP 闭环。

## 当前问题

- 历史 `BLOCK-P1-01`：生产 OIDC 未接入，不允许对外暴露。
- 没有发现可恢复的 Runtime run_id；原计划仅 M0 细分 ChangeSet，当前按已有 ROADMAP P5 和用户接手授权推进，不伪造 Runtime Gate。

## 下一步建议

1. 跑 `mvn clean verify`，核对 P5 及既有回归。
2. 回写 P5 Gate、服务边界与限制；按持续授权提交、正常合并推送 main。
3. 根据用户范围继续 P6；保留 OIDC 历史阻塞。

## 恢复 Prompt

请读取 `CODEX_PROGRESS.md`，基于其中的“未完成”和“下一步建议”继续执行。不要重新规划全部任务，不要等待我输入“继续”，除非遇到缺少信息、危险操作或权限问题。
