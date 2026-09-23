# 文档地图

同步日期：2026-09-23；基线：main `53290422976b54cbb7eeec36f2ae55b1c3d7faf2`。本次为未提交规划文档同步，不声称远程同步。

| 范围 | 权威文档 | 状态 |
|---|---|---|
| 当前任务与下一步 | [CODEX_PROGRESS](../CODEX_PROGRESS.md)、[PROGRESS_STATE](../.engineering/PROGRESS_STATE.md) | 当前事实；历史Gate保留 |
| 总体后端与已有选型 | [BACKEND_ARCHITECTURE](design/erp-platform/BACKEND_ARCHITECTURE.md)、[TECH_SELECTION](design/erp-platform/TECH_SELECTION.md) | 后端沿用；前端按用户决定改为React/TS/Vite/Ant Design |
| 已有HTTP/事件共同语义 | [CONTRACTS](design/erp-platform/contracts/CONTRACTS.md) | 原有权威；新增草案不覆盖已发布行为 |
| 首批采购产品化 | [BRIEF](design/erp-platform/first-business-loop/BRIEF.md) | PROPOSED；索引前端、HTTP、后端增量和候选切片 |
| 真实OIDC运行边界 | [OIDC](security/OIDC.md) | 本地已交付；生产目标未提供 |
| 能力缺口 | [CAPABILITY_MAP](../.engineering/exploration/CAPABILITY_MAP.md) | 只读分析，不是实施完成证明 |

本批文档描述目标设计，React方向已确认但尚未安装依赖，未更改实际软件版本、运行连接、数据库迁移或凭据；无需更新私密连接记录。落地时由模块Owner同步实现与契约，Progress仅记录状态。本轮未读写秘密。

- [React工作台运行](../erp-web/README.md)；[S0验收](../.engineering/gates/TEST_RESULT-FBL-S0.md)
