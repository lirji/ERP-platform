> 日期：2026-09-23；protocol: `capability-exploration-report/v1`；baseline: `engineering-baseline/v1`。
> explorationSummary：现状：业务内核和本地工程验证较完整，面向真实用户的 ERP 产品入口与生产运行闭环仍不完整。优先补齐业务 API/页面、可信授权与主数据管理、生产运行验收；暂不拆微服务或新增无实际需求的中间件。

# Evidence Index

以下路径相对仓库根目录，链接带证据定位行。源码证明当前结构与行为；历史 Gate 只证明当时验证结果。“未发现”以本节扫描范围为界。

<a id="e01"></a>
## E01

| Field | Value |
|---|---|
| Finding | 12 个模块，单一应用部署 |
| Capability | 模块边界 |
| Evidence Type | 源码/配置/运行文档 |
| File | [pom.xml](../../pom.xml#L27) |
| Class | `<modules>`（SQL/文档为定位文本） |
| Method | Maven reactor |
| Configuration | — |
| SQL·Table | — |
| Confidence | `FACT` |

<a id="e02"></a>
## E02

| Field | Value |
|---|---|
| Finding | 生产业务 Controller 仅 IAM 与登录；扫描其他模块未发现业务 HTTP 入口 |
| Capability | HTTP |
| Evidence Type | 源码/配置/运行文档 |
| File | [erp-app/src/main/java/com/lrj/erp/app/web/iam/IamController.java](../../erp-app/src/main/java/com/lrj/erp/app/web/iam/IamController.java#L23) |
| Class | `class IamController`（SQL/文档为定位文本） |
| Method | me / roles |
| Configuration | — |
| SQL·Table | iam_role |
| Confidence | `FACT` |

<a id="e03"></a>
## E03

| Field | Value |
|---|---|
| Finding | 静态资源仅登录页，无业务工作台 |
| Capability | 前端 |
| Evidence Type | 源码/配置/运行文档 |
| File | [erp-app/src/main/resources/static/auth/login.js](../../erp-app/src/main/resources/static/auth/login.js#L15) |
| Class | `UserManager`（SQL/文档为定位文本） |
| Method | 登录、刷新、展示身份 |
| Configuration | — |
| SQL·Table | — |
| Confidence | `FACT` |

<a id="e04"></a>
## E04

| Field | Value |
|---|---|
| Finding | SKU 生命周期与批量校验已实现；其他主数据缺等价服务 |
| Capability | 主数据 |
| Evidence Type | 源码/配置/运行文档 |
| File | [erp-masterdata/src/main/java/com/lrj/erp/masterdata/service/SkuService.java](../../erp-masterdata/src/main/java/com/lrj/erp/masterdata/service/SkuService.java#L22) |
| Class | `class SkuService`（SQL/文档为定位文本） |
| Method | create / updateKeyFields / importBatch |
| Configuration | — |
| SQL·Table | md_sku / md_reference |
| Confidence | `FACT` |

<a id="e05"></a>
## E05

| Field | Value |
|---|---|
| Finding | 供应商、客户、仓库等有表，不能据此认定管理流程完成 |
| Capability | 主数据 |
| Evidence Type | 源码/配置/运行文档 |
| File | [erp-masterdata/src/main/resources/db/migration/V50__masterdata.sql](../../erp-masterdata/src/main/resources/db/migration/V50__masterdata.sql#L99) |
| Class | `CREATE TABLE md_supplier`（SQL/文档为定位文本） |
| Method | DDL |
| Configuration | — |
| SQL·Table | md_supplier / md_customer / md_warehouse 等 |
| Confidence | `FACT` |

<a id="e06"></a>
## E06

| Field | Value |
|---|---|
| Finding | 采购下单、审批、收货、取消闭环存在 |
| Capability | 采购 |
| Evidence Type | 源码/配置/运行文档 |
| File | [erp-procurement/src/main/java/com/lrj/erp/procurement/application/PurchaseOrderService.java](../../erp-procurement/src/main/java/com/lrj/erp/procurement/application/PurchaseOrderService.java#L35) |
| Class | `class PurchaseOrderService`（SQL/文档为定位文本） |
| Method | createOrder / receive / cancel |
| Configuration | — |
| SQL·Table | pur_order / pur_order_line |
| Confidence | `FACT` |

<a id="e07"></a>
## E07

| Field | Value |
|---|---|
| Finding | 销售预占、信用占用与超限审批存在；信用额度由调用方传入 |
| Capability | 销售 |
| Evidence Type | 源码/配置/运行文档 |
| File | [erp-sales/src/main/java/com/lrj/erp/sales/application/SalesOrderService.java](../../erp-sales/src/main/java/com/lrj/erp/sales/application/SalesOrderService.java#L78) |
| Class | `客户授信上限`（SQL/文档为定位文本） |
| Method | createOrder / requestCreditApproval / usedCredit |
| Configuration | — |
| SQL·Table | sal_order / 信用占用表 |
| Confidence | `FACT` |

<a id="e08"></a>
## E08

| Field | Value |
|---|---|
| Finding | 库存过账与成本服务存在 |
| Capability | 库存 |
| Evidence Type | 源码/配置/运行文档 |
| File | [erp-inventory/src/main/java/com/lrj/erp/inventory/application/StockPostingService.java](../../erp-inventory/src/main/java/com/lrj/erp/inventory/application/StockPostingService.java#L23) |
| Class | `class StockPostingService`（SQL/文档为定位文本） |
| Method | post / cost / balance |
| Configuration | — |
| SQL·Table | inv_balance / inv_transaction |
| Confidence | `FACT` |

<a id="e09"></a>
## E09

| Field | Value |
|---|---|
| Finding | 预占只提供主动消耗释放，未实现过期回收 |
| Capability | 预占 |
| Evidence Type | 源码/配置/运行文档 |
| File | [erp-inventory/src/main/java/com/lrj/erp/inventory/application/ReservationService.java](../../erp-inventory/src/main/java/com/lrj/erp/inventory/application/ReservationService.java#L20) |
| Class | `class ReservationService`（SQL/文档为定位文本） |
| Method | reserve / consume / releaseRemaining |
| Configuration | — |
| SQL·Table | inv_reservation |
| Confidence | `FACT` |

<a id="e10"></a>
## E10

| Field | Value |
|---|---|
| Finding | 预占表无到期字段 |
| Capability | 预占 |
| Evidence Type | 源码/配置/运行文档 |
| File | [erp-inventory/src/main/resources/db/migration/V60__inventory.sql](../../erp-inventory/src/main/resources/db/migration/V60__inventory.sql#L98) |
| Class | `CREATE TABLE inv_reservation`（SQL/文档为定位文本） |
| Method | DDL |
| Configuration | — |
| SQL·Table | inv_reservation |
| Confidence | `FACT` |

<a id="e11"></a>
## E11

| Field | Value |
|---|---|
| Finding | 调拨在途、盘点、调整已有应用服务 |
| Capability | 仓储作业 |
| Evidence Type | 源码/配置/运行文档 |
| File | [erp-inventory/src/main/java/com/lrj/erp/inventory/application/StockOperationService.java](../../erp-inventory/src/main/java/com/lrj/erp/inventory/application/StockOperationService.java#L19) |
| Class | `class StockOperationService`（SQL/文档为定位文本） |
| Method | createTransfer / beginCount / execute / receiveTransfer |
| Configuration | — |
| SQL·Table | inv_stock_operation |
| Confidence | `FACT` |

<a id="e12"></a>
## E12

| Field | Value |
|---|---|
| Finding | 收付款、核销、反核销已有应用服务 |
| Capability | 往来财务 |
| Evidence Type | 源码/配置/运行文档 |
| File | [erp-finance/src/main/java/com/lrj/erp/finance/application/SettlementService.java](../../erp-finance/src/main/java/com/lrj/erp/finance/application/SettlementService.java#L19) |
| Class | `class SettlementService`（SQL/文档为定位文本） |
| Method | acceptPosted / recordCash / apply / reverse |
| Configuration | — |
| SQL·Table | fin_* |
| Confidence | `FACT` |

<a id="e13"></a>
## E13

| Field | Value |
|---|---|
| Finding | 财务消费入口只接收 v2 出入库事实 |
| Capability | 历史数据兼容 |
| Evidence Type | 源码/配置/运行文档 |
| File | [erp-finance/src/main/java/com/lrj/erp/finance/application/FinancialPostingListener.java](../../erp-finance/src/main/java/com/lrj/erp/finance/application/FinancialPostingListener.java#L16) |
| Class | `仅消费完整 v2`（SQL/文档为定位文本） |
| Method | on |
| Configuration | — |
| SQL·Table | Outbox / fin_* |
| Confidence | `FACT` |

<a id="e14"></a>
## E14

| Field | Value |
|---|---|
| Finding | 内置审批仅实例提交及决策，非完整待办工作台 |
| Capability | 审批 |
| Evidence Type | 源码/配置/运行文档 |
| File | [erp-approval/src/main/java/com/lrj/erp/approval/service/BuiltInApprovalService.java](../../erp-approval/src/main/java/com/lrj/erp/approval/service/BuiltInApprovalService.java#L19) |
| Class | `class BuiltInApprovalService`（SQL/文档为定位文本） |
| Method | submit / approve / reject / matches |
| Configuration | — |
| SQL·Table | apr_instance |
| Confidence | `FACT` |

<a id="e15"></a>
## E15

| Field | Value |
|---|---|
| Finding | 审批表记录提交人与决策人，无分配任务模型 |
| Capability | 审批 |
| Evidence Type | 源码/配置/运行文档 |
| File | [erp-approval/src/main/resources/db/migration/V40__approval.sql](../../erp-approval/src/main/resources/db/migration/V40__approval.sql#L11) |
| Class | `CREATE TABLE apr_instance`（SQL/文档为定位文本） |
| Method | DDL |
| Configuration | — |
| SQL·Table | apr_instance |
| Confidence | `FACT` |

<a id="e16"></a>
## E16

| Field | Value |
|---|---|
| Finding | 指定组织/公司范围仍退化为部门及下级 |
| Capability | 数据权限 |
| Evidence Type | 源码/配置/运行文档 |
| File | [erp-iam/src/main/java/com/lrj/erp/iam/service/AccessContextAssembler.java](../../erp-iam/src/main/java/com/lrj/erp/iam/service/AccessContextAssembler.java#L74) |
| Class | `SPECIFIED_*`（SQL/文档为定位文本） |
| Method | widestScope |
| Configuration | — |
| SQL·Table | iam_role |
| Confidence | `FACT` |

<a id="e17"></a>
## E17

| Field | Value |
|---|---|
| Finding | 数据权限只覆盖登记表；无独立仓库授权规则 |
| Capability | 数据权限 |
| Evidence Type | 源码/配置/运行文档 |
| File | [erp-iam/src/main/java/com/lrj/erp/iam/security/TenantAndDataScopeHandler.java](../../erp-iam/src/main/java/com/lrj/erp/iam/security/TenantAndDataScopeHandler.java#L54) |
| Class | `DATA_SCOPE_TABLES`（SQL/文档为定位文本） |
| Method | getSqlSegment |
| Configuration | — |
| SQL·Table | pur_order / sal_order / inv_stock_document / fin_account_* / doc_audit_log |
| Confidence | `FACT` |

<a id="e18"></a>
## E18

| Field | Value |
|---|---|
| Finding | OIDC 接入已实现，管理指标允许匿名访问 |
| Capability | 认证与管理面 |
| Evidence Type | 源码/配置/运行文档 |
| File | [erp-app/src/main/java/com/lrj/erp/app/security/SecurityConfiguration.java](../../erp-app/src/main/java/com/lrj/erp/app/security/SecurityConfiguration.java#L48) |
| Class | `requestMatchers`（SQL/文档为定位文本） |
| Method | securityFilterChain |
| Configuration | ERP_OIDC_* / /actuator/metrics/** |
| SQL·Table | — |
| Confidence | `FACT` |

<a id="e19"></a>
## E19

| Field | Value |
|---|---|
| Finding | Outbox 锁内批次投递、有限重试与死信 |
| Capability | 可靠性 |
| Evidence Type | 源码/配置/运行文档 |
| File | [erp-kernel/src/main/java/com/lrj/erp/kernel/outbox/OutboxDispatcher.java](../../erp-kernel/src/main/java/com/lrj/erp/kernel/outbox/OutboxDispatcher.java#L47) |
| Class | `dispatchBatch`（SQL/文档为定位文本） |
| Method | dispatchBatch |
| Configuration | erp.outbox.* |
| SQL·Table | Outbox |
| Confidence | `FACT` |

<a id="e20"></a>
## E20

| Field | Value |
|---|---|
| Finding | 五类快照可恢复重建，变更时 STALE，账龄非合同逾期 |
| Capability | 报表 |
| Evidence Type | 源码/配置/运行文档 |
| File | [erp-reporting/src/main/java/com/lrj/erp/reporting/application/ReportSnapshotService.java](../../erp-reporting/src/main/java/com/lrj/erp/reporting/application/ReportSnapshotService.java#L14) |
| Class | `class ReportSnapshotService`（SQL/文档为定位文本） |
| Method | step / snapshotPage / purgeStep / aging |
| Configuration | — |
| SQL·Table | rpt_* |
| Confidence | `FACT` |

<a id="e21"></a>
## E21

| Field | Value |
|---|---|
| Finding | 有业务指标及处置手册，无外部告警接收渠道 |
| Capability | 可观测性 |
| Evidence Type | 源码/配置/运行文档 |
| File | [docs/operations/OBSERVABILITY.md](../../docs/operations/OBSERVABILITY.md#L5) |
| Class | `尚未配置外部告警`（SQL/文档为定位文本） |
| Method | check-business-health.py |
| Configuration | erp.monitoring.* |
| SQL·Table | — |
| Confidence | `FACT` |

<a id="e22"></a>
## E22

| Field | Value |
|---|---|
| Finding | 本地回环发布、单应用加 PostgreSQL，不是生产交付 |
| Capability | 运行 |
| Evidence Type | 源码/配置/运行文档 |
| File | [deploy/compose.yaml](../../deploy/compose.yaml#L38) |
| Class | `127.0.0.1:${ERP_APP_PORT`（SQL/文档为定位文本） |
| Method | Compose |
| Configuration | ERP_APP_PORT / ERP_DB_* |
| SQL·Table | PostgreSQL |
| Confidence | `FACT` |

<a id="e23"></a>
## E23

| Field | Value |
|---|---|
| Finding | CI 包含真实数据库、构建、种子与清理、Smoke |
| Capability | 工程 |
| Evidence Type | 源码/配置/运行文档 |
| File | [.github/workflows/erp-ci.yml](../../.github/workflows/erp-ci.yml#L44) |
| Class | `mvn -B clean verify`（SQL/文档为定位文本） |
| Method | verify job |
| Configuration | — |
| SQL·Table | — |
| Confidence | `FACT` |

<a id="e24"></a>
## E24

| Field | Value |
|---|---|
| Finding | 179 项为历史 OIDC 验证结果，不是本轮重新执行结果 |
| Capability | 验证 |
| Evidence Type | 历史记录/规划 |
| File | [.engineering/gates/TEST_RESULT-P1-OIDC.json](../../.engineering/gates/TEST_RESULT-P1-OIDC.json#L8) |
| Class | `"tests": 179`（SQL/文档为定位文本） |
| Method | 历史回归 / Git CI |
| Configuration | — |
| SQL·Table | — |
| Confidence | `FACT` |

<a id="e25"></a>
## E25

| Field | Value |
|---|---|
| Finding | 存在专门规模测试，但数据与环境为合成测试 |
| Capability | 性能 |
| Evidence Type | 历史记录/规划 |
| File | [.engineering/gates/GATE-P9-20260922.md](../../.engineering/gates/GATE-P9-20260922.md#L7) |
| Class | `200万库存桶`（SQL/文档为定位文本） |
| Method | ReportScaleBenchmark |
| Configuration | — |
| SQL·Table | — |
| Confidence | `FACT` |

<a id="e26"></a>
## E26

| Field | Value |
|---|---|
| Finding | RPO/RTO 仍为假设，未以恢复演练验收 |
| Capability | 灾备 |
| Evidence Type | 历史记录/规划 |
| File | [.engineering/bootstrap/NFR.md](../../.engineering/bootstrap/NFR.md#L15) |
| Class | `NFR-07`（SQL/文档为定位文本） |
| Method | NFR |
| Configuration | RPO / RTO |
| SQL·Table | — |
| Confidence | `NEEDS_VERIFICATION` |

<a id="e27"></a>
## E27

| Field | Value |
|---|---|
| Finding | 采购申请只有表；不允许超收是明确业务决策 |
| Capability | 计划差异 |
| Evidence Type | 源码/配置/运行文档 |
| File | [erp-procurement/src/main/resources/db/migration/V70__procurement.sql](../../erp-procurement/src/main/resources/db/migration/V70__procurement.sql#L9) |
| Class | `CREATE TABLE pur_request`（SQL/文档为定位文本） |
| Method | DDL |
| Configuration | — |
| SQL·Table | pur_request / pur_order_line |
| Confidence | `FACT` |

<a id="e28"></a>
## E28

| Field | Value |
|---|---|
| Finding | 初始计划包含过期回收、申请、附件等能力 |
| Capability | 需求基线 |
| Evidence Type | 历史记录/规划 |
| File | [.engineering/bootstrap/CAPABILITY_MAP.md](../../.engineering/bootstrap/CAPABILITY_MAP.md#L49) |
| Class | `CAP-C03`（SQL/文档为定位文本） |
| Method | 能力目录 |
| Configuration | — |
| SQL·Table | — |
| Confidence | `FACT` |

<a id="e29"></a>
## E29

| Field | Value |
|---|---|
| Finding | 财务总账等与外部银行税务明确后置 |
| Capability | 范围 |
| Evidence Type | 历史记录/规划 |
| File | [.engineering/bootstrap/MVP_SCOPE.md](../../.engineering/bootstrap/MVP_SCOPE.md#L68) |
| Class | `总账`（SQL/文档为定位文本） |
| Method | MVP / FUTURE_SCOPE |
| Configuration | — |
| SQL·Table | — |
| Confidence | `FACT` |

<a id="e30"></a>
## E30

| Field | Value |
|---|---|
| Finding | 最新交付已关闭本地 OIDC 缺口，生产配置待目标环境 |
| Capability | 交付边界 |
| Evidence Type | 历史记录/规划 |
| File | [CODEX_PROGRESS.md](../../CODEX_PROGRESS.md#L25) |
| Class | `未完成`（SQL/文档为定位文本） |
| Method | 进度摘要 |
| Configuration | — |
| SQL·Table | — |
| Confidence | `FACT` |

<a id="e31"></a>
## E31

| Field | Value |
|---|---|
| Finding | 接口拦截仅检查方法权限注解，新增入口需完整绑定 |
| Capability | 接口授权 |
| Evidence Type | 源码/配置/运行文档 |
| File | [erp-app/src/main/java/com/lrj/erp/app/security/PermissionInterceptor.java](../../erp-app/src/main/java/com/lrj/erp/app/security/PermissionInterceptor.java#L27) |
| Class | `getMethodAnnotation`（SQL/文档为定位文本） |
| Method | preHandle |
| Configuration | /api/** |
| SQL·Table | — |
| Confidence | `FACT` |

<a id="e32"></a>
## E32

| Field | Value |
|---|---|
| Finding | 逆向链路已有实现，不应重新列为零建设 |
| Capability | 退货 |
| Evidence Type | 源码/配置/运行文档 |
| File | [erp-sales/src/main/java/com/lrj/erp/sales/application/SalesReturnService.java](../../erp-sales/src/main/java/com/lrj/erp/sales/application/SalesReturnService.java#L22) |
| Class | `class SalesReturnService`（SQL/文档为定位文本） |
| Method | create / approve / execute |
| Configuration | — |
| SQL·Table | sal_return* |
| Confidence | `FACT` |

<a id="e33"></a>
## E33

| Field | Value |
|---|---|
| Finding | 采购退货应用链路已有实现 |
| Capability | 退货 |
| Evidence Type | 源码/配置/运行文档 |
| File | [erp-procurement/src/main/java/com/lrj/erp/procurement/application/PurchaseReturnService.java](../../erp-procurement/src/main/java/com/lrj/erp/procurement/application/PurchaseReturnService.java#L22) |
| Class | `class PurchaseReturnService`（SQL/文档为定位文本） |
| Method | create / approve / execute |
| Configuration | — |
| SQL·Table | pur_return* |
| Confidence | `FACT` |

<a id="e34"></a>
## E34

| Field | Value |
|---|---|
| Finding | 有员工入职、绑定、离职服务，组织角色管理入口仍未完成 |
| Capability | IAM 管理 |
| Evidence Type | 源码/配置/运行文档 |
| File | [erp-iam/src/main/java/com/lrj/erp/iam/service/EmployeeService.java](../../erp-iam/src/main/java/com/lrj/erp/iam/service/EmployeeService.java#L12) |
| Class | `class EmployeeService`（SQL/文档为定位文本） |
| Method | enroll / bindUser / leave |
| Configuration | — |
| SQL·Table | iam_employee / iam_user |
| Confidence | `FACT` |

## 扫描与负证据边界

- 遍历 12 个模块的 `src/main`，搜索生产 Controller/Mapping，排除 `src/test` 的 FixtureController；发现 IAM、OIDC 两个 Controller。全局异常处理器不是业务入口。
- 静态目录仅 `static/auth` 三个文件；未发现前端 package.json/业务路由应用。没有以缺 package.json 单独证明“无页面”。
- 主数据生产 service 只有 SkuService/CurrencyService；结合 DDL 区分表结构与业务管理流程。
- 搜索 reservation 的 expires/expiry/expire/过期并核查 DDL/服务/调度；未找到自动到期回收。
- 搜索采购申请/报价/供应商对账及英文命名；采购申请命中 `pur_request` DDL，无申请明细/转单服务；后两者无对应业务服务。
- 检查 scripts/deploy/CI/operations 与 NFR，未找到本项目备份恢复演练。未查外部备份平台，故恢复能力不标 FACT 缺失。
- 检查依赖、模块及业务服务未发现 AI 链路；不将其当成必须修复项。

## 验证与局限

本轮未执行 Maven、浏览器 E2E、生产连接或恢复演练，也未读取本地私密 OIDC 凭据文件。历史 OIDC 测试记录 179 项；P9 另有合成规模报表基准。两者均不是本轮新运行的验收结果。

报告写入前记录了 431 个 Git 跟踪文件内容哈希。结束校验发现其中 13 个原有修改文件变化，且工作区跟踪文件全部与相同 HEAD 一致（git diff 无差异）；本轮工具未写入或回退这些文件，因此记录为外部并发变化，不声称全部哈希不变。最终本轮产物仅本目录五份报告。关键证据文件与结论经复核不受这 13 个文件变化影响。验证范围是报告完整性与证据路径，不是运行时能力验收。

validation：五文件非空；候选项涵盖四类、优先级/状态/触发条件齐全；九维度、缺口/证据映射、依赖图、Not Recommended Now 已覆盖。分析产物校验结果以最终执行输出为准。
