# ERP Business Capability Map

> Owner: `project-bootstrap` · Phase 1
> 发现顺序：**业务链路 → 角色 → 业务对象 → 业务动作 → 业务规则 → 状态 → 能力**。
> 能力从链路上的动作与规则归纳，**不从模块名或表名倒推**；本文件**不出现服务名**。

## 0. 先有链路，才有能力

```text
BL-1  采购到付款 Procure-to-Pay
      采购申请 → 审批 → 采购订单 → 收货 → 入库 → (退货) → 供应商对账 → 应付 → 付款 → 核销

BL-2  订单到收款 Order-to-Cash
      报价 → 销售订单 → 审批 → 信用校验 → 库存预占 → 拣货 → 出库 → 发货 → 签收
           → (退货) → 应收 → 收款 → 核销

BL-3  库存内部作业 Internal Inventory Operations
      调拨(出库→在途→入库) · 盘点(封存→点数→差异→审批→调整) · 报损 · 报溢 · 库存调整

BL-4  主数据生命周期 Master Data Lifecycle
      新建 → 审核 → 启用 → 被单据引用 → 受限修改/版本 → 停用 → (不可删除)

BL-5  组织与权限管理
      租户开通 → 公司/组织/部门建模 → 员工入职 → 用户开通 → 角色授权 → 数据范围授权 → 离职回收

BL-6  经营分析
      业务单据 → 读模型 → 库存余额/流水 · 采购统计 · 销售统计 · 应收应付账龄 · 经营概览
```

角色（BL 上的 Actor）：采购员 · 采购主管 · 仓管员 · 销售员 · 销售主管 · 应收会计 · 应付会计 · 出纳 · 财务主管 · 系统管理员 · 经营者。

核心业务对象：采购申请 · 采购订单 · 采购收货单 · 入库单 · 采购退货单 · 销售报价 · 销售订单 · 出库单 · 发货单 · 销售退货单 · 调拨单 · 盘点单 · 库存调整单 · 库存余额 · 库存流水 · 库存预占 · 应付单 · 付款单 · 应收单 · 收款单 · 核销记录 · 商品/SKU · 供应商 · 客户 · 仓库。

跨链路反复出现的**业务规则族**（它们才是能力的真正来源）：
`唯一性与编号` · `状态合法迁移` · `部分执行与累计量` · `幂等与重复提交` · `数量守恒` · `金额守恒与不超额` · `主数据引用快照` · `数据可见范围` · `留痕与追溯`。

---

## 1. 能力总表

MVP 分层依据（`basis`）只能取：`BUSINESS_LOOP` `BUSINESS_DEPENDENCY` `TECHNICAL_DEPENDENCY` `DELIVERY_RISK` `PRECEDENCE`。

### 1.1 Core（差异化价值、规则复杂、需要持续投入）

| ID | 能力 | 业务对象 | 关键动作 | 关键规则 | 证据 | 可依赖外部 | MVP |
|---|---|---|---|---|---|---|---|
| CAP-C01 | 库存余额台账 | 库存余额桶 | 增、减、锁定、解锁 | 桶唯一键 `tenant+company+warehouse+(location)+sku+batch`；`onHand ≥ 0`；`reserved+locked ≤ onHand`；`available` 是**计算值**不是可改写字段 | USER_REQUIREMENT · ARCHITECTURE_PRINCIPLE | 否（权威在 ERP） | `MUST_HAVE` |
| CAP-C02 | 库存流水账（Inventory Ledger） | 库存流水 | 记账（仅追加） | **不可变**；任何余额变化必须有且只有一条来源单据；`期初+入-出=期末` 可对账 | USER_REQUIREMENT | 否 | `MUST_HAVE` |
| CAP-C03 | 库存预占与释放 | 库存预占 | 预占、部分消耗、释放、过期回收 | 预占按来源单据行幂等；释放不得超过未消耗量；取消订单必须释放 | USER_REQUIREMENT | 否 | `MUST_HAVE` |
| CAP-C04 | 批次维度管理 | 库存批次 | 建批、指定批次出入 | 批次是**台账主键的一部分**；无批次商品用非空哨兵 `NO_BATCH`，禁止用 NULL 规避唯一键 | ARCHITECTURE_PRINCIPLE | 否 | `MUST_HAVE`（仅维度+手工指批） |
| CAP-C05 | 采购订单履约 | 采购订单/收货单/入库单 | 下单、收货、入库、超收、少收、取消、关闭 | 累计收货量 ≤ 订单量 ×(1+超收比例)；超收需配置+审批；已执行部分不可取消，只能关闭剩余 | USER_REQUIREMENT | 否 | `MUST_HAVE` |
| CAP-C06 | 销售订单履约 | 销售订单/出库单/发货单 | 接单、预占、拣货、出库、发货、签收、分批 | 发货量 ≤ 预占量；部分出库保留剩余；取消释放预占 | USER_REQUIREMENT | 否 | `MUST_HAVE` |
| CAP-C07 | 应付管理与付款 | 应付单/付款单 | 生成应付、付款、冲销 | 应付按 `(来源单据类型,来源单据ID)` **唯一**，重复生成被数据库拒绝；累计付款 ≤ 应付金额 | USER_REQUIREMENT | 否 | `MUST_HAVE` |
| CAP-C08 | 应收管理与收款 | 应收单/收款单 | 生成应收、收款、冲销 | 同 CAP-C07 镜像规则 | USER_REQUIREMENT | 否 | `MUST_HAVE` |
| CAP-C09 | 核销（Settlement / Write-off） | 核销记录 | 核销、反核销 | `已核销 ≤ 单据金额`；同一对 (应收/付单, 收/付款单) 不重复核销；反核销必须留痕 | USER_REQUIREMENT | 否 | `MUST_HAVE` |

### 1.2 Supporting（业务必需、但不构成差异化）

| ID | 能力 | 业务对象 | 关键动作 | 关键规则 | 证据 | 可依赖外部 | MVP |
|---|---|---|---|---|---|---|---|
| CAP-S01 | 采购退货 | 采购退货单 | 退货出库、冲减应付 | 退货量 ≤ 已入库未退量；冲减走**新单据**，不改原单 | USER_REQUIREMENT | 否 | `SHOULD_HAVE` |
| CAP-S02 | 销售退货 | 销售退货单 | 退货入库、冲减应收 | 同上镜像；退货入库不得沿用原出库时的批次结论 | USER_REQUIREMENT | 否 | `SHOULD_HAVE` |
| CAP-S03 | 仓间调拨 | 调拨单 | 调出、在途、调入 | **在途不计入任一仓 onHand**；企业维度守恒：期末仓内合计+在途 = 期初+外部收 −外部发 | USER_REQUIREMENT | 否 | `SHOULD_HAVE` |
| CAP-S04 | 库存盘点 | 盘点单 | 封存范围、点数、复核、审批、生成差异调整 | 差异必须经审批才落台账；盘点期间范围内的变动需冻结或记账补偿 | USER_REQUIREMENT | 否 | `SHOULD_HAVE` |
| CAP-S05 | 报损报溢与库存调整 | 库存调整单 | 调增、调减 | 必须有原因码与审批；直接改余额而不写流水被禁止 | USER_REQUIREMENT | 否 | `SHOULD_HAVE` |
| CAP-S06 | 客户信用额度控制 | 客户信用 | 占用、释放、超限拦截 | 占用 = 未核销应收 + 未出库订单金额；超限需审批放行 | USER_REQUIREMENT | 否 | `SHOULD_HAVE` |
| CAP-S07 | 供应商对账 | 对账单 | 生成对账期、确认、差异处理 | 对账确认后应付锁定，差异走调整单 | USER_REQUIREMENT | 否 | `COULD_HAVE` |
| CAP-S08 | 销售报价 | 报价单 | 报价、转订单、失效 | 报价不占库存；转单时重新校验价格与信用 | USER_REQUIREMENT | 否 | `COULD_HAVE` |
| CAP-S09 | 采购申请 | 采购申请单 | 申请、审批、转采购订单 | 申请不产生库存与金额效果；可多申请合并转单 | USER_REQUIREMENT | 否 | `SHOULD_HAVE` |
| CAP-S10 | 库存计价（移动加权平均） | 库存成本 | 入库加权、出库结转 | 成本口径见 `Q-05`；**任何计价方法都可从不可变流水重算** | BUSINESS_ASSUMPTION | 否 | `SHOULD_HAVE` |

### 1.3 Generic（各行业通用、已有成熟解法，优先 BUY / INTEGRATE / REUSE_EXISTING）

| ID | 能力 | 关键规则 | 证据 | 可依赖外部 | MVP |
|---|---|---|---|---|---|
| CAP-G01 | 身份认证与 SSO | 认证与授权分离：认证外包，**授权留在 ERP** | REPOSITORY_EVIDENCE `auth-platform/README.md` | **是** — 复用 `auth-platform`(Casdoor) | `MUST_HAVE` |
| CAP-G02 | 文件存储与附件 | 附件与单据关联，权限随单据 | REPOSITORY_EVIDENCE `dev-infra/docs/service-catalog.md`(MinIO) | 是 — dev-infra MinIO | `SHOULD_HAVE` |
| CAP-G03 | 消息通知 | 审批待办、异常提醒 | INDUSTRY_PATTERN | 是 | `COULD_HAVE` |
| CAP-G04 | 导入导出 | 主数据批量导入、报表导出；导入必须校验并可回滚 | INDUSTRY_PATTERN | 否 | `SHOULD_HAVE` |

### 1.4 Platform（被多个业务域复用的共享能力）

| ID | 能力 | 业务对象 | 关键规则 | 证据 | MVP |
|---|---|---|---|---|---|
| CAP-P01 | 租户与组织建模 | Tenant/Company/Organization/Department/Employee | 单表 `org_unit` + `type` + 闭包/物化路径，**不为部门与业务单元各建一套表** | ARCHITECTURE_PRINCIPLE · 参考 `oa-platform` 记录 | `MUST_HAVE` |
| CAP-P02 | 用户与角色 | User/Role/UserRole | 一人多岗；角色是授权载体不是组织节点 | USER_REQUIREMENT | `MUST_HAVE` |
| CAP-P03 | 功能权限（菜单/按钮/API） | Permission/Menu | **API 权限是唯一安全边界**；菜单与按钮只是呈现 | ARCHITECTURE_PRINCIPLE | `MUST_HAVE` |
| CAP-P04 | 数据权限（DataScope + 隔离） | DataScope | 本人/本部门/本部门及子部门/指定部门/指定公司/全部；租户·公司·仓库隔离**在查询层统一施加**，过滤字段走服务端允许列表 | USER_REQUIREMENT | `MUST_HAVE` |
| CAP-P05 | 主数据中心 | 商品 · 往来单位 · 财务基础数据 | 编码唯一、启停用、版本；**被单据引用后走受限修改 + 单据端快照** | USER_REQUIREMENT | `MUST_HAVE` |
| CAP-P06 | 编号中心 | 单据号 | `tenant+businessType+date+sequence`；并发唯一、可读、不依赖分布式锁 | USER_REQUIREMENT | `MUST_HAVE` |
| CAP-P07 | 统一单据模型与状态机 | DocumentHeader/Item/Status | **机制统一、状态集合各自拥有**；迁移表 `(当前态,事件)→守卫→动作→目标态`；非法迁移拒绝、迁移幂等、乐观锁并发、迁移日志 | USER_REQUIREMENT | `MUST_HAVE` |
| CAP-P08 | 单据关系图（Document Graph） | DocumentLink | 回答「这张单从哪来」「它产生了什么」；由生产方**发领域事件**登记，登记失败不影响主事务成败判定但必须可补 | USER_REQUIREMENT | `MUST_HAVE` |
| CAP-P09 | 业务审计与操作日志 | OperationLog/AuditLog/BusinessChangeLog | 谁·何时·操作什么·前值·后值·IP·终端·关联单据；禁止记录密钥与证件号明文 | USER_REQUIREMENT | `MUST_HAVE` |
| CAP-P10 | 审批中心（Port + 可插拔引擎） | ApprovalDefinition/Instance/Node/Task/Action/Record | 业务系统以 `(businessType, businessId)` 接入；**审批状态不等于业务状态**，业务状态权威归所属聚合 | USER_REQUIREMENT | `MUST_HAVE`（内置顺序审批） |
| CAP-P11 | 业务参数配置 | Config | 是否允许超收、超收比例、是否允许负库存、信用超限策略等按租户/公司生效 | USER_REQUIREMENT | `MUST_HAVE`（最小集） |

### 1.5 Integration（与外部系统交换数据）

| ID | 能力 | 方向 | 方式 | 证据 | MVP |
|---|---|---|---|---|---|
| CAP-I01 | 身份平台集成 | ERP → auth-platform/Casdoor | OIDC + SDK | REPOSITORY_EVIDENCE `auth-platform/README.md`、`auth-platform-sdk` | `MUST_HAVE` |
| CAP-I02 | 审批引擎集成 | ERP ↔ workflow-platform | SDK(REST) + Kafka 契约 | REPOSITORY_EVIDENCE `workflow-platform/README.md`（`workflow-platform-sdk`、`workflow.command.start.v1`、`workflow.action.applied.v1`、server `:8300`） | `COULD_HAVE`（见 Roadmap P8） |
| CAP-I03 | WMS 仓储执行集成 | ERP ↔ wms-platform | 端口预留，方式待定 | REPOSITORY_EVIDENCE `wms-platform/docs/design/02-domain.md` | `LATER`（见 `Q-03`） |
| CAP-I04 | 电商/渠道订单接入 | 外部 → ERP | 待定 | INDUSTRY_PATTERN | `LATER` |
| CAP-I05 | 银行回单/支付 | 外部 → ERP | 待定 | INDUSTRY_PATTERN | `LATER` |
| CAP-I06 | 税务/电子发票 | ERP ↔ 外部 | 待定 | INDUSTRY_PATTERN | `LATER` |

### 1.6 Analytics（报表、指标、数据服务）

| ID | 能力 | 数据来源 | 关键规则 | MVP |
|---|---|---|---|---|
| CAP-A01 | 库存余额与流水报表 | 台账 + 流水 | 与 CAP-C02 对账口径一致，不另造第二套口径 | `SHOULD_HAVE` |
| CAP-A02 | 采购统计 | 采购读模型 | 不用几十张业务表实时 JOIN | `COULD_HAVE` |
| CAP-A03 | 销售统计 | 销售读模型 | 同上 | `COULD_HAVE` |
| CAP-A04 | 应收账龄 | AR 读模型 | 账龄桶口径可配置 | `COULD_HAVE` |
| CAP-A05 | 应付账龄 | AP 读模型 | 同上 | `COULD_HAVE` |
| CAP-A06 | 经营概览 | 各读模型 | 指标定义须与单项报表一致 | `LATER` |

---

## 2. 反模板自检

| 检查 | 结果 |
|---|---|
| 能力是否从本系统的 BL-1..BL-6 归纳？ | 是。每条能力的「关键动作」都能在链路上指出位置 |
| 是否与 `wms-platform` 的能力地图相同？ | 否。ERP 的 Core 是**账与金额**（台账、履约、应收应付、核销）；WMS 的 Core 是**物理执行**（波次、拣货、上架、序列号、设备）。二者 Core 不重叠，重叠点只在库存数量口径，已在 `SYSTEM_BOUNDARY.md` 划清 |
| 能力表里是否出现服务名？ | 否 |
| Generic 能力是否默认自研？ | 否。CAP-G01 走 `REUSE_EXISTING` |
| 六类是否齐全？ | 齐全：Core 9 · Supporting 10 · Generic 4 · Platform 11 · Integration 6 · Analytics 6 |

追溯链（后续文档必须逐段落实）：

```text
Goal → Business Capability → Domain / Subdomain → Bounded Context → Architecture Boundary → Module
```
