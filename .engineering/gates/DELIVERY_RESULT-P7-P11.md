# P7–P11 交付记录

用户已授权按原路线持续执行；P7 已结算退货采用独立红字调整及待退款记录，保留原现金、核销事实。

| 阶段 | 结果 | Git 合并 |
|---|---|---|
| P7 成本/库存作业/退货 | PASS，146 项回归 | 1720e12 |
| P8 复杂审批 | 条件未触发，不新增会签等复杂能力 | 不适用 |
| P9 报表 | PASS，151 项回归；规模测量见 REPORT_SCALE-P9.json | af71858 |
| P10 可观测性 | PASS，155 项回归 | e9e6029 |
| P11 本地交付 | PASS，158 项回归；员工链路、Compose、演示、CI、空库基线 | 41b6854 |

P11 任务分支 `feat/p11-local-delivery`，逻辑提交：`b1d930d` 员工链路，`671e88e` 本地运行/种子/CI，`e735c06` 空库迁移修复，`ac8c331` 验收证据。已正常合并并推送 main，无强推、无生产部署。后续仅文档回写，不改变已验证源码。

任务分支 CI [35681340638](https://github.com/lirji/ERP-platform/actions/runs/35681340638) SUCCESS，绑定 e735c068dd1bdc55ea2f76ef503a947725344d54，JUnit artifact 核对为 158 项回归及 1 项显式种子命令，均无失败/错误/跳过。首次失败 35681078016 及 B30 修复保留在 TEST_RESULT-P11.json。

主分支 CI：[35681661831](https://github.com/lirji/ERP-platform/actions/runs/35681661831) SUCCESS，绑定合并提交 `41b6854a0f5e47f84e62cea36c3031761d1e9859`。全部步骤通过；之后只追加交付文档，路径过滤不触发新的 CI，未把未运行的文档提交冒充 CI 目标。

当前本地应用健康、业务指标 OK，演示租户 990001 已重建并验证。使用 `deploy/up.sh`、`test-data/init-test-data.sh`、`test-data/verify-test-data.sh`、`deploy/smoke.sh`；详情见 deploy/README.md。

未纳入交付的工作区修改仍在本地；可见格式调整来自并行编辑，本任务不覆盖、不暂存。交付结束前记录如下（后续编辑可能继续增加）：

- `erp-finance/src/main/java/com/lrj/erp/finance/infrastructure/CreditMapper.java`
- `erp-finance/src/main/java/com/lrj/erp/finance/infrastructure/FinanceMapper.java`
- `erp-finance/src/main/java/com/lrj/erp/finance/infrastructure/MyBatisCreditRepository.java`
- `erp-finance/src/main/java/com/lrj/erp/finance/infrastructure/ReceivableReportMapper.java`
- `erp-finance/src/main/java/com/lrj/erp/finance/infrastructure/ReceivableReportSource.java`
- `erp-inventory/src/main/java/com/lrj/erp/inventory/domain/CostSnapshot.java`
- `erp-inventory/src/main/java/com/lrj/erp/inventory/domain/InventoryBalance.java`
- `erp-inventory/src/main/java/com/lrj/erp/inventory/domain/InventoryBucket.java`
- `erp-kernel/src/main/java/com/lrj/erp/kernel/outbox/InProcessOutboxDelivery.java`
- `erp-kernel/src/main/java/com/lrj/erp/kernel/outbox/OutboxHealthService.java`
- `erp-kernel/src/main/java/com/lrj/erp/kernel/reporting/ReportSource.java`

边界：当前内部服务两条 MVP 闭环已验证；生产 OIDC、销售/财务 HTTP 入口、前端及历史 v1 财务金额回填尚未完成。P8 没有复杂审批需求。未承诺生产容量/SLA，未执行生产部署。
