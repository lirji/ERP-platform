# Technology Selection — erp-platform

> Owner: `backend-architecture-design`
> 原则：先写任务/约束/NFR，不把方案伪装成需求；每个新增主要组件必须通过 **Complexity Budget**；「技术先进」不是理由。
> 提示词第十五章给了建议栈并要求「必须通过选型流程，而不是直接照搬」——下表有 **4 处与建议栈不同**，均写明理由。

## 1. 主要选型

| 决策 | 当前/约束 | 候选 | 选定 | 选择理由 | 否决原因 | 版本/兼容 | 引入阶段 |
|---|---|---|---|---|---|---|---|
| 语言 / 运行时 | 本机 `java 21.0.11`（已验证 `java -version`） | Java 21 / Java 17 | **Java 21** | 本机与 Workspace 全部项目一致；虚拟线程可用于 Outbox 投递 | Java 17：无理由降级 | 21.0.11 LTS（已验证） | Phase 0 |
| 构建 | 本机 `Maven 3.9.12`（已验证 `mvn -v`） | Maven / Gradle | **Maven** | 与 `oa-platform` / `workflow-platform` / `wms-platform` 一致，多模块 reactor | Gradle：团队无收益，增加认知成本 | 3.9.12（已验证） | Phase 0 |
| 服务框架 | — | Spring Boot 3.3.x / 3.4.x | **Spring Boot 3.3.5** | 与 `workflow-platform`（README 明示 3.3.5）、`oa-platform` 一致，便于未来共用 SDK | 3.4.x：无必需特性，且与既有项目版本漂移 | ⚠️ **待 Phase 0 核验**：需对照官方支持矩阵确认 3.3.5 的维护状态；本规划未核验，不得写成已确认 | Phase 0 |
| API 风格 | — | REST / RPC / GraphQL | **REST（JSON）** | 消费方只有自家控制台；REST 的调试与文档成本最低 | RPC：无跨服务调用；GraphQL：ERP 列表查询以固定报表为主，收益低于 schema 治理成本 | OpenAPI 3.1 | Phase 0 |
| **主数据存储** | `dev-infra` 同时提供 MySQL 8.4 与 PostgreSQL 16 | **PostgreSQL 16** / MySQL 8.4 | **PostgreSQL 16** | ① **部分唯一索引**（如"未取消的单据号唯一"）原生支持，MySQL 8 需变通；② `NUMERIC` 精确十进制符合金额/数量要求；③ `JSONB` 承载审计前后值与单据扩展字段；④ `text_pattern_ops` 支持 `org_path LIKE '/1/23/%'` 数据权限前缀下推；⑤ 与 `workflow-platform` 同栈，Phase 8 接入成本更低 | MySQL 8.4：无部分唯一索引、JSON 索引能力弱、表达式索引受限。**它不是错误选择，只是本项目的约束更吃 PG 的特性** | `postgres:16-alpine`（`dev-infra/compose.yaml:37`） | Phase 0 · **见 `Q-01`，用户可推翻，Phase 0 内改动成本极低** |
| 持久层 | 全局开发规范 §7 要求 SQL 集中在 Mapper | MyBatis / MyBatis-Plus / JPA | **MyBatis-Plus** | 复杂 SQL 写在可审查的 XML；单表 CRUD 用 Plus 减少样板；符合"业务层不得拼接 SQL" | JPA：ERP 的复杂查询与批量更新用 JPQL 表达困难，且隐式 SQL 不利审查 | 与 Spring Boot 3.3.x 兼容版本待 Phase 0 核验 | Phase 0 |
| 迁移工具 | — | Flyway / Liquibase | **Flyway** | SQL 原生，便于写表/字段注释（全局开发规范 §一）；每模块独立目录 | Liquibase：XML/YAML 抽象让注释与 PG 特性表达变绕 | 待核验 | Phase 0 |
| **缓存** | — | 无 / L1 / L2 / L1+L2 | **L1（Caffeine）仅用于主数据与权限快照** | 读热点仅限主数据与权限；单进程内 L1 足够，TTL ≤ 60s | **Redis 被否决**：无跨实例失效需求（单实例）。默认 L1+L2 是典型过度设计 | Caffeine（随 Spring 生态） | Phase 1 |
| **搜索** | — | 无 / DB 索引 / ES | **无（用 PG 索引）** | 查询是结构化筛选 + 分页，复合索引可覆盖 | **ES 被否决**：`dev-infra` 共享栈无 ES 实例，引入需新建并运维；当前无全文/相关性需求 | — | 触发后再议 |
| **消息** | — | 无 / 现有 Kafka / 现有 RabbitMQ | **MVP 无 MQ；用同库事务性 Outbox + 进程内投递** | 单进程内无跨进程异步协作；Outbox 已满足"提交后可靠触发" | **Kafka/RabbitMQ 被推迟**：为 MQ 而 MQ（提示词第十八章）。引入时**复用 dev-infra 实例**（Kafka 3.8.0 / RabbitMQ 3.13）而非新建 | — | Phase 8（接 workflow-platform）或 Stage 3（抽进程） |
| 调度 | — | 无 / `@Scheduled` / XXL-JOB | **`@Scheduled` + 数据库锁** | 只有 Outbox 投递与预占过期回收两个任务 | XXL-JOB：单实例下纯增运维成本 | — | Phase 1 |
| 认证 / IdP | `auth-platform` 已在运行 | 自建 / Casdoor(auth-platform) / Keycloak | **Casdoor via `auth-platform`** | `REUSE_EXISTING`；已有 SDK 与接入文档 | 自建：Generic 能力自研是浪费；Keycloak：组织内已有 Casdoor | 依 `auth-platform` 现状（待 Phase 1 核验 SDK 版本） | Phase 1 |
| 工作流引擎 | `workflow-platform` 已在运行（Flowable 7.1.0） | 内置顺序审批 / workflow-platform / 自建 BPMN | **MVP 内置；Phase 8 接 workflow-platform** | MVP 审批需求是顺序多级 + 驳回撤回抄送，内置几百行即可；接引擎会带来 Kafka + outbox/inbox + 跨系统最终一致 | 自建 BPMN：明确禁止 | Flowable 7.1.0（对方版本，`workflow-platform/README.md`） | Phase 8 |
| 可观测 | — | logs+health / +metrics / +traces | **MVP：结构化日志 + traceId + health** | NFR-05 底线；单进程排障靠单据号+traceId 即可 | metrics/traces 推迟到多实例或 SLA 明确时 | Micrometer 预留 | Phase 0（日志/health）· Phase 10（metrics） |
| 配置 | `dev-infra` 有 Nacos 2.4.3 | env / Nacos / 数据库配置表 | **env（技术配置）+ 数据库表（业务参数 CAP-P11）** | 两类配置职责不同：技术配置随部署走 env；业务参数（超收比例等）要业务人员可维护且有审计 | **Nacos 被否决**：单实例单部署，引入配置中心解决的是不存在的问题；且同一配置键不得有两个权威来源 | — | Phase 0 / Phase 1 |
| 限流 | — | 无 / 应用入口 / 网关 | **无** | 内部系统、无公网入口、无租户公平性问题 | 触发条件：开放外网或出现租户挤占 | — | 触发后再议 |
| 数据库运行时 | — | — | 隔离级别 **READ COMMITTED**（PG 默认）；HikariCP 上限显式配置；语句超时 30s（报表 60s） | 库存并发靠显式行锁 + 乐观锁，不靠 REPEATABLE READ | SERIALIZABLE：吞吐代价高且会带来大量重试 | — | Phase 0 |
| 数据保留 | — | 无 / TTL / archive | **MVP 无额外策略**，流水表预留按 `posted_at` 分区方案 | 未给保留年限（`Q-08`/NFR） | 不编造统一保留期限 | — | 触发后再议 |
| 对象存储 | `dev-infra` MinIO | 本地磁盘 / MinIO | **MinIO** | 附件需跨实例可见；`REUSE_EXISTING` | 本地磁盘：多实例不共享 | `dev-infra` 现有版本 | Phase 2 |
| 前端 | — | Vue3+TS / React18+TS | **Vue 3 + TypeScript + Vite + Element Plus** | 提示词建议栈；ERP 后台表单/表格密集，Element Plus 的表格与表单组件成熟 | React+antd5：`oa-platform` 用它，但本项目无复用代码，按提示词建议即可 | 待 Phase 9 核验具体版本 | 与后端切片并行 |

## 2. 与提示词建议栈的 4 处差异

| 建议 | 本方案 | 理由 |
|---|---|---|
| MySQL 8 | **PostgreSQL 16** | 见上表「主数据存储」。已作为 `Q-01` 交用户确认，Phase 0 内可低成本改回 |
| RocketMQ / Kafka | **MVP 不引入** | 无跨进程异步协作；Outbox 足够。提示词第十八章「不要所有操作都 MQ 化」、第二十五章「禁止加入当前项目没有使用的无关中间件」 |
| Elasticsearch | **不引入** | 无全文/相关性需求；共享栈无 ES 实例 |
| Redis | **不引入（改用 L1 Caffeine）** | 单实例无跨实例失效需求；避免默认 L1+L2 |

## 3. Complexity Budget（每个新增主要组件必须回答）

| 组件 | 解决什么**已存在**的问题 | 简单方案为何不够 | 增加的运维成本 | 判定 |
|---|---|---|---|---|
| PostgreSQL 16 | 全部权威数据 | 无更简方案 | 复用 dev-infra 共享实例，独立 database | ✅ 通过 |
| MinIO | 附件跨实例可见 | 本地磁盘多实例不共享 | 复用 dev-infra，独立 bucket + key | ✅ 通过（Phase 2） |
| Caffeine L1 | 主数据与权限的重复读 | 每次查库会让判权进入热路径 | 无额外进程；需定义 TTL 与失效边界 | ✅ 通过 |
| Redis | — | **问题不存在**（单实例） | 新增依赖与失效传播复杂度 | ❌ 否决 |
| Kafka / RabbitMQ | — | **问题不存在**（单进程） | Topic/vhost 治理、消费位点、DLQ | ❌ 推迟 |
| Elasticsearch | — | **问题不存在** | 新增实例、mapping 与索引生命周期治理 | ❌ 否决 |
| Nacos | — | **问题不存在**（单部署） | 配置双权威来源风险 | ❌ 否决 |
| XXL-JOB | — | **问题不存在**（2 个任务、单实例） | 调度中心部署与注册 | ❌ 否决 |
| Seata / TCC | — | **问题不存在**（单库本地事务） | TC 部署、Fence 表、恢复协议 | ❌ 否决 |

## 4. Build / Buy / Integrate

| 能力 | decision | reason | alternatives | tradeoffs（含退出成本） | constraints | risk | 阶段 |
|---|---|---|---|---|---|---|---|
| 身份认证 / SSO（CAP-G01） | `REUSE_EXISTING` | 组织内 `auth-platform` 已运行 Casdoor + SDK | 自建 / Keycloak | 绑定 Casdoor 的 OIDC 行为；退出成本低（ERP 只依赖标准 OIDC，换 IdP 改配置） | 必须 ACL 隔离其 org/group 模型 | R-06 | Phase 1 |
| 授权 RBAC + DataScope（CAP-P03/P04） | `BUILD` | 结构化批量判权 + 数据权限 SQL 下推，是 ERP 的内在能力 | SpiceDB(auth-platform) | 自研判权引擎的维护成本；换成远程 ReBAC 的退出成本高 | 判权必须在服务端且可下推 SQL | R-06 | Phase 1 |
| 审批引擎（CAP-P10/I01） | `BUILD`（最简）→ `INTEGRATE`（触发时） | MVP 需求是顺序审批；复杂编排时用已有 `workflow-platform` | 自建 BPMN（禁止） | 内置引擎在会签/并行场景会撞墙；切换需实现 outbox/inbox | Port + Adapter 结构必须先在（Phase 1） | R-07 | Phase 1 / Phase 8 |
| 流程引擎本体 | `REUSE_EXISTING`（不自建） | Flowable 已由 `workflow-platform` 承载 | — | — | — | R-07 | Phase 8 |
| 文件存储（CAP-G02） | `REUSE_EXISTING` | dev-infra MinIO | 本地磁盘 / 云 OSS | 退出成本低（S3 协议） | 独立 bucket + access key | — | Phase 2 |
| 通知（CAP-G03） | `DEFER` | 暂无真实通知场景 | — | — | 触发：审批待办需要外部推送 | — | 触发后 |
| BI / 报表平台（CAP-A06） | `DEFER` | 先用自建读模型验证口径 | Metabase / Superset | — | 触发：非技术人员需要自助分析 | R-08 | 触发后 |
| 监控 / 日志平台 | `DEFER` | MVP 用结构化日志 + health | dev-infra 可观测栈 | — | 触发：多实例或 SLA 明确 | — | Phase 10 |
| 仓储执行（CAP-I03） | `DEFER` | ERP 自管库存账已满足 MVP | `wms-platform` | 两套库存权威的风险 | 端口先在 | R-12 | 见 `Q-03` |
| 电子发票 / 支付 / 银行（CAP-I05/I06） | `DEFER` | 无合规与量级依据 | — | — | — | — | FUTURE |

## 5. 选型完成条件自检

- [x] 每个新增主要组件都有真实问题来源（第 3 节逐项回答）
- [x] 不适用项明确写 `无 / 不引入`，并写了触发条件
- [x] Decision 可追溯到 Fact / Constraint / Assumption
- [ ] **版本精确核验未完成**：Spring Boot 3.3.5 的当前维护状态、MyBatis-Plus 与 Spring Boot 3.3.x 的兼容版本、Flyway 与 PG16 的兼容版本 —— **Phase 0 的第一个 ChangeSet 必须核验并写入 ADR，本规划不得声称已核验**

## P1-OIDC 补充（2026-09-23）

复用 Boot 管理的 spring-boot-starter-oauth2-resource-server（Spring Security 6.3.9），浏览器使用同源 WebJar `org.webjars.npm:oidc-client-ts:3.2.1`。选择 WebJar 是为了让最小回调页随唯一 JVM Artifact 构建交付，无新增 Node 服务或 CDN 依赖；后续完整前端可以沿用 OIDC 契约。许可、已知公告核查及未解决维护风险见 [OIDC](../../security/OIDC.md)。
