# Module Map

> Owner: `backend-architecture-design`
> 提示词第十六章给了一个候选目录结构并要求「禁止直接采用」。本文件是 Domain Boundary Analysis 之后的结论，与那份候选**有五处实质差异**（见第 4 节）。
> **Service Map 暂不产出**：部署边界未被证明（见 `ARCHITECTURE_OPTIONS.md` DEC-01），当前只有一个可运行单元。

## 1. 模块清单

每行都能回指到 `CAPABILITY_MAP.md` 的能力 ID 与 `BOUNDED_CONTEXT_MAP.md` 的上下文。

| 模块 | 上下文 | 职责 | 能力 | 核心聚合 | 拥有的表前缀 | 入向依赖 | 出向依赖 | 发布事件 | 消费事件 | API 边界 |
|---|---|---|---|---|---|---|---|---|---|---|
| `erp-kernel` | —（共享内核） | 单据基类、状态机机制、幂等框架、`Money`/`Quantity`、错误码、`AccessContext`、Outbox 框架 | CAP-P07 机制部分 | 无 | 无 | 全部模块 | 无 | 无 | 无 | 内部库 |
| `erp-numbering` | —（平台件） | 编号中心 | CAP-P06 | 无 | `num_` | 全部业务模块 | kernel | 无 | 无 | 内部服务接口 |
| `erp-iam` | BC-1 | 租户/公司/组织/部门/员工/用户/角色/权限/数据范围 | CAP-P01..P04 | OrgUnit 树 | `iam_` | 全部 | kernel | `UserDisabled` `RoleChanged` | 无 | 判权 API、`AccessContext` |
| `erp-masterdata` | BC-2 | 商品、往来单位、财务基础数据 | CAP-P05 | Product/Supplier/Customer | `md_` | 业务模块 | kernel, iam, numbering | `MasterDataDisabled` | 无 | 查询 + `MasterDataRef` 签发 |
| `erp-inventory` | BC-3 | 仓库结构、台账、流水、预占、批次、调拨/盘点/调整 | CAP-C01..C04, S03..S05, S10 | InventoryBalance / Reservation / StockDocument | `inv_` | procurement, sales | kernel, iam, masterdata, numbering, approval | `InventoryInboundCompleted` `InventoryOutboundCompleted` `InventoryReserved` `InventoryReleased` `StockAdjusted` `TransferInTransit` | `ApprovalCompleted` | 过账、预占、可用量、余额/流水查询 |
| `erp-procurement` | BC-4 | 采购申请、采购订单、收货、入库、退货 | CAP-C05, S01, S09 | PurchaseOrder / Receipt / Return | `pur_` | app | kernel, iam, masterdata, numbering, inventory, approval | `PurchaseOrderApproved` `PurchaseReceiptPosted` `PurchaseReturnPosted` `PurchaseOrderClosed` | `ApprovalCompleted` | 采购单 REST |
| `erp-sales` | BC-5 | 报价、销售订单、预占、出库、发货、退货、信用占用 | CAP-C06, S02, S06, S08 | SalesOrder / Shipment / CreditAccount | `sal_` | app | kernel, iam, masterdata, numbering, inventory, approval | `SalesOrderApproved` `ShipmentPosted` `SalesReturnPosted` | `ApprovalCompleted` `ReceivableCreated` `SettlementApplied` | 销售单 REST |
| `erp-finance` | BC-6 | 应收、应付、收款、付款、核销、对账 | CAP-C07..C09, S07 | AR / AP / Receipt / Payment / Settlement | `fin_` | app | kernel, iam, masterdata, numbering, approval | `ReceivableCreated` `PayableCreated` `PaymentCompleted` `ReceiptCompleted` `SettlementApplied` `SettlementReversed` | `PurchaseReceiptPosted` `ShipmentPosted` `PurchaseReturnPosted` `SalesReturnPosted` | 往来账 REST |
| `erp-approval` | BC-7 | 审批定义/实例/节点/任务/动作 + `ApprovalPort` + 适配器 | CAP-P10, I02 | ApprovalInstance | `apr_` | 全部业务模块 | kernel, iam | `ApprovalCompleted` `ApprovalRejected` | 无 | `ApprovalPort` + 待办 REST |
| `erp-document` | BC-8 | 单据关系图、操作日志、审计、变更日志 | CAP-P08, P09 | 无（追加型） | `doc_` | app | kernel, iam | 无 | 全部业务事件 | 溯源 + 审计查询 |
| `erp-reporting` | BC-9 | 读模型与报表 | CAP-A01..A06 | 无 | `rpt_` | app | kernel, iam | 无 | 全部业务事件 | 报表 REST |
| `erp-app` | —（组装） | Spring Boot 启动、REST 装配、全局异常、安全过滤链、Flyway 汇总、健康检查 | — | 无 | 无 | — | 全部 | 无 | 无 | HTTP `:8500` |
| `erp-console` | —（前端） | Vue 3 + TS 管理控制台 | — | — | — | — | `erp-app` REST | — | — | 浏览器 |

## 2. 允许 / 禁止的依赖

```text
erp-app
  └─► erp-procurement ─┐
      erp-sales       ─┼─► erp-inventory ─┐
      erp-finance     ─┘                  │
      erp-document                        ├─► erp-masterdata ─┐
      erp-reporting                       │   erp-approval   ─┼─► erp-iam ─┬─► erp-numbering
                                          └───────────────────┘            └─► erp-kernel
```

**禁止**（ArchUnit 强制，违反即构建失败）：

| 规则 | 说明 |
|---|---|
| 反向依赖 | `erp-inventory` 不得依赖 `erp-procurement` / `erp-sales`（库存不认识谁在用它；过账请求由调用方传入来源单据标识） |
| 横向依赖 | `erp-procurement` 不得依赖 `erp-sales`，反之亦然 |
| 跨模块表访问 | 任何模块的 Mapper/SQL 不得出现其他模块的表前缀 |
| 实体跨模块 | 领域对象与持久化对象不得跨模块引用；跨模块只传 DTO / 事件契约 |
| Controller → Mapper | Controller 不得直接依赖 Mapper（提示词第三十章禁止事项 4） |
| 业务层拼 SQL | 应用层与领域层不得出现 SQL 字符串（全局开发规范 §7） |
| 环依赖 | 模块间不得存在依赖环 |

## 3. `erp-finance` 消费上游事件而不是上游直接写 AR/AP

`erp-procurement` **不**调用 `erp-finance` 创建应付。它发布 `PurchaseReceiptPosted`，`erp-finance` 订阅后自己决定金额、税额、账期与币种。
理由：金额与税务规则属于往来账的统一语言，放在采购里会让两个上下文都懂钱；且未来抽进程时这条边已经是异步的。
幂等靠 INV-06：`fin_account_payable` 上 `(tenant_id, source_doc_type, source_doc_id)` 唯一索引。

## 4. 与提示词候选结构的五处差异（及理由）

| 提示词候选 | 本方案 | 理由 |
|---|---|---|
| `erp-product` 与 `erp-master-data` 并列 | **合并为 `erp-masterdata`** | 商品是主数据的一部分，拆开会让"主数据中心"失去统一语言；SPU/SKU 与供应商/客户共享同一套「编码唯一 + 引用后受限修改 + 版本」规则 |
| `erp-identity` | **`erp-iam`**，且**只做授权**，认证外包 | `auth-platform` 已提供 Casdoor/OIDC（REPOSITORY_EVIDENCE）；重复实现认证是浪费。授权（RBAC + DataScope）留在 ERP，理由见 `SECURITY_ARCHITECTURE.md` |
| 无 `erp-document` | **新增 `erp-document`** | 提示词第九/十三章要求单据关系图与业务审计，但候选结构里没有它们的归属。它们是横跨全部业务域的平台能力，不能塞进任一业务模块 |
| 无 `erp-numbering` / `erp-kernel` | **新增两个平台件** | 编号（第二十一章）与统一单据/状态机机制（第九/十章）需要明确归属，否则会被复制到每个业务模块 |
| `erp-common` + `erp-infrastructure` | **合并进 `erp-kernel`，且刻意保持很小** | 两个"公共模块"最容易长成垃圾桶；共享内核的代价是耦合发布，范围必须小。基础设施代码放在各模块自己的 `infrastructure` 包内（六边形的适配器层），不集中 |
| `erp-web` | **`erp-app`（后端组装）+ `erp-console`（前端）** | `erp-web` 名字同时暗示"Web 层"和"前端"，会诱导把 Controller 集中到一个模块，从而绕过模块边界 |

## 5. 单模块内部结构（Core 上下文，六边形）

```text
erp-inventory/
  src/main/java/com/lrj/erp/inventory/
    domain/           聚合、实体、值对象、领域服务、领域事件、仓储端口(interface)
    application/      应用服务(用例编排)、命令/查询对象、事务边界
    infrastructure/   仓储实现(MyBatis Mapper)、外部适配器、事件发布实现
    interfaces/       REST Controller、DTO、装配器
  src/main/resources/
    mapper/           MyBatis XML（复杂 SQL 只出现在这里）
    db/migration/     Flyway（表与字段注释必须写，见全局开发规范 §一）
  src/test/java/      单元 / 领域 / 仓储 / 集成 / 架构测试
```

Supporting / Generic 上下文（`erp-iam` `erp-masterdata` `erp-approval` `erp-document`）用 `controller / service / repository / model` 四层，**明确不使用六边形**（理由见 `DOMAIN_MAP.md` 第 3 节）。
