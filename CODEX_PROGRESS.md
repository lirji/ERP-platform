# Codex Progress

## 任务目标

接手 Claude 会话 `6ce8fae5-f198-48db-ae23-48bd5dbc80c8`，用户已明确完成 P5 后继续 P6，做到采购到付款、订单到收款两条 MVP 业务闭环。

## 已完成

- P5 销售出库与真实信用审批：104 项回归通过，提交 `67e3ed1`，合并 `edd85db`，已推送 main 与 P5 分支。
- P6 已实现应收/应付、收款/付款、核销/反核销、v2 财务事件、后台 Outbox 调度和权威信用查询。
- 两条内部服务 E2E、真实数据库重复/并发/失败恢复与舍入测试已增加。
- 复核并修复：多行销售金额舍入、采购并发累计金额、关系投影失败吞异常、发布时间测量口径。
- 最终全量回归 120 项通过（33 单元/架构 + 87 集成）；Outbox 本地 P95 1.13772545s；Gate/TEST_RESULT 已落盘。

## 已修改文件

- `erp-finance/src/main/`：财务业务、Mapper、V90 迁移。
- `erp-kernel/src/main/`：v2 事件 DTO、权威信用查询端口、Outbox 实际完成时间。
- `erp-procurement/src/main/`、`erp-sales/src/main/`：财务事件金额/币种、并发锁与信用联动。
- `erp-masterdata/src/main/`：本位币查询服务。
- `erp-document/src/main/java/com/lrj/erp/document/service/LineageEventListener.java`：失败重试。
- `erp-app/src/main/java/com/lrj/erp/app/outbox/`、`erp-app/src/test/`：后台调度及真实数据库测试。
- `docs/design/erp-platform/contracts/`、`docs/design/erp-platform/adr/ADR-005-financial-events-and-credit.md`、`.engineering/slices/IMPLEMENTATION_SLICES_P6.md`。

## 未完成

- P6 提交、合并推送与远端核对。

## 当前问题

- 历史 BLOCK-P1-01：生产 OIDC 未接入；本轮没有新增销售/财务 HTTP 入口，也没有执行生产部署。
- 历史 v1 只有关系载荷，不自动补财务账；新闭环使用完整 v2。
- 未发现原 Runtime run_id；沿用 ROADMAP 和明确用户授权，不伪造 Runtime Gate。

## 下一步建议

1. 核对 `/tmp/erp-p6-verified.log` 及 Surefire/Failsafe XML；任何失败先修复。
2. 写 P6 验收证据与当前进度；复核 diff，只提交本任务路径。
3. 在 `feat/p6-finance-settlement` 提交，正常合并推送 main，核实远端 SHA 并回写交付结果。

## 恢复 Prompt

请读取 `CODEX_PROGRESS.md`，从“未完成”和“下一步建议”继续完成 P6 MVP 闭环交付。复用当前分支，不重做 P5，不等待“继续”；保护用户改动，不强推，不部署生产。
