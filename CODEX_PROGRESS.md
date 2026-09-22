# Codex Progress

## 任务目标

继续原计划 P7–P11；P8 仅在真实复杂审批需求触发时实施。用户已确认：退货生成独立红字调整及待退款记录，保留原收付款事实。当前分支 `feat/p10-observability`，P7 已验证并交付，P10 已验证，接续 P11。

历史目标：接手 Claude 会话 `6ce8fae5-f198-48db-ae23-48bd5dbc80c8`，用户已明确完成 P5 后继续 P6，做到采购到付款、订单到收款两条 MVP 业务闭环。

## 已完成

- P10 业务审计、低基数指标、对账探针和告警手册完成，155项全量回归通过。
- P9 已提交 f22b95e，合并 af71858，已推送 origin/main。

- P9 已完成五类快照及可恢复重建，151 项全量回归通过；显式规模测试和 CLI 通过，详见 TEST_RESULT-P9.json / REPORT_SCALE-P9.json。

- P7.3 采购/销售退货、独立红字/待退款/退款确认已完成，146 项全量回归通过；Gate 和 TEST_RESULT-P7 已落盘。P8 条件未触发。
- P7.1/P7.2 已合并推送 origin/main（40e6d02）；P7.3 已提交 d31732e 并合并推送 origin/main。

- P7.2 调拨/冻结盘点/审批调整完成，133 项全量回归通过（证据 TEST_RESULT-P7-2.json）；P7.3 现已完成。

- P7.1 成本计价已实现：数量/成本原子过账、采购成本接入、旧写入失效保护，127 项回归通过。证据 `.engineering/gates/TEST_RESULT-P7-1.json`。

- P5 销售出库与真实信用审批：104 项回归通过，提交 `67e3ed1`，合并 `edd85db`，已推送 main 与 P5 分支。
- P6 已实现应收/应付、收款/付款、核销/反核销、v2 财务事件、后台 Outbox 调度和权威信用查询；实现提交 ad38b1f，合并 067b732，已推送 main 与 P6 分支并核对远端一致。
- 两条内部服务 E2E、真实数据库重复/并发/失败恢复与舍入测试已增加。
- 复核并修复：多行销售金额舍入、采购并发累计金额、关系投影失败吞异常、发布时间测量口径。
- 最终全量回归 120 项通过（33 单元/架构 + 87 集成）；Outbox 本地 P95 1.13772545s；Gate/TEST_RESULT 已落盘。

## 已修改文件

当前 P9 未提交改动：`erp-kernel/.../reporting/ReportSource.java`；inventory/procurement/sales/finance 各自的 `*ReportSource/*ReportMapper` 导出；`erp-reporting/src/main/` 快照任务/投影/查询/清理；`erp-app/.../ReportRebuildCommand.java`；`scripts/rebuild-report.sh`；`ReportSnapshotIT`、显式规模测试 `ReportScaleBenchmark`；P9 契约/切片/ADR-007。


- `erp-finance/src/main/`：财务业务、Mapper、V90 迁移。
- `erp-kernel/src/main/`：v2 事件 DTO、权威信用查询端口、Outbox 实际完成时间。
- `erp-procurement/src/main/`、`erp-sales/src/main/`：财务事件金额/币种、并发锁与信用联动。
- `erp-masterdata/src/main/`：本位币查询服务。
- `erp-document/src/main/java/com/lrj/erp/document/service/LineageEventListener.java`：失败重试。
- `erp-app/src/main/java/com/lrj/erp/app/outbox/`、`erp-app/src/test/`：后台调度及真实数据库测试。
- `docs/design/erp-platform/contracts/`、`docs/design/erp-platform/adr/ADR-005-financial-events-and-credit.md`、`.engineering/slices/IMPLEMENTATION_SLICES_P6.md`。

## 未完成

- P10 Git交付，然后 P11 本地Compose应用、隔离演示数据、CI真实运行与最终验收。

## 当前问题

- 历史 BLOCK-P1-01：生产 OIDC 未接入；本轮没有新增销售/财务 HTTP 入口，也没有执行生产部署。
- 历史 v1 只有关系载荷，不自动补财务账；新闭环使用完整 v2。
- 未发现原 Runtime run_id；沿用 ROADMAP 和明确用户授权，不伪造 Runtime Gate。

## 下一步建议

1. 继续当前 P9 规模测试和最终验证，再交付并进入 P10/P11。历史 P5/P6 目标已完成；详细验收见 `.engineering/gates/GATE-P6-20260922.md`，交付见 `DELIVERY_RESULT-P5-P6.md`。
2. 如另行要求对外提供产品能力，先补生产 OIDC、HTTP 入口权限及实际租户币种/编号配置；不把内部 E2E 当生产验收。
3. 如需迁移已有历史单据，单独规划 v1 到 v2 财务回填与对账，不直接猜金额。

## 恢复 Prompt

请先读取 `CODEX_PROGRESS.md`。P5、P6 及 Git 交付已完成，不要重复实施。依据新的明确目标继续；P7 已通过 Gate 并推送 main（1720e12）。当前 feat/p9-reporting 含未提交 P9 实现，先检查 /tmp/p9-scale.log 和实际进程，完成 P9 验证与交付，再推进 P10/P11，P8 按条件执行。生产 OIDC/HTTP 与历史补账边界仍需保持明确，未执行生产部署。
