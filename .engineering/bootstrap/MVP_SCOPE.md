# MVP Scope

> Owner: `project-bootstrap`
> **MVP 的定义**：能让真实用户在生产中走通**至少一条完整业务闭环**的最小能力集合，加上它的前置依赖。
> 分层依据（`basis`）只能取五种之一，每条写具体理由，**不打主观分**。

## MVP 判定

本 ERP 的 MVP = **一条完整的采购到付款闭环 + 一条完整的订单到收款闭环**。

为什么是两条而不是一条：库存既要有"进"也要有"出"才能验证 INV-01（余额=流水累计）与 INV-08（数量守恒）；只做采购闭环时库存只增不减、应收永远为空，账实一致性无法被真实验证。这是 `BUSINESS_LOOP` 依据，不是"功能越多越好"。

## MUST_HAVE

| 能力 | basis | 理由 |
|---|---|---|
| CAP-P01 租户与组织建模 | `TECHNICAL_DEPENDENCY` | 所有业务表的 `tenant_id` / `company_id` / `org_path` 都来自它；后补等于改全部表 |
| CAP-P02 用户与角色 | `TECHNICAL_DEPENDENCY` | 判权的载体 |
| CAP-P03 功能权限 | `TECHNICAL_DEPENDENCY` | API 权限是唯一安全边界，不能等到上线前再加 |
| CAP-P04 数据权限 | `TECHNICAL_DEPENDENCY` | 要下推成 SQL 条件；后补需要重写所有查询 |
| CAP-P05 主数据中心 | `PRECEDENCE` | 没有 SKU / 供应商 / 客户 / 仓库，任何单据都建不出来 |
| CAP-P06 编号中心 | `TECHNICAL_DEPENDENCY` | 每张单据都要单据号 |
| CAP-P07 统一单据模型与状态机 | `TECHNICAL_DEPENDENCY` | 采购/销售/库存单据都依赖同一机制；后补会让已写的状态判断散落成 if/else |
| CAP-P08 单据关系图 | `BUSINESS_LOOP` | 闭环的定义就是"可追溯"，这是本 ERP 的价值主张之一 |
| CAP-P09 业务审计 | `TECHNICAL_DEPENDENCY` | 审计要拦截写入路径；**后补代价极高**（这是把它从提示词 Phase 11 提前到 Phase 1 的理由） |
| CAP-P10 审批中心（内置顺序审批） | `BUSINESS_DEPENDENCY` | 采购订单与销售订单的 `APPROVING` 态需要它；用最简实现而非 BPMN 引擎 |
| CAP-P11 业务参数配置（最小集） | `BUSINESS_DEPENDENCY` | 超收比例、负库存策略是 CAP-C05 的守卫条件 |
| CAP-C01 库存余额台账 | `BUSINESS_LOOP` | 闭环核心 |
| CAP-C02 库存流水 | `BUSINESS_LOOP` | 没有它就没有可追溯性与对账能力 |
| CAP-C03 库存预占与释放 | `BUSINESS_LOOP` | 销售闭环的"库存占用"环节 |
| CAP-C04 批次维度 | `PRECEDENCE` | 批次是**台账主键的一部分**；后加维度＝全量数据迁移。MVP 只做"维度 + 手工指批"，FEFO/效期预警推迟 |
| CAP-C05 采购订单履约 | `BUSINESS_LOOP` | P2P 闭环 |
| CAP-C06 销售订单履约 | `BUSINESS_LOOP` | O2C 闭环 |
| CAP-C07 应付管理与付款 | `BUSINESS_LOOP` | P2P 闭环终点 |
| CAP-C08 应收管理与收款 | `BUSINESS_LOOP` | O2C 闭环终点 |
| CAP-C09 核销 | `BUSINESS_LOOP` | 不核销则应收应付永远挂账，闭环不闭 |
| CAP-G01 身份认证与 SSO | `TECHNICAL_DEPENDENCY` | 没有登录就没有 `AccessContext` |

## SHOULD_HAVE

| 能力 | basis | 理由 |
|---|---|---|
| CAP-S09 采购申请 | `BUSINESS_DEPENDENCY` | 常见但非必需（可直接下采购订单）；企业内控通常要求，故紧随 MVP |
| CAP-S01 采购退货 / CAP-S02 销售退货 | `BUSINESS_LOOP` | 逆向链路；MVP 闭环不含它也能跑通，但真实业务很快会用到 |
| CAP-S03 仓间调拨 | `BUSINESS_DEPENDENCY` | 多仓是需求（提示词第一章），调拨是多仓的基本作业 |
| CAP-S04 库存盘点 / CAP-S05 报损报溢 | `DELIVERY_RISK` | 没有盘点则账实不符时**没有合法的纠正手段**，只能改库——那会破坏 INV-01 |
| CAP-S06 客户信用额度 | `BUSINESS_DEPENDENCY` | 销售订单审批的守卫条件之一 |
| CAP-S10 移动加权平均计价 | `PRECEDENCE` | 成本口径影响流水字段；维度先留，算法随后 |
| CAP-G02 文件存储 / CAP-G04 导入导出 | `DELIVERY_RISK` | 主数据没有批量导入时，初始化数据的人工成本会拖慢验收 |
| CAP-A01 库存余额与流水报表 | `DELIVERY_RISK` | 对账要用；缺它则 INV-01 只能靠测试验证，运营看不见 |

## COULD_HAVE

| 能力 | basis | 理由 |
|---|---|---|
| CAP-S07 供应商对账 / CAP-S08 销售报价 | `BUSINESS_DEPENDENCY` | 依赖已有的往来账与销售订单，属于增强 |
| CAP-I01 审批引擎集成（workflow-platform） | `DELIVERY_RISK` | 推迟它能显著降低 MVP 交付风险（避免引入 Kafka + 跨系统最终一致） |
| CAP-A02..A05 统计与账龄 | `PRECEDENCE` | 需要先有真实数据才能谈口径 |
| CAP-G03 消息通知 | `BUSINESS_DEPENDENCY` | 审批待办的体验增强 |

## LATER

| 能力 | basis | 理由 |
|---|---|---|
| CAP-A06 经营概览 | `PRECEDENCE` | 依赖全部单项报表口径先稳定 |
| CAP-I03 WMS 集成 | `BUSINESS_DEPENDENCY` | 见 `Q-03`，触发条件未出现 |
| CAP-I04/I05/I06 电商 / 银行 / 税务 | `BUSINESS_DEPENDENCY` | 无真实对接对象 |
| 总账 / 凭证 / 会计期间 / 多币种折算 | `BUSINESS_DEPENDENCY` | 见 `SYSTEM_BOUNDARY.md` FUTURE_SCOPE |

## mvpClass 映射（交给 Planner，Planner 只做视野治理，不重新判定）

| 本文件分层 | `mvpClass` |
|---|---|
| MUST_HAVE（及其前置依赖） | `MVP` |
| SHOULD_HAVE · COULD_HAVE | `POST_MVP` |
| LATER · FUTURE_SCOPE | `DEFERRED` |
| OUT_OF_SCOPE | `OUT_OF_SCOPE` |

**首个执行视野只放 `MVP`。**
