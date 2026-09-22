# Codex Progress

## 任务目标

接手 Claude 会话 `6ce8fae5-f198-48db-ae23-48bd5dbc80c8`，用户已明确完成 P5 后继续 P6，做到采购到付款、订单到收款两条 MVP 业务闭环。

## 已完成

- P5 销售出库与真实信用审批：104 项回归通过，提交 `67e3ed1`，合并 `edd85db`，已推送 main 与 P5 分支。
- P6 已实现应收/应付、收款/付款、核销/反核销、v2 财务事件、后台 Outbox 调度和权威信用查询；实现提交 ad38b1f，合并 067b732，已推送 main 与 P6 分支并核对远端一致。
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

- 本轮 P5→P6 MVP 内部业务闭环及 Git 交付已完成，无剩余授权实施事项。

## 当前问题

- 历史 BLOCK-P1-01：生产 OIDC 未接入；本轮没有新增销售/财务 HTTP 入口，也没有执行生产部署。
- 历史 v1 只有关系载荷，不自动补财务账；新闭环使用完整 v2。
- 未发现原 Runtime run_id；沿用 ROADMAP 和明确用户授权，不伪造 Runtime Gate。

## 下一步建议

1. 本次目标已完成；详细验收见 `.engineering/gates/GATE-P6-20260922.md`，交付见 `DELIVERY_RESULT-P5-P6.md`。
2. 如另行要求对外提供产品能力，先补生产 OIDC、HTTP 入口权限及实际租户币种/编号配置；不把内部 E2E 当生产验收。
3. 如需迁移已有历史单据，单独规划 v1 到 v2 财务回填与对账，不直接猜金额。

## 恢复 Prompt

请先读取 `CODEX_PROGRESS.md`。P5、P6 及 Git 交付已完成，不要重复实施。依据新的明确目标继续；生产 OIDC、HTTP 入口、历史补账和 P7–P11 仍属于后续范围，未执行生产部署。
