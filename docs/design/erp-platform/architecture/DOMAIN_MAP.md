# Domain Map

> Owner: `backend-architecture-design` · 委托方 `project-bootstrap`

```text
Business Domain: 企业经营资源管理（Enterprise Resource Management）
  ├── Core Subdomain
  │     ├── 库存账（Inventory Accounting）
  │     ├── 采购履约（Procurement Execution）
  │     ├── 销售履约（Sales Fulfillment）
  │     └── 往来账与结算（Receivables & Payables / Settlement）
  ├── Supporting Subdomain
  │     ├── 主数据（Master Data）
  │     ├── 审批（Approval）
  │     ├── 单据链与审计（Document Lineage & Audit）
  │     └── 报表（Reporting）
  └── Generic Subdomain
        ├── 身份与访问（Identity & Access）
        ├── 编号（Numbering）
        └── 文件 / 通知（File / Notification）
```

## 1. 子域分类依据

| 子域 | 分类 | 依据 |
|---|---|---|
| 库存账 | **Core** | 差异化价值所在（账实一致 + 可追溯 + 可承诺量）；规则最复杂（多维度桶、五种数量口径、并发扣减、守恒对账）；需要持续投入 |
| 采购履约 | **Core** | 部分/多次收货、超收少收、取消与关闭的累计量规则是 ERP 的核心难点，不是通用解法 |
| 销售履约 | **Core** | 预占—拣货—发货的数量链与信用控制构成差异化 |
| 往来账与结算 | **Core** | 核销（不超额、不重复、可反核销）是内控关键，且与库存过账强耦合 |
| 主数据 | Supporting | 业务必需，但规则以「唯一性 + 生命周期 + 引用后受限修改」为主，不构成差异化 |
| 审批 | Supporting | 业务必需；引擎本身是通用解法（已有 `workflow-platform`），ERP 侧只保留接入模型 |
| 单据链与审计 | Supporting | 必需的内控能力，模式通用 |
| 报表 | Supporting | 读侧投影，价值在口径一致而非算法 |
| 身份与访问 | **Generic** | 成熟解法遍地；认证已由 `auth-platform` 提供 → `REUSE_EXISTING` |
| 编号 | Generic | 通用解法（号段/序列） |
| 文件 / 通知 | Generic | 通用解法 → MinIO / 外部通知 |

> 注意：这里的 Core/Supporting/Generic 是**子域分类**，与 `CAPABILITY_MAP.md` 的六类能力分类（Core/Supporting/Generic/Platform/Integration/Analytics）是两套不同的坐标，不可互相代替。例如「主数据」作为**能力**属于 Platform（被多域复用），作为**子域**属于 Supporting（业务必需但不差异化）。

## 2. 聚合设计（只为 Core 与规则复杂的上下文设计）

### 2.1 Inventory Context（Core）

| 聚合根 | 保护的不变量 | 内部实体 / 值对象 |
|---|---|---|
| `InventoryBalance`（库存桶） | INV-02 `onHand ≥ 0`、`reserved + locked ≤ onHand`；桶键唯一 | VO `StockBucketKey(tenantId, companyId, warehouseId, locationId, skuId, batchId)`、VO `Quantity(value: BigDecimal, uom)` |
| `InventoryReservation`（预占） | `consumed + released ≤ reservedQty`；按 `(来源单据类型, 来源单据行ID)` 幂等 | Entity `ReservationLine` |
| `StockDocument`（调拨/盘点/调整单） | 状态合法迁移；差异必须经审批才落账；调拨在途量守恒（INV-08） | Entity `StockDocumentLine`、VO `ReasonCode` |
| `InventoryBatch`（批次档案） | 批次号在 `tenant+sku` 内唯一；`[producedAt, expiresAt)` 半开区间 | — |
| `Warehouse`（仓库结构） | 库区/库位编码在仓内唯一；停用的库位不可被新流水引用 | Entity `WarehouseArea`、`WarehouseLocation` |

`InventoryTransaction`（库存流水）**不是独立聚合**：它是 `InventoryBalance` 写操作的不可变副产物，与余额在**同一本地事务**内追加，否则 INV-01 无法保证。它有独立的查询模型，但没有独立的写入口。

领域服务：
- `StockPostingService` — 把一条「过账请求（来源单据行 + 方向 + 数量 + 批次）」翻译成余额变更 + 流水追加，持有 INV-01/02/03/04。
- `AvailabilityCalculator` — `available` 是**资格过滤后的计算值**（扣除 reserved/locked，排除冻结、质量不合格、效期不满足），**不是可写字段**。
- `CostingService` — 移动加权平均（A-09）。

领域事件：`InventoryInboundCompleted` · `InventoryOutboundCompleted` · `InventoryReserved` · `InventoryReleased` · `StockAdjusted` · `TransferInTransit` · `TransferReceived`

### 2.2 Procurement Context（Core）

| 聚合根 | 不变量 | 说明 |
|---|---|---|
| `PurchaseRequest` | 无库存与金额效果；转单后不可重复转 | |
| `PurchaseOrder` | INV-05 累计收货量 ≤ 订单量 ×(1+超收比例)；已执行部分不可取消，只能**关闭剩余** | Entity `PurchaseOrderLine` 持有 `orderedQty / receivedQty / returnedQty / closedQty` |
| `PurchaseReceipt` | 单次收货量 ≤ 订单行剩余可收量（受超收策略约束）；过账幂等 | |
| `PurchaseReturn` | 退货量 ≤ 已入库未退量 | |

领域服务：`ReceiptTolerancePolicy`（超收/少收判定，读 CAP-P11 配置）。
领域事件：`PurchaseOrderApproved` · `PurchaseReceiptPosted` · `PurchaseReturnPosted` · `PurchaseOrderClosed`

### 2.3 Sales Context（Core）

| 聚合根 | 不变量 | 说明 |
|---|---|---|
| `Quotation` | 不占库存；转单时重新校验价格与信用 | |
| `SalesOrder` | 累计发货量 ≤ 订单量；取消必须释放预占 | Entity `SalesOrderLine` 持有 `orderedQty / reservedQty / shippedQty / returnedQty` |
| `Shipment` | 发货量 ≤ 预占未消耗量 | |
| `SalesReturn` | 退货量 ≤ 已发货未退量；退货入库**不沿用原发货时的批次结论**，需重新指定 | |
| `CustomerCreditAccount` | `占用 = 未核销应收 + 未出库订单金额 ≤ 信用额度`（超限需审批放行） | **额度（limit）属于主数据 Partner；占用（used）属于 Sales**，两者不可混在一个聚合 |

领域事件：`SalesOrderApproved` · `InventoryReservedForOrder` · `ShipmentPosted` · `SalesReturnPosted` · `SalesOrderClosed`

### 2.4 Receivables & Payables Context（Core）

| 聚合根 | 不变量 |
|---|---|
| `AccountReceivable` | INV-06 `(sourceDocType, sourceDocId)` 唯一；INV-07 `writtenOffAmount ≤ amount` |
| `AccountPayable` | 同上镜像 |
| `Receipt`（收款单） | 累计核销 ≤ 收款金额 |
| `Payment`（付款单） | 累计核销 ≤ 付款金额；累计付款 ≤ 应付金额 |
| `SettlementRecord`（核销） | 一条核销记录同时扣减往来单与收付款单；同一对 `(往来单ID, 收付款单ID)` 不重复；反核销生成**反向记录**而不是删除 |

领域服务：`SettlementService` — 应收与应付**共用同一套核销算法**。
值对象：`Money(amount: BigDecimal, currency, scale, rounding)` — 金额必须带币种与舍入规则（全局开发规范 §5）。
领域事件：`ReceivableCreated` · `PayableCreated` · `PaymentCompleted` · `ReceiptCompleted` · `SettlementApplied` · `SettlementReversed`

## 3. 不使用战术模式的上下文（明确写出理由）

| 上下文 | 决定 | 理由 |
|---|---|---|
| Identity & Access | **不做完整聚合建模**，采用分层 + 事务脚本 | 规则以结构约束（树完整性、唯一性）为主，数据库约束 + 少量服务即可；强行建 `User` 聚合只会把 CRUD 包一层。保留两个领域概念：领域服务 `DataScopeResolver`、值对象 `AccessContext(tenantId, companyId, userId, orgPath, dataScope, permissionCodes)` |
| Master Data | **轻量聚合**：`Product`（SPU 为根、SKU 为实体）、`Supplier`、`Customer` 各自为根；财务基础数据（Currency/TaxRate/SettlementMethod/BankAccount/AccountingSubject）用 CRUD | 只有「编码唯一 + 引用后受限修改 + 版本」三条不变量，不值得完整战术模式 |
| Document & Audit | 无聚合 | 追加型记录，写入即终态 |
| Reporting | 无聚合 | 只读投影 |
| Numbering | 无聚合 | 单一不变量（唯一且连续可读），由数据库序列/号段保证 |

> 反过度抽象红线：**不给每个上下文都画聚合**；上表五项明确声明不用战术模式，防止「为 DDD 而 DDD」（提示词第三十章第 15 条）。
