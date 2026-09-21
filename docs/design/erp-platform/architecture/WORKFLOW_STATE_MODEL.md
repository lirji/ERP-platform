# Workflow & State Model

> Owner: `backend-architecture-design`
> 回答提示词第十章「需要分析哪些领域适合统一状态机」与第十一章「审批中心」。

## 1. 先分类，不要一刀切

| 类型 | 适用 | 本项目用在哪 |
|---|---|---|
| Simple Status | 状态少、迁移简单、无并发冲突 | 主数据启用/停用；报价单 |
| **State Machine** | 受约束的生命周期：合法迁移、前置条件、副作用、并发控制 | **采购订单、销售订单、收货单、出库单、退货单、调拨单、盘点单、应收/应付单** |
| Workflow | 多步骤、跨角色、可能长时间运行 | 盘点（封存→点数→复核→审批→调整） |
| Approval Flow | 可配置审批人、会签/或签、驳回 | 采购申请、采购订单、销售订单、库存调整、盘点差异、超额放行 |
| Event-Driven Process | 由多个上下文事件推进 | 过账 → 往来账 → 核销 |

**结论**：统一的是**机制**（迁移表 + 守卫 + 动作 + 日志 + 并发控制），**不统一状态集合**。不存在一张「万能状态表」——那正是提示词第三十章第 12/7 条要防的东西。

## 2. 统一状态机机制（`erp-kernel` 提供）

每条迁移声明五元组：

```text
CurrentState × Event → Guard → Action → NextState
```

机制必须提供：

| 能力 | 实现 |
|---|---|
| 非法迁移拒绝 | 迁移表中不存在的 `(state, event)` 抛 `IllegalTransitionException`，返回统一错误码 |
| 状态幂等 | 同一 `(businessId, event, requestId)` 重复触发返回首次结果，不产生第二次副作用 |
| 并发控制 | 迁移必须带 `version` 乐观锁；**更新影响 0 行视为冲突，不得当成功** |
| 迁移日志 | `doc_state_transition_log`：谁、何时、从什么态到什么态、触发事件、请求 ID、结果 |
| 状态恢复 | 日志可重放出当前状态，用于排查与对账 |
| 补偿 | 已提交的效果用**新单据**补偿（退货单、反核销单、调整单），不做"状态回滚" |

**禁止**：`if (status == 1)` 散落在服务里做业务控制（提示词第十章、第三十章第 7 条）。状态判断只能经状态机入口。

## 3. 各聚合的状态集合（各自拥有，互不复用）

### 3.1 采购订单 `PurchaseOrder`

```text
DRAFT ──submit──► SUBMITTED ──startApproval──► APPROVING ──approve──► APPROVED
  ▲                   │                            │
  └──withdraw─────────┘                            └──reject──► REJECTED ──edit──► DRAFT
APPROVED ──firstReceipt──► PARTIALLY_RECEIVED ──receiveRemaining──► FULLY_RECEIVED
APPROVED / PARTIALLY_RECEIVED ──close──► CLOSED        （关闭剩余量，已收部分保留）
DRAFT / SUBMITTED / APPROVED(未收货) ──cancel──► CANCELLED
```

守卫举例：
- `receive`：`已收累计 + 本次 ≤ 订单量 × (1 + 超收比例)`；超收比例来自 CAP-P11 配置；超出需超收审批通过。
- `cancel`：`已收累计 == 0`。**已执行部分不可取消，只能 `close`** —— 这是 ERP 的关键业务规则。
- `close`：剩余量置为 0 并记录原因，不删除行。

### 3.2 销售订单 `SalesOrder`

```text
DRAFT → SUBMITTED → APPROVING → APPROVED → RESERVED → PARTIALLY_SHIPPED → FULLY_SHIPPED → SIGNED
                                    │                                          │
                                    └──cancel(释放预占)──► CANCELLED           └──close──► CLOSED
```

守卫：`approve` 前置 = 信用校验通过（或超限审批通过）；`cancel` 必须**释放全部未消耗预占**，释放失败则迁移失败（同事务）。

### 3.3 收货单 / 出库单（执行类单据）

```text
CREATED → CONFIRMED → POSTED        （POSTED 是终态，已过账不可改）
CREATED → CANCELLED                  （未过账才可取消）
```

**已过账单据永不回到未过账状态**；错误用红字单据（退货/调整）冲销。

### 3.4 应收 / 应付单

```text
OPEN → PARTIALLY_SETTLED → SETTLED
OPEN / PARTIALLY_SETTLED → WRITTEN_OFF   （坏账核销，需审批）
```

守卫：`已核销 ≤ 金额`；`SETTLED` 后反核销回到 `PARTIALLY_SETTLED` 或 `OPEN`，并生成反向核销记录。

### 3.5 盘点单（Workflow 型）

```text
DRAFT → SCOPE_FROZEN → COUNTING → COUNTED → REVIEWING → APPROVING → ADJUSTED
                                                              └──reject──► COUNTING
```

守卫：差异必须经审批才生成调整单并落台账；盘点期间范围内的变动需冻结或记账补偿。

## 4. 三种状态必须解耦

| 状态 | 归谁 | 规则 |
|---|---|---|
| **Business Status** | 所属聚合（`PurchaseOrder.status`） | **唯一权威** |
| **Approval Status** | `erp-approval` 的 `ApprovalInstance` | 审批自己的生命周期 |
| **Workflow/Engine Status** | 外部引擎（Phase 8 的 Flowable 实例） | **绝不成为业务状态的权威来源** |

映射方式：审批完成 → 发 `ApprovalCompleted(businessType, businessId, decision, decisionId)` → 业务聚合**自己**执行 `approve` / `reject` 迁移（带 `decisionId` 幂等）。业务模块**不**读审批表来判断自己的状态。

## 5. 审批中心设计（CAP-P10）

### 5.1 ERP 侧模型（与引擎无关）

```text
ApprovalDefinition   审批定义：businessType + 条件 + 节点序列
ApprovalInstance     审批实例：businessType + businessId + 状态
ApprovalNode         节点：审批人解析规则（指定人 / 角色 / 部门主管 / 上级）
ApprovalTask         待办任务
ApprovalAction       动作：提交 · 审批 · 驳回 · 撤回 · 转交 · 加签 · 抄送
ApprovalRecord       不可变审批记录
```

业务模块**只**通过 `(businessType, businessId)` 接入，不感知引擎。

### 5.2 引擎可插拔（Port + Adapter）

| 适配器 | 阶段 | 能力 | 何时切换 |
|---|---|---|---|
| `BuiltInSequentialApprovalAdapter` | **MVP** | 顺序多级审批、驳回、撤回、抄送；审批人按「指定人/角色/部门主管/上级」解析 | 默认 |
| `WorkflowPlatformApprovalAdapter` | Phase 8 | 会签、或签、加签、转交、并行分支、可视化设计器 | 出现真实的会签/并行/复杂条件需求时 |

切换 `WorkflowPlatformApprovalAdapter` 的代价必须提前知道（来自 `workflow-platform/README.md` 的既有契约）：
- 接入方式：`workflow-platform-sdk`（REST 查/办）+ Kafka（`workflow.command.start.v1` 发起、`workflow.action.applied.v1` 回执）；
- 办理返回 **202 `PENDING_BUSINESS`**，是"已受理"不是"已完成"，UI 不得呈现为已完成；
- 需要 ERP 侧实现 outbox + inbox + DLQ；
- 因此它引入 **Kafka 依赖 + 跨系统最终一致**——这正是 MVP 不启用的理由（A-12、提示词第十一章「不要在缺乏必要性的情况下过度引入复杂 BPM 平台」）。

### 5.3 审批人计算归 ERP

审批人解析（部门主管、上级、指定角色）**在 ERP 侧算好**再传给引擎，不让流程引擎反查 ERP 的组织表。理由：组织模型是 ERP 的数据所有权，反查会形成跨系统的隐式耦合。

## 6. 核心流程的完整要素（示例：采购收货）

| 要素 | 内容 |
|---|---|
| Actor | 仓管员（执行）、采购员（跟踪） |
| Trigger | 供应商到货，仓管员在 ERP 创建收货单并确认 |
| State | `PurchaseOrder`: APPROVED → PARTIALLY_RECEIVED / FULLY_RECEIVED；`PurchaseReceipt`: CREATED → CONFIRMED → POSTED |
| Transition | 见 3.1、3.3；守卫为超收比例与订单状态 |
| Decision | 超收 → 是否允许（CAP-P11 配置）→ 需要则触发超收审批；少收 → 保留剩余或关闭 |
| Exception | 业务异常（超收超限、订单已关闭）→ 领域异常 + 业务错误码；系统异常（DB 失败）→ 事务回滚 + 系统错误码，**两类分开，边界统一转换** |
| Compensation | 已过账的收货用**采购退货单**冲销，不改原单 |
| Timeout | 无外部调用，无超时点；Outbox 投递有重试上限与 DLQ |
| External Interaction | MVP 无；Phase 8 审批走 workflow-platform 时有幂等键与对账 |
| Side Effect | 库存过账（同事务）、应付生成（Outbox）、单据关系登记（Outbox）、审计（Outbox） |
