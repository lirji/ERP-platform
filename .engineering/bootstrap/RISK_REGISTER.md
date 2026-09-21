# Risk Register

> Owner: `project-bootstrap`
> 每条：`ID` · 类别 · Risk · Trigger · Impact · Mitigation · **Verification（怎样证明缓解有效）** · 负责阶段。
> 覆盖 11 个类别；`Over-Engineering` 必写。

| ID | 类别 | Risk | Trigger | Impact | Mitigation | Verification | 阶段 |
|---|---|---|---|---|---|---|---|
| R-01 | Requirement | 能力地图源自提示词模板而非真实企业干系人，可能与实际业务不符 | 首次与真实用户评审 MVP | Core 上下文返工 | MVP 只做 P2P/O2C 骨干；Supporting 能力等到 P7 再按真实反馈展开 | 与干系人逐条走查 CAP-C01..C09，记录确认/否决 | P0 前 |
| R-02 | Domain | 「统一单据模型」被泛化成万能单据表，丢失各聚合的领域约束 | 出现名为 `business_document` 的通用表承载所有单据数据 | 领域规则退化为配置，不变量无处安放 | **机制统一、状态集合与表结构各自拥有**；`erp-kernel` 只提供基类与状态机机制，不提供通用单据表 | 架构测试：不存在被 3 个以上模块共同写入的业务表 | P1 |
| R-03 | Architecture | 为"企业级"过早拆服务或上中间件 | 出现第二个可部署单元，或新增 MQ/ES/Redis | 一致性从本地事务退化为分布式问题，运维成本翻倍 | 新增可部署单元必须先有 `ARCHITECTURE_EVOLUTION.md` 的可观察触发条件；新增组件必须过 Complexity Budget | Gate G-03 逐期检查；compose 服务清单与 `TECH_SELECTION.md` 逐项比对 | 全程 |
| R-04 | Data | 库存桶主键维度选错（缺 batch 或 location），后期迁移代价极高 | P3 之后需要新增台账维度 | 全量数据迁移 + 历史重算 | MVP 就把 `batch` 与 `location` 放进唯一键，用非空哨兵 `NO_BATCH` / `NO_LOCATION` 占位 | P3 Gate：查看 `inv_inventory_balance` 唯一索引定义包含六个维度 | P3 |
| R-05 | Consistency | 单据过账与库存变动不在同一事务，导致账实不符 | 出现"先更新单据，再调库存"的两段代码 | INV-01 被破坏，且无法自动发现 | 过账走单一入口 `StockPostingService`，与单据更新同事务；每日对账脚本 | INV-01 集成测试 + 对账脚本在 CI 中运行 | P3–P6 |
| R-06 | Security | 数据权限只在 Controller 判定，被其他入口绕过 | 新增一个直接调用 Mapper 的查询路径 | 越权读取其他部门/公司数据 | 数据权限在**持久层拦截器**统一施加；ArchUnit 禁止 Controller→Mapper | P1 Gate：断言生成的 SQL 含 `tenant_id` 与 `org_path` 条件（断言 SQL，不只断言结果） | P1 |
| R-07 | Integration | 接入 `workflow-platform` 引入 Kafka + 跨系统最终一致，拖累 MVP | 在 P6 之前启用 `WorkflowPlatformApprovalAdapter` | MVP 交付延期；新增 outbox/inbox/DLQ/对账四套机制 | MVP 用内置顺序审批（A-12）；接入推迟到 P8 且保留内置适配器作为回落 | P8 Gate：切换适配器后 P4/P5 审批 E2E 仍绿 | P8 |
| R-08 | Performance | 报表直接 JOIN 几十张业务表，拖垮 OLTP | 报表查询 P95 > 3s 或月末写入劣化 | 业务不可用 | 报表走 `rpt_*` 读模型；报表 SQL 不出现业务表 | P9 Gate：报表 SQL 扫描无业务表前缀；P95 达标 | P9 |
| R-09 | Operational | 与 `dev-infra` 共享 PostgreSQL 实例，隔离不当影响其他项目 | 使用了共享账号或未独立 database | 其他项目受影响；`docker compose down -v` 误删共享卷 | 按 `dev-infra` 约定使用独立 database + owner + 应用账号；**禁止 `docker compose down -v` 作为日常停止方式** | P0 Gate：连接串审查 + 运维文档写明禁令 | P0 |
| R-10 | Delivery | 一次推进 12 个阶段，反馈周期过长 | 连续两期没有可演示的闭环 | 方向偏差发现太晚 | MVP 在 P6 即闭合；P4/P5 可并行；每期 Exit Criteria 都是可演示的行为 | 每期结束有一次可运行演示 | 全程 |
| R-11 | **Over-Engineering** | 见下方专表 | — | — | — | — | 全程 |
| R-12 | Domain | 与 `wms-platform` 能力重叠，出现两个库存权威 | 在 ERP 里实现波次/拣货路径/序列号逐件追踪 | 重复投入；两套库存数据对不上 | `SYSTEM_BOUNDARY.md` §5 划清：ERP 只做库存账，物理执行留 Port；MVP 不集成 | 每期 Gate G-03：检查是否出现 WMS 语义（波次、库位推荐、设备、序列号执行） | 全程 |
| R-13 | Data | 主数据被修改后历史单据金额/名称变化，破坏可审计性 | 单据行持有主数据外键而非快照 | 审计不可解释；对账失败 | `MasterDataRef` 快照嵌入单据行，关键字段引用后冻结 | P2 Gate：改名后历史单据显示快照值 | P2 |
| R-14 | Security | 审计日志被应用账号篡改或删除 | 应用账号拥有审计表的 UPDATE/DELETE 权限 | 审计失去证明力 | 审计表只追加；数据库级 GRANT 收回 UPDATE/DELETE | P1 Gate：用应用账号尝试 UPDATE 审计表应失败 | P1 |
| R-15 | Delivery | 版本兼容未核验就开工（Spring Boot 3.3.5 / MyBatis-Plus / Flyway + PG16） | P0 直接开始写代码 | 中途升级依赖，返工 | P0 第一个 ChangeSet 就核验并写 ADR | P0 Gate：ADR 中有核验结论与来源 | P0 |

## Over-Engineering Risk 专表（R-11）

本规划中**最可能被过度设计的 7 个点**，以及阻止它的具体检查：

| 点 | 过度设计的样子 | 阻止它的检查 | 在哪一期检查 |
|---|---|---|---|
| 1. 统一单据模型 | 一张 `business_document` 万能表 + 一张 `document_item` 万能行表，字段全是 JSON | 架构测试：不存在被 3 个以上模块共同写入的业务表 | P1 |
| 2. DDD 战术模式 | 给 `erp-iam` / `erp-masterdata` / `erp-document` 也画聚合、加仓储端口、加领域事件 | `DOMAIN_MAP.md` §3 已明文声明这些上下文**不用**战术模式；代码审查比对 | P1–P2 |
| 3. 缓存 | 默认 L1+L2（Caffeine + Redis） | `TECH_SELECTION.md` 已否决 Redis；compose 中出现 Redis 即视为违规 | 全程 |
| 4. 消息 | 所有跨模块交互都走 MQ | MVP 无 MQ；跨模块同步调用 vs 事件的划分见 `ARCHITECTURE_OPTIONS.md` DEC-03 | 全程 |
| 5. 分布式事务 | 引入 Seata/TCC"以防将来拆服务" | 单库本地事务已足够；`CONSISTENCY_MODEL.md` §2 明确否决 | 全程 |
| 6. 微服务 | 按 9 个上下文拆 9 个进程 | 只有 1 个可部署单元；新增需触发条件 | 全程 |
| 7. 端口与适配器 | 给每个上下文都加一层端口 | 只有 Core 上下文（BC-3/4/5/6）用六边形，Supporting 用分层 | P1–P6 |

> 反向风险也要记：**过度简化**。把库存做成 `sku_id + stock`、把状态做成 `int status` + if/else、跳过幂等与审计——这些在提示词第三十章被明确禁止，且比过度设计更难补救（会污染历史数据）。本规划把 CAP-C04（批次维度）、CAP-P09（审计）、CAP-P07（状态机）都放进 MVP，正是为此。

---

## P0 新增风险（2026-09-21，Phase 0 核验产出）

| ID | 风险 | 概率 | 影响 | 当前处置 | 触发即行动 |
|---|---|---|---|---|---|
| `RISK-VER-01` | **Spring Boot 3.3.x 很可能已退出 OSS 维护**。主线已至 4.1.x，3.3.11 很可能是该线最后一个公开补丁，后续安全修复不再进入 3.3.x | 高 | 中 | **ACCEPTED（2026-09-21 用户决策）**：维持 3.3.11，不做进一步升级，不保留强制复核点。理由是与 `workflow-platform` / `oa-platform` 同线、MyBatis-Plus 稳定 starter 仍为 `spring-boot3`。属知情接受。见 `ADR-003` | 出现影响本系统的 3.3.x 未修复 CVE 时重新评估 |
| `RISK-SCOPE-01` | `modify_runtime_files` 现在同时覆盖 Dockerfile/compose 与**依赖清单**，授予该 grant 即隐含授予修改依赖版本的能力（供应链面） | 中 | 中 | 接受。依赖变更的审查由开发规范 §设计 29（核查兼容性、许可证、已知漏洞）承担。见 `ADR-002` | 出现"允许改 compose 但不允许动依赖"的真实需求时，在 Skill System v2.1 引入独立的 `modify_build_manifests` 动作 |
