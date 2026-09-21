# Artifact Index

> Owner: `project-bootstrap`。本文件只登记引用与 Owner，**不复制其他 Owner 的内容**。

| 规划项 | ref | Owner |
|---|---|---|
| 系统愿景 | [`.engineering/bootstrap/SYSTEM_VISION.md`](SYSTEM_VISION.md) | project-bootstrap |
| **业务能力地图** | [`.engineering/bootstrap/CAPABILITY_MAP.md`](CAPABILITY_MAP.md) | project-bootstrap |
| 系统边界 | [`.engineering/bootstrap/SYSTEM_BOUNDARY.md`](SYSTEM_BOUNDARY.md) | project-bootstrap |
| 非功能需求 | [`.engineering/bootstrap/NFR.md`](NFR.md) | project-bootstrap |
| 假设登记 | [`.engineering/bootstrap/ASSUMPTIONS.md`](ASSUMPTIONS.md) | project-bootstrap |
| 待澄清问题 | [`.engineering/bootstrap/OPEN_QUESTIONS.md`](OPEN_QUESTIONS.md) | project-bootstrap |
| MVP 范围 | [`.engineering/bootstrap/MVP_SCOPE.md`](MVP_SCOPE.md) | project-bootstrap |
| **Roadmap 与 Phase/Gate** | [`.engineering/bootstrap/ROADMAP.md`](ROADMAP.md) | project-bootstrap |
| 风险登记 | [`.engineering/bootstrap/RISK_REGISTER.md`](RISK_REGISTER.md) | project-bootstrap |
| Planner Handoff | [`.engineering/bootstrap/PLANNER_HANDOFF.md`](PLANNER_HANDOFF.md) | project-bootstrap |
| **ERP Implementation Plan（计划源）** | [`.engineering/bootstrap/EXECUTION_PLAN_SOURCE.json`](EXECUTION_PLAN_SOURCE.json) | project-bootstrap |
| 下一步 | [`.engineering/bootstrap/NEXT_ACTIONS.md`](NEXT_ACTIONS.md) | project-bootstrap |
| 结构化报告 | [`.engineering/bootstrap/PROJECT_BOOTSTRAP_REPORT.json`](PROJECT_BOOTSTRAP_REPORT.json) | project-bootstrap |
| 需求 BRIEF | [`docs/design/erp-platform/BRIEF.md`](../../docs/design/erp-platform/BRIEF.md) | public-engineering-workflow |
| 目标架构（索引） | [`docs/design/erp-platform/BACKEND_ARCHITECTURE.md`](../../docs/design/erp-platform/BACKEND_ARCHITECTURE.md) | backend-architecture-design |
| 领域地图 | [`architecture/DOMAIN_MAP.md`](../../docs/design/erp-platform/architecture/DOMAIN_MAP.md) | backend-architecture-design |
| **限界上下文图** | [`architecture/BOUNDED_CONTEXT_MAP.md`](../../docs/design/erp-platform/architecture/BOUNDED_CONTEXT_MAP.md) | backend-architecture-design |
| 架构选项与决策 | [`architecture/ARCHITECTURE_OPTIONS.md`](../../docs/design/erp-platform/architecture/ARCHITECTURE_OPTIONS.md) | backend-architecture-design |
| 架构演进 | [`architecture/ARCHITECTURE_EVOLUTION.md`](../../docs/design/erp-platform/architecture/ARCHITECTURE_EVOLUTION.md) | backend-architecture-design |
| 模块图 | [`architecture/MODULE_SERVICE_MAP.md`](../../docs/design/erp-platform/architecture/MODULE_SERVICE_MAP.md) | backend-architecture-design |
| 数据架构 | [`architecture/DATA_ARCHITECTURE.md`](../../docs/design/erp-platform/architecture/DATA_ARCHITECTURE.md) | backend-architecture-design |
| 集成架构 | [`architecture/INTEGRATION_ARCHITECTURE.md`](../../docs/design/erp-platform/architecture/INTEGRATION_ARCHITECTURE.md) | backend-architecture-design |
| 安全架构 | [`architecture/SECURITY_ARCHITECTURE.md`](../../docs/design/erp-platform/architecture/SECURITY_ARCHITECTURE.md) | backend-architecture-design |
| 一致性模型 | [`architecture/CONSISTENCY_MODEL.md`](../../docs/design/erp-platform/architecture/CONSISTENCY_MODEL.md) | backend-architecture-design |
| 流程与状态模型 | [`architecture/WORKFLOW_STATE_MODEL.md`](../../docs/design/erp-platform/architecture/WORKFLOW_STATE_MODEL.md) | backend-architecture-design |
| 技术选型 | [`docs/design/erp-platform/TECH_SELECTION.md`](../../docs/design/erp-platform/TECH_SELECTION.md) | backend-architecture-design |

## 尚未产出（刻意，属于后续阶段的 Owner）

| 产物 | Owner | 何时 |
|---|---|---|
| `CONTRACTS`（API 路径、错误码表、事件 schema、字段校验） | public-engineering-workflow:contracts | P0 之后、P1 之前 |
| `IMPLEMENTATION_SLICES` | implementation-slicing | CONTRACTS 之后 |
| `SOURCE_CODE` | backend-implementation / frontend-implementation | 各切片 |
| `TEST_RESULT` | implementation-validation | 各切片 |
| `FRONTEND_ARCHITECTURE` | frontend-architecture-design | 控制台设计启动时 |
| `DEPLOYMENT_TOPOLOGY` / Compose | runtime-and-deploy · project-containerization | P0 与 P11 |
| `TEST_DATA_KIT` | full-link-test-data | P11 |
