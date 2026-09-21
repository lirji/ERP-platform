# API CONTRACT — P3 Inventory Core

> Owner: `public-engineering-workflow:contracts` · 状态 `ACTIVE`
> 覆盖 CAP-C01..C04、CAP-S05。跨切面语义见 [`CONTRACTS.md`](CONTRACTS.md)。

---

## 1. 库存桶（Bucket）

库存不是 `sku_id + stock`（提示词禁止事项第 8 条）。台账的主键是**六元组**：

```
(tenant_id, company_id, warehouse_id, location_id, sku_id, batch_no)
```

| 维度 | MVP 状态 | 说明 |
|---|---|---|
| `location_id` | **哨兵 `0`** | 按假设 `A-11` 不启用库位，但**维度先在主键里**——后加维度等于全量数据迁移 |
| `batch_no` | 哨兵 `'-'` 或真实批次 | SKU 的 `batch_managed` 决定；批次是主键的一部分（CAP-C04） |

> 这两个哨兵是本设计的关键取舍：**启用时只填充维度值，不改主键结构**。

## 2. 数量语义

| 字段 | 存储 | 含义 |
|---|---|---|
| `on_hand` | ✅ 列 | 实际在库 |
| `reserved` | ✅ 列 | 已被销售订单预占 |
| `locked` | ✅ 列 | 质检/冻结等锁定 |
| `in_transit` | ✅ 列 | 调拨在途（企业维度守恒用） |
| **`available`** | ❌ **没有列** | **算出来的**：`on_hand - reserved - locked` |

> **`available` 绝不能是一列。** 它是三个数的函数；一旦落列，就要在每条写路径上同步维护，
> 任何一处漏掉都会让可用量与事实不符，而且这种不一致无法自愈。
> 出口条件专门要求验证「`available` 无对应数据库字段」，由架构测试强制。

## 3. 不可变流水（Inventory Transaction）

**任何**库存变化都写一条流水，且流水**不可修改、不可删除**。

```
余额 == 该桶全部流水的代数和         ← INV-01，本阶段最重要的不变量
```

每条流水必须能反查来源单据行：`source_doc_type` + `source_doc_id` + `source_line_id`。
没有来源的库存变化不允许存在——那正是「账实不符且无法追溯」的根源。

## 4. 幂等（INV-04）

```sql
CREATE UNIQUE INDEX uk_inv_txn_source
    ON inv_transaction (tenant_id, source_doc_type, source_doc_id, source_line_id, direction);
```

同一来源行重复过账 10 次，只有一次效果。幂等由**唯一索引**保证，不由应用层判重——
并发下先查后写不成立。

## 5. 并发与非负（INV-02）

扣减用**条件更新 + 影响行数判定**：

```sql
UPDATE inv_balance SET on_hand = on_hand - :qty, version = version + 1
WHERE ... AND on_hand - reserved - locked >= :qty
```

影响行数为 0 = 可用量不足（`ERP-INV-3001`），**不得当作成功**。
是否允许负库存由业务参数 `inventory.negative.allowed` 控制，默认禁止（`Q-09` 的安全假设）。

## 6. 预占（CAP-C03）

```
预占 → 部分消耗 → 释放剩余
```

- `reserved_qty`：预占总量；`consumed_qty`：已被出库消耗的量。
- **释放不得超过 `reserved_qty - consumed_qty`**，否则 `reserved` 会被扣成负数。
- 取消订单释放全部未消耗预占。

## 7. 错误码（`ERP-INV-*`）

| Code | HTTP | 含义 |
|---|---|---|
| `ERP-INV-1001` | 400 | 过账参数非法（数量非正等） |
| `ERP-INV-3001` | 422 | 可用库存不足 |
| `ERP-INV-3002` | 422 | 预占释放量超过未消耗量 |
| `ERP-INV-3003` | 422 | 预占不存在 |
| `ERP-INV-3004` | 422 | 批次管理的 SKU 必须指定批次 |
