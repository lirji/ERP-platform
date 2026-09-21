# Integration Architecture

> Owner: `backend-architecture-design`
> 统一边界：`Business Domain → Integration Port → Adapter → External System`。领域层只依赖 Port；第三方 SDK、报文、协议只出现在 Adapter。

## 1. 端口清单

| Port | 定义方 | 用途 | MVP 适配器 | 未来适配器 |
|---|---|---|---|---|
| `IdentityPort` | `erp-iam` | 校验令牌、取 subject 与 claims | `CasdoorOidcAdapter` | 企业 IdP 联邦（由 Casdoor 承担） |
| `ApprovalPort` | `erp-approval` | 提交/审批/驳回/撤回/转交/加签/抄送 | `BuiltInSequentialApprovalAdapter` | `WorkflowPlatformApprovalAdapter`（Phase 8） |
| `FileStoragePort` | `erp-kernel` | 附件上传/下载/签名 URL | `MinioAdapter`（Phase 2） | 云对象存储 |
| `NotificationPort` | `erp-kernel` | 待办与异常通知 | `NoopAdapter`（MVP） | 邮件 / 企业 IM |
| `InventoryExecutionPort` | `erp-inventory` | 仓库物理执行委托 | `InternalExecutionAdapter`（ERP 自管，MVP） | `WmsPlatformAdapter`（见 `Q-03`） |

> 端口在 MVP 就存在，适配器可以是最简实现。这不是过度设计：端口是**未来抽进程的边界**（`ARCHITECTURE_EVOLUTION.md` Stage 3），且 `NoopAdapter` 的成本接近零。

## 2. 外部依赖明细

| 外部系统 | 方向 | 方式 | 超时 | 重试 | 幂等键 | 结果未知时 | 对账 | 失败影响 | 降级 |
|---|---|---|---|---|---|---|---|---|---|
| Casdoor（`auth-platform`） | ERP → 外 | OIDC 授权码 + PKCE；JWT 本地验签 | 连接 3s / 读 5s | 不重试（用户重试登录） | — | 登录失败 | — | 新登录不可用；**已登录会话不受影响** | 本地应急超管账号（全程审计） |
| PostgreSQL 16（`dev-infra`） | ERP → 外 | JDBC，连接池上限显式配置 | 连接 3s / 语句 30s（报表 60s） | 不重试写操作 | — | 事务回滚 | — | 系统不可用 | 无 |
| MinIO（`dev-infra`） | ERP → 外 | S3 API | 10s | 3 次退避 | 对象 key | 标记附件待确认，后台核对 | 定期比对附件记录与对象存在性 | 附件不可用，**单据主流程不受影响** | 提示附件暂不可用 |
| `workflow-platform`（Phase 8） | ERP ↔ 外 | SDK(REST) 查/办 + Kafka 发起/回执 | REST 5s | Outbox 退避重试 + DLQ | `(businessType, businessId, commandId)` | **不得当失败**；置 `PENDING` 并由对账收敛 | 比对 ERP 待审实例 vs 平台实例 | 可配置审批不可用 | **回落内置顺序审批** |
| `wms-platform`（未来） | ERP ↔ 外 | 待定 | — | — | 过账 `operation_id` | — | 库存数量对账 | — | ERP 自管 |

## 3. 防腐层的具体职责

| ACL | 隔离什么 |
|---|---|
| `CasdoorOidcAdapter` | Casdoor 的 `organization` / `group` / `role` 概念**不得**映射成 ERP 的组织与角色。只取 `sub`、`email`、`preferred_username`，其余在 ERP 内解析 |
| `WorkflowPlatformApprovalAdapter` | Flowable 的 `processInstanceId` / `taskId` / 流程变量不得进入 ERP 的 `ApprovalInstance`；适配器维护 ERP ID ↔ 引擎 ID 的映射表 |
| `WmsPlatformAdapter` | WMS 的桶维度（`owner_id` / `quality_code` / `serial`）与 ERP 台账维度不同；适配器负责聚合到 ERP 维度，**不把 WMS 模型引进来** |

## 4. 对外开放接口

MVP **不**对外提供开放 API（无外部消费方）。
未来（CAP-I04 电商接入）触发时，按 Open Host Service + Published Language 设计：版本化路径、显式错误码、幂等键、字段语义文档、兼容窗口。**不预先建设**（提示词第三十章第 15 条）。
