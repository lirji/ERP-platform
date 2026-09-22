# EVENT CATALOG

> Owner: `public-engineering-workflow:contracts` · 状态 `ACTIVE`
> 投递机制：**同库事务性 Outbox**（`erp_outbox_message`，V1 基线）。MVP 无 MQ。
> 事件一旦发布，**payload 字段语义不可更改**——历史事件长期存在于 Outbox 与审计中，改语义会让重放产生错误结果。

## 0. 信封（所有事件共用）

```json
{
  "eventId":      "uuid",
  "eventType":    "PurchaseReceiptPosted.v1",
  "tenantId":     123,
  "aggregateType":"PurchaseReceipt",
  "aggregateId":  "PR20260921000001",
  "occurredAt":   "2026-09-21T10:41:33.925+08:00",
  "traceId":      "3f9d4afa548a4641922d2849ff667cef",
  "payload":      { }
}
```

- `eventType` **自带版本后缀**（`.v1`）。破坏性变更发 `.v2`，两者在兼容窗口内并存。
- `traceId` 贯穿：从触发事件的 HTTP 请求，到 Outbox 投递，到消费方处理，可串成一条链。
- `occurredAt` 是**业务发生时刻**，不是投递时刻；投递时刻在 `erp_outbox_message.published_at`。

## 1. 消费规则（强制）

1. **消费方幂等**：同一 `eventId` 处理多次只能有一次效果。不依赖投递方只发一次。
2. **未知字段忽略**：消费方遇到 payload 中不认识的字段必须忽略，不得报错——否则生产方无法做加法变更。
3. **未知枚举降级**：见 `CONTRACTS.md` §1.2。
4. **乱序容忍**：Outbox 按 `created_at` 顺序投递，但**不保证**跨聚合的全局顺序。
   依赖顺序的消费方必须用聚合内的版本或状态自行判断，不得假定全局有序。
5. **失败重试有界**：`retry_count` 达上限后置 `DEAD`，进入死信人工处理，不无限重试。

---

## 2. P1 平台内核事件

| 事件 | 生产者 | 消费者 | payload 关键字段 |
|---|---|---|---|
| `ApprovalCompleted.v1` | `erp-approval` | `erp-inventory` `erp-procurement` `erp-sales` | `businessType` `businessId` `result(APPROVED)` `approvedBy` `approvedAt` |
| `ApprovalRejected.v1` | `erp-approval` | 同上 | `businessType` `businessId` `reason` `rejectedBy` |
| `UserDisabled.v1` | `erp-iam` | `erp-approval`（转交待办） | `userId` `disabledAt` |
| `RoleChanged.v1` | `erp-iam` | 权限缓存失效 | `roleId` `changeType` |

### `ApprovalCompleted.v1`

```json
{
  "businessType": "PURCHASE_ORDER",
  "businessId":   "PO20260921000001",
  "instanceId":   "APR20260921000007",
  "result":       "APPROVED",
  "approvedBy":   4501,
  "approvedAt":   "2026-09-21T11:02:10.001+08:00"
}
```

> **为什么审批用事件而不是回调**：业务模块通过 `businessType` + `businessId` 接入审批，
> 审批中心不认识任何具体业务模块——这是 `erp-approval` 不依赖业务模块的前提（`MODULE_SERVICE_MAP` §2）。
> 若改成审批直接调用业务模块，依赖方向立刻反转，ArchUnit 会拦截。

---

## 3. 下游事件（声明契约，由各自 Phase 实现）

在此**提前声明**是为了让 P1 的 Outbox 框架一次做对；实现在各自 Phase。

| 事件 | 生产者 | 消费者 | Phase |
|---|---|---|---|
| `InventoryInboundCompleted.v1` | `erp-inventory` | `erp-document` `erp-reporting` | P3 |
| `InventoryOutboundCompleted.v1` | `erp-inventory` | `erp-document` `erp-reporting` | P3 |
| `InventoryReserved.v1` / `InventoryReleased.v1` | `erp-inventory` | `erp-sales` | P3 |
| `PurchaseOrderApproved.v1` | `erp-procurement` | `erp-document` | P4 |
| `PurchaseReceiptPosted.v1` | `erp-procurement` | **`erp-finance`**（生成应付） `erp-document` | P4→P6 |
| `ShipmentPosted.v1` | `erp-sales` | **`erp-finance`**（生成应收） `erp-document` | P5→P6 |
| `PayableCreated.v1` / `ReceivableCreated.v1` | `erp-finance` | `erp-document` `erp-reporting` | P6 |
| `PaymentCompleted.v1` / `ReceiptCompleted.v1` | `erp-finance` | `erp-document` `erp-reporting` | P6 |
| `SettlementApplied.v1` / `SettlementReversed.v1` | `erp-finance` | `erp-reporting` | P6 |

> **`PurchaseReceiptPosted` → `erp-finance` 的幂等约定**：
> 消费方以 `(tenant_id, source_doc_type='PURCHASE_RECEIPT', source_doc_id)` 建唯一索引兜底（INV-06）。
> 同一入库单事件投递 10 次只能生成一张应付——这是 P6 出口条件 ③ 的断言对象。

---

## 4. AuditRecord（不是事件，是审计投影）

`erp-document` 在写入路径上拦截产生，**不经过 Outbox**（审计必须与业务同事务，异步会丢）。

| 字段 | 说明 |
|---|---|
| `tenantId` `userId` | 谁 |
| `occurredAt` | 什么时候 |
| `businessType` `businessId` `documentNo` | 操作了什么 |
| `action` | 动作（CREATE/UPDATE/SUBMIT/APPROVE/CANCEL/...） |
| `beforeValue` `afterValue` | 修改前 / 修改后（`JSONB`） |
| `sourceIp` `userAgent` | 来源 IP / 终端 |
| `traceId` | 关联同一次请求的全部日志 |

`beforeValue` / `afterValue` 中的敏感字段（价格授权、银行账号、证件号）按 `SECURITY_ARCHITECTURE` 的脱敏规则处理后再落库。

## 5. P6 实施后的 v2 财务事实

P4/P5 实际发布的 v1 只有单据关系字段，不能据此计算金额。保留原有 v1 重放语义，财务生成改由以下完整 v2 驱动（详见 ADR-005）：

| 事件 | 生产者 | 消费者 | 幂等键 |
|---|---|---|---|
| `PurchaseReceiptPosted.v2` | procurement | finance 生成 AP、document 关系图 | 租户 + PURCHASE_RECEIPT + 来源 ID |
| `ShipmentPosted.v2` | sales | finance 生成 AR、document 关系图 | 租户 + SALES_SHIPMENT + 来源 ID |

载荷对应 `kernel.events.PostedDocument`：

- `parentType/parentId/parentNo`：源头订单；`childType/childId/childNo`：本次收发单；
- `companyId/partnerId`：主体与供应商/客户；
- `amount/currency`：本批金额及来源事务读取的启用本位币；
- `orgPath/operatorId`：组织范围与来源操作人。

不从子模块数据库补查财务字段，不为历史 v1 猜测金额；存量 v1 补账需要来源侧另行回填完整 v2。
`ReceivableCreated.v1 / PayableCreated.v1 / ReceiptCompleted.v1 / PaymentCompleted.v1` 带关系字段，支持继续沿图追溯。
`SettlementApplied.v1 / SettlementReversed.v1` 带 `billType/billId/cashId/amount/currency/partnerId/orderId/settlementId`；反核销 amount 为负数。
财务与关系图消费均独立事务，失败必须抛出以重试；发布标记失败后的重复投递由数据库约束防重。
