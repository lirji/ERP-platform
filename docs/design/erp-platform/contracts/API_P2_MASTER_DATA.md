# API CONTRACT — P2 Master Data

> Owner: `public-engineering-workflow:contracts` · 状态 `ACTIVE`
> 覆盖 CAP-P05（主数据中心）、CAP-G04（导入导出）。跨切面语义见 [`CONTRACTS.md`](CONTRACTS.md)。
> CAP-G02（MinIO 附件）**本阶段不实现**：P2 的五条出口条件均不涉及附件，
> 在没有真实使用场景的情况下接入对象存储属于提前建设。触发时再做。

---

## 1. 主数据的四条共同规则

这四条适用于**所有**主数据（商品、往来单位、仓库、财务基础数据），不逐个重复。

### 1.1 编码租户内唯一

```sql
CREATE UNIQUE INDEX uk_md_xxx_code ON md_xxx (tenant_id, code);
```

唯一性由**数据库**保证，不是先查后插——并发建档时先查后插必然重码。
应用层的重复校验只用于给出友好错误。

### 1.2 生命周期：启用 / 停用，而不是删除

主数据一旦被单据引用就不能删除——删了历史单据会指向空。因此只有 `enabled` 开关：

| 状态 | 新单据可引用 | 已有单据 |
|---|---|---|
| `enabled = true` | ✅ | 正常 |
| `enabled = false` | ❌ 拒绝（`ERP-MD-3001`） | **完全不受影响** |

> 停用只切断**未来**的引用。让停用影响历史单据，等于用一次主数据维护改写了已发生的业务事实。

### 1.3 被引用后，关键字段锁定

| 字段类别 | 被引用后 | 理由 |
|---|---|---|
| **关键字段**：`code`、基本单位 `base_unit_id` | **禁止修改**（`ERP-MD-3002`） | 编码是单据上的业务标识；基本单位一改，历史库存数量的含义全变了 |
| 描述字段：`name`、`spec`、备注 | 允许修改 | 历史单据看到的是快照，不受影响（§1.4） |

### 1.4 `MasterDataRef` 快照

单据引用主数据时，**同时存下当时的展示字段快照**：

```json
{ "id": 10023, "code": "SKU-001", "name": "A4 复印纸 70g", "unitName": "包" }
```

- 单据展示读**快照**，不 JOIN 主数据表；
- 主数据后续改名，历史单据显示的仍是改名前的名字；
- 需要"当前值"的场景（如主数据管理页）才按 `id` 读实时表。

> 这不是冗余，是**业务正确性**：一张 2026 年的采购单，显示的必须是下单当时的商品名称。
> 靠 JOIN 取实时值会让历史单据随主数据变化而"改写自己"。

### 1.5 引用登记

主数据被引用时写入 `md_reference`。它回答两个问题：
「这条主数据能不能改关键字段 / 能不能停用后再启用」与「谁在用它」。

---

## 2. API

| 方法 | 路径 | 权限点 |
|---|---|---|
| `GET` | `/api/v1/masterdata/skus` | `masterdata:sku:read` |
| `POST` | `/api/v1/masterdata/skus` | `masterdata:sku:write` |
| `PUT` | `/api/v1/masterdata/skus/{id}` | `masterdata:sku:write` |
| `PUT` | `/api/v1/masterdata/skus/{id}/disable` | `masterdata:sku:write` |
| `PUT` | `/api/v1/masterdata/skus/{id}/enable` | `masterdata:sku:write` |
| `POST` | `/api/v1/masterdata/skus/import` | `masterdata:sku:write` |

供应商、客户、仓库同构，前缀分别为 `suppliers` / `customers` / `warehouses`。

## 3. 批量导入语义

```
POST /api/v1/masterdata/skus/import
```

- **整批原子**：任一行校验失败 → 整批回滚，不写入任何一行。
  部分成功会让调用方无从判断"哪些进去了"，重试时又会重复导入前半批。
- 响应给出**全部**失败行的行号与原因，而不是遇到第一个错就返回——
  让用户改一轮就能全部修好，而不是改一行试一次。
- 导入同样受 §1.1 唯一约束与 §1.2 生命周期约束。

```json
{ "total": 1000, "imported": 0, "failed": [ { "row": 37, "code": "SKU-037", "reason": "编码已存在" } ] }
```

## 4. 错误码（`ERP-MD-*`）

| Code | HTTP | 含义 |
|---|---|---|
| `ERP-MD-1001` | 400 | 主数据字段校验失败 |
| `ERP-MD-2001` | 409 | 编码在租户内已存在 |
| `ERP-MD-3001` | 422 | 主数据已停用，不能被新单据引用 |
| `ERP-MD-3002` | 422 | 主数据已被引用，关键字段不可修改 |
| `ERP-MD-3003` | 422 | 引用的主数据不存在 |
| `ERP-MD-3004` | 422 | 批量导入存在校验失败行，整批未写入 |
