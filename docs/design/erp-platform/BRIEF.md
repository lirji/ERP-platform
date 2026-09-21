# BRIEF — erp-platform

> Owner: `public-engineering-workflow:discover` · 引用 `project-bootstrap` Phase 1 产物，**不复制**。

## 目标

见 [`SYSTEM_VISION.md`](../../../.engineering/bootstrap/SYSTEM_VISION.md)。一句话：让一家企业在同一套数据上跑通「采购→入库→库存→销售→出库→应收应付→结算」，且每一笔库存与金额变化都能追溯到来源单据。

## 主作业（谁做什么）

| Actor | 主作业 | 链路 |
|---|---|---|
| 采购员 / 采购主管 | 提申请、下采购订单、跟踪收货、处理超收少收 | BL-1 |
| 仓管员 | 收货入库、拣货出库、调拨、盘点、报损报溢 | BL-1/2/3 |
| 销售员 / 销售主管 | 报价、接单、跟踪发货、处理退货 | BL-2 |
| 应付会计 / 应收会计 | 核对应付应收、发起收付款、核销 | BL-1/2 |
| 出纳 | 执行付款与收款 | BL-1/2 |
| 系统管理员 | 组织、用户、角色、数据权限、主数据、业务参数 | BL-4/5 |
| 经营者 | 看库存、看往来、看经营概览 | BL-6 |

能力清单见 [`CAPABILITY_MAP.md`](../../../.engineering/bootstrap/CAPABILITY_MAP.md)；边界见 [`SYSTEM_BOUNDARY.md`](../../../.engineering/bootstrap/SYSTEM_BOUNDARY.md)。

## 崩溃、重试、并发写之后仍必须成立的不变量

这些是架构设计的真正输入，优先级高于任何功能清单：

| # | 不变量 | 受影响链路 |
|---|---|---|
| INV-01 | 库存余额 = 该桶全部流水的代数和（可随时对账） | BL-1/2/3 |
| INV-02 | `onHand ≥ 0`；`reserved + locked ≤ onHand` | BL-1/2/3 |
| INV-03 | 任何余额变化都有且只有一条来源单据行，可双向追溯 | 全部 |
| INV-04 | 同一来源单据行重复过账不产生第二次库存效果（幂等） | BL-1/2/3 |
| INV-05 | 累计收货量 ≤ 订单量 ×(1+超收比例)；累计发货量 ≤ 订单量 | BL-1/2 |
| INV-06 | 每张入库/出库单最多生成一张应付/应收（`(来源类型,来源ID)` 唯一） | BL-1/2 |
| INV-07 | 累计核销金额 ≤ 单据金额；累计付款 ≤ 应付金额 | BL-1/2 |
| INV-08 | 企业维度数量守恒：期末仓内合计 + 在途 = 期初 + 外部收 − 外部发 ± 有凭据调整 | BL-3 |
| INV-09 | 任何查询都带租户与数据范围边界，不存在"忘了加 tenant_id"的查询路径 | 全部 |
| INV-10 | 非法状态迁移被拒绝且留痕；并发迁移只有一个成功 | 全部 |

## 已知容量、SLA、隔离、合规与恢复目标

见 [`NFR.md`](../../../.engineering/bootstrap/NFR.md)。**用户未给出任何数值**；表中数值均为 `ASSUMED` / `RECOMMENDED`。

## 只是 Assumption 的部分

见 [`ASSUMPTIONS.md`](../../../.engineering/bootstrap/ASSUMPTIONS.md)（A-00..A-14）与 [`OPEN_QUESTIONS.md`](../../../.engineering/bootstrap/OPEN_QUESTIONS.md)（Q-01..Q-10，无 BLOCKING）。

## 约束（USER_CONSTRAINT）

1. 不得一上来 CRUD；不得一个超大 `erp-service` 模块；不得 Controller 直接操作 Mapper。
2. 不得为使用微服务而微服务、为使用 MQ 而 MQ、为 DDD 而过度抽象。
3. 库存不得用单一 `stock` 字段；状态不得用 `if(status==1)` 散落控制。
4. 必须有幂等、状态机、业务审计、单据关系。
5. 建表必须写表注释与字段注释（全局开发规范 §一）；后端代码写中文注释说明**为什么**（§二）；Mock 数据入库不写死页面（§三）；Docker 公共组件优先 `dev-infra`（§四）。
6. 未过 Gate 不得进入下一阶段。
