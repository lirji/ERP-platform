# ERP Non-Functional Requirements

> Owner: `project-bootstrap` · Phase 1
> 状态取值：`CONFIRMED`（用户明确给出）· `ASSUMED`（为继续规划采用）· `RECOMMENDED`（建议值，待确认）。
> **用户未给出任何数值型 SLA**，因此下表除标注 CONFIRMED 的定性要求外，数值全部是 `RECOMMENDED` / `ASSUMED`，不得当作需求执行。

| # | 维度 | 指标 / 要求 | 状态 | 依据 | 对架构的影响 |
|---|---|---|---|---|---|
| NFR-01 | Performance | 单据保存 P95 < 500ms；列表查询 P95 < 1s；库存扣减 P99 < 200ms | `RECOMMENDED` | 由 A-04/A-06 推出峰值 ≈45 写/秒，目标留 100× 余量 | 单库单实例即可；**不引入缓存/MQ** |
| NFR-02 | Availability | 工作时间 99.5%（非 7×24 交易系统） | `ASSUMED` | 内部经营系统，夜间可停机维护 | 单实例起步；多副本推迟到 Phase 10 |
| NFR-03 | Scalability | 单实例起步；水平扩展的前置条件是消除实例本地状态（L1 缓存失效、定时任务抢占） | `RECOMMENDED` | A-03/A-04 | Phase 0 就约束：**不得把状态写进单实例内存作为权威** |
| NFR-04 | Security | 服务端强制判权；租户隔离；数据权限在查询层统一施加；敏感字段脱敏；关键操作审计 | `CONFIRMED` | 提示词第十二 / 二十七章 | 见 `architecture/SECURITY_ARCHITECTURE.md` |
| NFR-05 | Observability | 结构化日志 + `traceId`；日志必须可关联 `tenantId/userId/businessType/businessId/documentNo/traceId`；health 端点 | `CONFIRMED` | 提示词第二十六章 | Phase 0 基线即落地；metrics/traces 推迟 |
| NFR-06 | Auditability | 核心单据的创建、状态迁移、金额与数量变更全量留痕（前值/后值/IP/终端） | `CONFIRMED` | 提示词第十三章 | **审计必须进平台内核（Phase 1），不能后补** |
| NFR-07 | Recoverability | RPO ≤ 15 分钟；RTO ≤ 4 小时 | `ASSUMED` | 内部系统常见值 | 依赖 PG 备份策略；**未做恢复演练，不得声称达标** |
| NFR-08 | Consistency | 单据过账与库存变动**强一致**；AR/AP 生成最终一致，滞后 ≤ 60s 并可对账 | `RECOMMENDED` | 见 `architecture/CONSISTENCY_MODEL.md` | 模块化单体 + Outbox |
| NFR-09 | Maintainability | 模块依赖方向由 ArchUnit 强制；跨模块禁止访问对方表 | `CONFIRMED` | 提示词第三十章禁止事项 5/6 | Phase 0 引入架构测试 |
| NFR-10 | Extensibility | 新增业务域只需新增模块 + 注册状态机 + 订阅事件，不改平台内核 | `CONFIRMED` | 提示词第一章「模块化扩展」 | 平台内核对业务模块单向依赖 |
| NFR-11 | Deployability | `docker compose up -d` 后本地可启动；只包含真实用到的中间件 | `CONFIRMED` | 提示词第二十五章 | 复用 `dev-infra` PostgreSQL，**不引入未使用的 MQ/ES** |
| NFR-12 | Testability | 单元 / 领域 / 仓储 / 集成 / API / 架构 / E2E 七层；真实数据库语义用集成测试验证，不用纯 Mock 证明事务正确 | `CONFIRMED` | 提示词第二十三章 + 全局开发规范 §13 | Testcontainers PostgreSQL |
| NFR-13 | Operability | 慢查询、异常、任务执行可观测；业务参数可在不发版的情况下调整 | `RECOMMENDED` | — | CAP-P11 |

## 未给出即不编造

以下指标**用户没有给出，本规划也不虚构**，进入 `OPEN_QUESTIONS.md`：并发用户数上限、日单量、SKU 规模、可接受停机窗口、数据保留年限、合规/数据驻留要求。
`NFR-07` 的 RPO/RTO 在完成一次真实恢复演练之前，只能标 `ASSUMED`，不得在任何 Gate 中写成 PASS。
