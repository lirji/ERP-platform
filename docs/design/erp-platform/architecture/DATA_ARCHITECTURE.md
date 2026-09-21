# Data Architecture

> Owner: `backend-architecture-design`
> 逐项回答「是否真的需要 / 解决什么问题 / 哪个阶段引入 / 替代方案 / 复杂度 / 运维成本」。**不为"企业级完整性"一次性全部引入。**

## 1. 组件判定

| 组件 | 需要？ | 解决什么问题 | 引入阶段 | 替代方案 | 复杂度 / 运维成本 |
|---|---|---|---|---|---|
| **OLTP（PostgreSQL 16）** | ✅ 必有 | 全部权威业务数据 | Phase 0 | 无 | 复用 `dev-infra` 共享实例，独立 database + owner |
| Cache（Redis） | ❌ **不引入** | 当前无读热点超出索引能力（NFR-01 目标留 100× 余量） | 触发后引入：单实例改多实例**且** L1 失效无法跨实例传播；或主数据查询 P95 > 200ms | L1 Caffeine（进程内，主数据与权限快照，TTL ≤ 60s） | 引入后需处理失效传播、脏读边界 |
| Search（ES） | ❌ **不引入** | ERP 的查询是结构化筛选 + 分页，PostgreSQL 复合索引 + 分页足够 | 触发后引入：商品/单据全文检索成为主要入口，或筛选组合导致索引无法覆盖 | PG 复合索引、`pg_trgm`（模糊匹配） | 共享 `dev-infra` 无 ES 实例（service-catalog 只列 lc4j 专属），引入需新增实例 |
| **MQ（Kafka / RabbitMQ）** | ❌ **MVP 不引入** | 当前无跨进程异步协作（单进程） | 触发后引入：① 抽出第二个进程（Stage 3）；② 接入 `workflow-platform`（其契约就是 Kafka） | **同库事务性 Outbox + 进程内可靠投递** | `dev-infra` 已有 Kafka 3.8 / RabbitMQ 3.13，引入时复用而非新建 |
| OLAP | ❌ 不引入 | 分析量级远未达到 | 触发后引入：报表查询影响 OLTP 且只读副本不够 | 读模型表（Stage 2） | — |
| File / Object Storage（MinIO） | ✅ Phase 2 | 单据附件、导入导出文件 | Phase 2 | 本地磁盘（不可接受：多实例不共享） | 复用 `dev-infra` MinIO，独立 bucket + access key |
| CDC | ❌ 不引入 | 无需要从权威库派生的外部投影 | 触发后引入：需要把 ERP 数据可靠同步给外部 BI/数据湖 | 事件投影 | — |
| 调度 | ✅ 最简 | Outbox 投递、预占过期回收 | Phase 1 | — | Spring `@Scheduled` + 数据库锁；分布式调度推迟到多实例阶段 |

## 2. 核心数据的权威与事务边界

| 数据 | Source of Truth | 写入者 | 事务边界 | 关键约束 |
|---|---|---|---|---|
| 库存余额 `inv_inventory_balance` | Inventory | 仅 `StockPostingService` | 与流水**同一本地事务** | 唯一键 `(tenant_id, company_id, warehouse_id, location_id, sku_id, batch_id)`；`on_hand_qty >= 0` CHECK；`version` 乐观锁 |
| 库存流水 `inv_inventory_transaction` | Inventory | 仅追加，**无 UPDATE / DELETE** | 同上 | 唯一键 `(tenant_id, source_doc_type, source_doc_id, source_line_id, direction)` → 实现 INV-04 幂等 |
| 库存预占 `inv_inventory_reservation` | Inventory | 预占/释放服务 | 与余额同事务 | 唯一键 `(tenant_id, source_doc_type, source_line_id)` |
| 采购订单行 `pur_purchase_order_line` | Procurement | 采购应用服务 | 订单聚合内 | `received_qty <= ordered_qty * (1 + over_receipt_ratio)` 由领域校验 + CHECK 兜底；`version` |
| 销售订单行 `sal_sales_order_line` | Sales | 销售应用服务 | 订单聚合内 | `shipped_qty <= ordered_qty`；`version` |
| 应收/应付 `fin_account_receivable` / `fin_account_payable` | Finance | 事件消费者 | 单聚合本地事务 | **唯一键 `(tenant_id, source_doc_type, source_doc_id)`** → 实现 INV-06；`written_off_amount <= amount` CHECK |
| 核销 `fin_settlement_record` | Finance | 核销服务 | 与两端单据同事务 | 唯一键 `(tenant_id, bill_type, bill_id, payment_type, payment_id, reversal_of)` |
| 单据号 `num_sequence` | Numbering | 编号服务 | 独立短事务 | 唯一键 `(tenant_id, business_type, date_key)` |
| 组织 `iam_org_unit` + `iam_org_closure` | IAM | 组织服务 | 树变更单事务 | `org_path` 物化路径；**PG 非 C locale 下前缀 LIKE 必须建 `text_pattern_ops` 索引** |
| 单据关系 `doc_document_link` | Document | 事件消费者 | 独立事务 | 唯一键 `(tenant_id, source_type, source_id, target_type, target_id, relation_type)` |
| 读模型 `rpt_*` | **派生，非权威** | 投影器 | 独立事务 | 必须有从权威数据全量重建的脚本 |

## 3. 类型与精度（全局开发规范 §5）

| 概念 | 类型 | 规则 |
|---|---|---|
| 数量 | `NUMERIC(20,6)`；Java `BigDecimal`；API 十进制字符串 | SKU 定义基本单位与允许精度，换算必须精确，**禁止截断凑数** |
| 金额 | `NUMERIC(20,4)` + `currency CHAR(3)`；值对象 `Money` | 币种必带；舍入规则显式（`HALF_UP`，scale=2 用于结算，4 用于单价计算） |
| 时间 | `TIMESTAMPTZ`，存 UTC | 业务时间（过账日期）与系统时间分开；仓库时区用于日期换算，记录原值与规则版本 |
| 批次效期 | `[produced_at, expires_at)` 半开区间 | 只给日期时按仓库 IANA 时区换算，**不默认当日 00:00**（会差一天） |
| 枚举落库 | 显式稳定 `code`（字符串或整数常量） | **禁止使用 `ordinal`**；必须定义未知值与废弃值的兼容策略（全局开发规范 §4） |
| 逻辑删除 | 主数据用 `status`（启用/停用），**业务单据不做逻辑删除**，用状态机的 `CANCELLED` / `CLOSED` | 已过账的单据永不删除 |

## 4. 多租户与数据权限的落库形态

- 每张业务表首列 `tenant_id`，**参与每一个唯一索引与每一个查询条件**（INV-09）。
- 单据表冗余 `company_id` + `org_id` + `org_path`（**发生时快照，不随组织调整回刷**）；数据权限用 `org_path LIKE '/1/23/%'` 前缀匹配，**禁止 `IN (5000 个 id)`**。
- 该做法的依据来自 `oa-platform` 的既有决策记录（用户的项目笔记）；本项目独立成立的理由是：数据权限过滤要下推到 SQL，`IN` 列表在部门数量增长时会击穿查询计划。
- 租户隔离形态为共享库 + `tenant_id`（A-10）；隔离级别若升级，`tenant_id` 已在，可按租户迁库。见 `Q-04`。

## 5. 索引与分页

| 场景 | 设计 |
|---|---|
| 库存可用量查询 | 唯一键即主索引；`(tenant_id, company_id, sku_id)` 覆盖索引用于跨仓汇总 |
| 库存流水追溯 | `(tenant_id, source_doc_type, source_doc_id)` 与 `(tenant_id, bucket_key, posted_at)` |
| 单据列表 | `(tenant_id, company_id, status, created_at DESC, id DESC)`——**排序必须稳定**（加 `id` 兜底），否则分页会漏/重 |
| 单据溯源 | `doc_document_link` 双向索引（source 方向 + target 方向） |
| 报表 | 走 `rpt_*` 读模型，**禁止几十张业务表实时 JOIN**（提示词第二十章） |

分页统一使用 keyset 或带稳定排序的 limit/offset；批量操作必须有界（单批 ≤ 1000 行）。

## 6. 迁移

- Flyway，每模块独立迁移目录，版本号带模块前缀（`V1_001__iam_init.sql`）。
- **建表必须写表注释与每个字段的注释**（全局开发规范 §一；PostgreSQL 用 `COMMENT ON TABLE` / `COMMENT ON COLUMN`）。没有注释的建表语句视为未完成。
- 迁移兼容滚动升级：先扩展 → 再迁移 → 最后收缩；**已执行的迁移不得修改**。
