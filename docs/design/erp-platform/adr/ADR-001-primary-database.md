# ADR-001 主数据库选用 PostgreSQL 16

- 状态：**ACCEPTED**（2026-09-21，用户在 Phase 0 决策点确认）
- 相关：`Q-01`、`TECH_SELECTION.md`、假设 `A-13`

## 背景

提示词第十五章建议 MySQL 8；`backend-architecture-design` 的选型分析倾向 PostgreSQL 16，并把最终选择作为 `Q-01` 交由用户确认。两者在 `dev-infra` 中**均已实测运行**（PostgreSQL 16 → 45432，MySQL 8.4 → 43306），因此不存在基础设施约束，这是一次纯粹的技术取舍。

## 决策

采用 **PostgreSQL 16**。

## 理由

1. **部分唯一索引**：`CREATE UNIQUE INDEX ... WHERE status <> 'CANCELLED'` 原生支持。ERP 大量需要"未取消的单据号唯一""同一来源单据只允许一张有效应付"这类约束；MySQL 8 需要用触发器或冗余列变通，而把完整性约束从数据库挪到应用层，正是账实不符的常见起点。
2. **`NUMERIC` 精确十进制**：金额与数量的精度要求硬性，不接受浮点。
3. **`JSONB`**：审计前后值（`doc_` 审计表）与单据扩展字段需要可检索的半结构化存储。
4. **`text_pattern_ops`**：数据权限下推 `org_path LIKE '/1/23/%'` 需要前缀索引有效。P1 的出口条件要求断言"SQL 中确实出现前缀条件"，索引可用性直接决定该方案是否成立。
5. 与 `workflow-platform` 同栈，P8 接入时减少一套运维知识。

## 被否决方案

**MySQL 8.4** — 不是错误选择，只是本项目的约束更吃 PostgreSQL 的特性。若组织层面统一要求 MySQL，回退代价在 Phase 0 内极低（当前零业务表），Phase 3 之后急剧升高。

## 后果

- 本地默认复用 `dev-infra` 的 `postgres:16-alpine` 共享实例，ERP 使用独立 database 与账号（`A-13`）。
- 项目自带 `deploy/compose.yaml` 亦使用 `postgres:16-alpine`，与 `dev-infra` 保持同版本，避免版本漂移（开发规范 §五）。
- 迁移脚本将使用 PostgreSQL 方言（部分索引、`JSONB`、`TIMESTAMPTZ`），这构成事实上的数据库绑定；**这是有意接受的代价**，换取上述完整性能力。
