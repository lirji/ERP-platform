# ERP System Vision

> Owner: `project-bootstrap` · Phase 1 · 模式 `GREENFIELD_PLANNING`
> 标签：`KNOWN` / `USER_CONSTRAINT` / `ASSUMPTION` / `UNKNOWN` / `OPEN_QUESTION`

| 字段 | 内容 | 标签 | 证据类型 |
|---|---|---|---|
| System Name | `erp-platform`（企业资源管理平台），groupId `com.lrj.erp` | `ASSUMPTION` A-00 | BUSINESS_ASSUMPTION |
| Business Goal | 让一家企业在**同一套数据**上完成「采购—入库—库存—销售—出库—应收应付—结算」的日常经营，并且每一笔库存与金额变化都能追溯到来源单据 | `KNOWN` F-01 | USER_REQUIREMENT |
| Problem Statement | 进销存与财务分散在 Excel / 独立工具中时：库存账实不符、单据之间无法互相追溯、应收应付靠人工对账、权限与审计缺失 | `ASSUMPTION` A-01 | INDUSTRY_PATTERN |
| Target Users | 采购员、采购主管、仓管员、销售员、销售主管、应收会计、应付会计、出纳、财务主管、系统管理员、经营者 | `KNOWN` F-02 | USER_REQUIREMENT |
| Business Value | ① 库存可信（台账=流水累计）② 单据链可追溯 ③ 应收应付自动生成并可核销 ④ 权限与审计满足内控 ⑤ 模块化可持续扩展 | `KNOWN` F-03 | USER_REQUIREMENT |
| System Positioning | **经营主数据与业务单据的权威系统（System of Record）**。不是仓储作业执行系统（WMS），不是专业总账财务软件，不是 CRM | `ASSUMPTION` A-02 | ARCHITECTURE_PRINCIPLE |
| Business Scope | 基础资料 → 采购 → 入库 → 库存 → 销售 → 出库 → 应收应付 → 结算核销 → 审批 → 报表 | `KNOWN` F-04 | USER_REQUIREMENT |
| Expected Scale | 见下方推导，全部为 `ASSUMPTION` | `ASSUMPTION` A-03..A-06 | BUSINESS_ASSUMPTION |
| Current Constraints | 多租户 / 多公司 / 多组织 / 多仓库 / 多角色；不得为展示技术强拆服务；不得一上来 CRUD | `USER_CONSTRAINT` F-05 | USER_REQUIREMENT |
| Future Expectations | 可扩展到总账凭证、多币种折算、WMS 对接、电商渠道接入、BI | `KNOWN` F-06 | USER_REQUIREMENT |

## Expected Scale 推导（全部 ASSUMPTION，未经用户确认）

用户**没有给出任何规模数字**。以下是为了让架构决策可判定而采用的前提，不是需求：

| 编号 | 假设 | 依据 | 若不成立的影响 | 验证方式 |
|---|---|---|---|---|
| A-03 | 单租户 100–2,000 命名用户，并发在线 ≤ 200 | 「中小型及中大型企业」的常见区间 · INDUSTRY_PATTERN | 若达到万人级，判权热路径需按 `oa-platform` 的位图快照方案重做 | 向干系人确认在册员工数 |
| A-04 | 日业务单据 ≤ 8,000 张头、≤ 50,000 行 | 与 A-03 的用户数匹配 | 超过 10× 时需要读写分离与报表预聚合提前到 MVP | 统计现有 Excel / 旧系统单量 |
| A-05 | SKU ≤ 500,000；库存桶（tenant×company×warehouse×sku×batch）≤ 2,000,000 | 商品主数据常见上限 | 超过 10× 时库存余额表需要分区 | 统计现有商品档案数 |
| A-06 | 峰值倍数 5×（上午 9–11 点、月末结账集中） | 企业内部系统常见日内分布 | 峰值更高时需要入口限流 | 上线后观测 |

**由 A-03..A-06 推导出的容量结论**（推导参数本身是 ASSUMPTION）：

```text
50,000 行/日 ÷ 8 小时 ≈ 1.7 行/秒（平均）
1.7 × 5（峰值倍数） ≈ 9 行/秒（峰值）
每行写入 ≈ 台账更新 1 + 流水插入 1 + 单据行更新 1 + outbox 1 + 审计 1 ≈ 5 次写
≈ 45 写/秒（峰值）
```

对架构的影响（逐项）：

| 维度 | 影响 | 结论 |
|---|---|---|
| Architecture | 45 写/秒 与单台 PostgreSQL 的量级差约两个数量级 | **模块化单体 + 单库足够**，微服务无容量依据 |
| Storage | 库存桶 2,000,000 行、流水按年增长 | 单表可承载；流水表按 `posted_at` 预留分区方案，MVP 不分区 |
| Cache | 无明确读热点超出索引能力 | **MVP 不引入 Redis**；L1 Caffeine 仅用于主数据与权限快照 |
| MQ | 无跨进程异步协作 | **MVP 不引入 MQ**；事务性 Outbox + 进程内可靠投递 |
| Observability | 排障靠单据号与 traceId 关联 | 结构化日志 + traceId + health 是底线，metrics 延后 |
| Deployment | 单可部署单元 | `docker compose` 单应用 + 复用 `dev-infra` PostgreSQL |

## 不做的定位声明

- **不是 WMS**：库位拣货、波次、设备、序列号执行归 `wms-platform` 一类系统。ERP 侧只保留「库存账 + 可选库位维度 + 集成端口」。见 [SYSTEM_BOUNDARY.md](SYSTEM_BOUNDARY.md) 与 `Q-03`。
- **不是总账财务软件**：MVP 只做应收/应付/收付款/核销，凭证、总账、会计期间、报表体系属于 `FUTURE_SCOPE`。
- **不是 CRM / MES / HR**。
