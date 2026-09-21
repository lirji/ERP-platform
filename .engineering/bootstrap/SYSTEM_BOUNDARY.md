# ERP System Boundary

> Owner: `project-bootstrap` · Phase 1
> 每个能力/外部系统**归入且只归入一类**。每个 `IN_SCOPE` 必须追溯到一个能力 ID 和一段业务链路。

## 1. IN_SCOPE（本系统自己做）

| 能力 | 追溯链路 | 阶段 |
|---|---|---|
| CAP-P01..P11 平台内核（组织/权限/主数据/编号/单据模型/状态机/单据图/审计/审批端口/配置） | BL-5, BL-4, 横跨 BL-1..BL-3 | Phase 1–2 |
| CAP-C01..C04 库存台账·流水·预占·批次 | BL-1, BL-2, BL-3 | Phase 3 |
| CAP-C05 采购订单履约 | BL-1 | Phase 4 |
| CAP-C06 销售订单履约 | BL-2 | Phase 5 |
| CAP-C07..C09 应付·应收·核销 | BL-1, BL-2 | Phase 6 |
| CAP-S01..S05, S09 退货·调拨·盘点·调整·采购申请 | BL-1, BL-2, BL-3 | Phase 7 |
| CAP-S06 信用额度 | BL-2 | Phase 5 |
| CAP-S10 移动加权平均计价 | BL-1, BL-3 | Phase 7 |
| CAP-A01..A05 报表读模型 | BL-6 | Phase 9 |
| CAP-G04 导入导出 | BL-4 | Phase 2 |

## 2. OUT_OF_SCOPE（明确不做）

| 项 | 原因 |
|---|---|
| 仓储作业执行（波次、拣货路径、上架推荐、PDA/设备、序列号逐件追踪） | 属于 WMS 领域。`wms-platform` 已实现（`wms-platform/docs/design/02-domain.md`：桶维度含 `location_id`/`quality_code`、序列号唯一域、跨仓 Seata TCC）。ERP 重复实现＝重复投入且两套库存权威 |
| 总账凭证、会计科目余额、会计期间、财务报表体系 | 提示词第八章明确「第一阶段不要求完整专业财务软件」；架构预留见 `FUTURE_SCOPE` |
| 生产制造（MES/BOM/工单）、CRM 商机、HR 薪酬、固定资产 | 不在给定业务链路上，无能力追溯 |
| 自建身份提供方（用户名密码、验证码、SSO 协议实现） | `auth-platform` 已提供（Casdoor + OIDC） |
| 自建 BPMN 流程引擎 | `workflow-platform` 已提供 Flowable 7.1.0；ERP 只做审批端口与内置顺序审批 |
| 跨境多语言、多法域税务合规 | 未在需求中出现 |

## 3. FUTURE_SCOPE（以后可能做，附触发条件）

| 项 | 触发条件 |
|---|---|
| 总账 / 凭证 / 会计期间 / 财务报表 | 财务部门要求 ERP 出具法定账簿，或需要与外部财务软件做凭证级对接 |
| 多币种与汇率折算 | 出现第二个结算币种的真实交易 |
| FIFO / 个别计价 | 审计或行业要求特定计价方法（可从不可变流水重算，见 `Q-05`） |
| CAP-I03 WMS 对接 | 单仓日作业量超过人工登记能力，或引入 PDA/自动化设备 |
| CAP-I04 电商渠道接入 | 出现线上销售渠道 |
| CAP-I05 银行回单 / CAP-I06 电子发票 | 收付款量使人工录入成为瓶颈；或税务合规要求 |
| 服务化拆分（从模块化单体抽出进程） | 见 `architecture/ARCHITECTURE_EVOLUTION.md` 的触发条件 |
| 搜索引擎（ES）、消息队列（Kafka）、二级缓存（Redis） | 见 `TECH_SELECTION.md` 各自的引入触发条件；当前均**不引入** |

## 4. EXTERNAL_DEPENDENCY（依赖外部系统）

| 系统 | 方向 | 方式 | 失败影响 | 降级 | 证据 |
|---|---|---|---|---|---|
| `auth-platform` / Casdoor | ERP → 外部（认证） | OIDC 授权码 + `auth-platform-sdk` | 无法登录；**已登录会话不受影响**（ERP 侧本地校验 token） | 保留本地应急管理员账号（仅限超管，全程审计） | `auth-platform/README.md`、`auth-platform-sdk/pom.xml` |
| `dev-infra` PostgreSQL 16 | ERP → 外部（存储） | JDBC | 系统不可用 | 无（权威数据库） | `dev-infra/docs/service-catalog.md`、`dev-infra/compose.yaml:35` |
| `dev-infra` MinIO | ERP → 外部（附件） | S3 API | 附件不可用，**单据主流程不受影响** | 附件上传降级为不可用提示 | `dev-infra/docs/service-catalog.md` |
| `workflow-platform` | ERP ↔ 外部（审批，Phase 8） | SDK(REST) + Kafka 契约 | 可配置审批不可用 | **回落到内置顺序审批适配器** | `workflow-platform/README.md`（server `:8300`、`workflow-platform-sdk`） |
| `wms-platform` | ERP ↔ 外部（未来） | 端口预留 | — | — | `wms-platform/README.md` |

## 5. 与 wms-platform 的边界（本规划最关键的一条边界）

提示词第七章要求 ERP 库存「不要只设计 sku_id + stock」，并列出 `WarehouseLocation`。而 `wms-platform` 已经实现了更深的库位级执行模型。两者必须划清，否则会出现**两个库存权威**。

| 关注点 | ERP（本系统） | WMS（`wms-platform`） |
|---|---|---|
| 回答的问题 | 「我**有多少、值多少、能卖多少**」 | 「货**在哪、怎么拿、谁动的**」 |
| 权威数据 | 库存账（数量口径 + 成本 + 可承诺量）、单据链、应收应付 | 库位库存、作业任务、序列号、质量状态 |
| 台账维度 | `tenant+company+warehouse+sku+batch`（`location` 为**可选维度**，默认哨兵 `NO_LOCATION`） | `enterprise+warehouse+owner+location+sku+lot+quality` |
| 一致性手段 | 单库本地事务（模块化单体内） | 跨仓 Seata TCC |
| 是否 MVP 自建 | **是**（ERP 必须有自己的库存账，否则无法生成 AR/AP 与成本） | 不适用 |

**决策**：MVP 阶段 ERP 自建库存账，`location` 作为可选维度预留（启用不改主键）；CAP-I03 留一个 `InventoryExecutionPort`，未来某仓库标记为「WMS 托管」时，该仓的物理执行委托出去、ERP 只接收过账回执。
**未决**：见 `Q-03`。
**风险**：见 `RISK_REGISTER.md` R-12（能力重叠导致重复实现）。
