# Assumptions（假设登记）

> Owner: `project-bootstrap`。每条写：陈述 · 依据（证据类型）· 不成立的影响 · 验证方式。
> **假设不是事实。** 下列任何一条都不得在后续文档中被当作 `KNOWN` 引用。

| ID | 陈述 | 依据 | 若不成立的影响 | 验证方式 |
|---|---|---|---|---|
| A-00 | 项目名 `erp-platform`，目录 `/Users/liruijun/personal/LLM/erp-platform`，groupId `com.lrj.erp` | 与 `oa-platform`/`wms-platform`/`workflow-platform` 的命名一致（BUSINESS_ASSUMPTION） | 仅需改名，无架构影响 | 用户确认 |
| A-01 | 目标问题是「进销存与财务分散、账实不符、无法追溯」 | INDUSTRY_PATTERN | 能力地图 Core 需重排 | 与干系人访谈 |
| A-02 | 系统定位为经营 System of Record，不承担仓储执行与法定账簿 | ARCHITECTURE_PRINCIPLE | 若要求法定账簿，需提前引入凭证与会计期间 | 用户确认 |
| A-03 | 单租户 100–2,000 用户，并发 ≤ 200 | INDUSTRY_PATTERN | 万人级时判权需改为位图快照方案 | 统计在册员工数 |
| A-04 | 日单据 ≤ 8,000 头 / 50,000 行 | 与 A-03 匹配 | 10× 时需提前读写分离与报表预聚合 | 统计旧系统单量 |
| A-05 | SKU ≤ 500,000，库存桶 ≤ 2,000,000 | INDUSTRY_PATTERN | 10× 时余额表需分区 | 统计商品档案数 |
| A-06 | 峰值倍数 5× | INDUSTRY_PATTERN | 峰值更高需入口限流 | 上线后观测 |
| A-07 | 团队规模小（1–5 人），无独立运维团队 | 该 Workspace 下 17 个项目均为单人主导（BUSINESS_ASSUMPTION） | 多团队并行时才需要考虑服务拆分 | 用户确认 |
| A-08 | MVP 单一本位币，币种字段预留但不做汇率折算 | BUSINESS_ASSUMPTION | 多币种时需补汇率表与折算规则，金额字段已含币种故为加法变更 | 用户确认 |
| A-09 | 库存计价采用移动加权平均 | INDUSTRY_PATTERN | 需 FIFO/个别计价时**可从不可变流水重算**，代价有界 | 与财务确认 |
| A-10 | 多租户采用共享库 + `tenant_id` 列 + 强制过滤 | 与 A-03/A-04 规模匹配 | 有数据驻留/隔离合规要求时需分库，届时 `tenant_id` 已在，迁移路径存在 | 确认合规要求 |
| A-11 | 库位（Location）在 MVP 不启用，台账用哨兵 `NO_LOCATION` 占位 | 与 A-04 作业量匹配 | 启用时不改主键，只填充维度值 | 确认是否有库位管理需求 |
| A-12 | MVP 审批为「内置顺序/多级审批」，不接 BPMN 引擎 | 提示词第十一章「不要在缺乏必要性的情况下过度引入复杂 BPM 平台」 | 需要会签/加签/转交/抄送时切换到 `workflow-platform` 适配器 | 收集真实审批场景 |
| A-13 | 本地开发复用 `dev-infra` 的 PostgreSQL 16 共享实例，ERP 使用独立 database 与账号 | REPOSITORY_EVIDENCE `dev-infra/docs/service-catalog.md`「每项目使用独立 database、owner 和应用账号」 | 若需隔离则改为项目专属实例 | 按 dev-infra onboarding 执行 |
| A-14 | 应用端口 8500（app）/ 8501（console dev）未被占用 | REPOSITORY_EVIDENCE：扫描 Workspace 全部 `*.yml`/`compose*` 的端口，8500–8505 未出现 | 冲突则顺延 | 启动时验证 |
