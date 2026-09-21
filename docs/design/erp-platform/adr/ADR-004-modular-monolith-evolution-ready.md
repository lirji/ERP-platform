# ADR-004 模块化单体 + Day-0 演进就绪边界

- 状态：**ACCEPTED**（2026-09-21，P0 落地确认）
- 相关：`ARCHITECTURE_OPTIONS.md` DEC-01、`MODULE_SERVICE_MAP.md`、`ARCHITECTURE_EVOLUTION.md`、提示词第十四/三十二章

## 背景

提示词第十四章要求先比较 `Modular Monolith` 与 `Microservices` 再决定，并禁止为展示技术而拆服务；第三十二章要求从 Day-0 起区分四类边界。本 ADR 把这些要求固定为 P0 的可执行现实。

## 决策

**一个可部署单元（`erp-app`）+ 12 个有明确边界的 Maven 模块**，四类边界显式分离：

| 边界 | 当前状态 |
|---|---|
| Domain Boundary | 11 个业务/平台模块各自独立 |
| Module Boundary | 独立（Maven 模块 + ArchUnit 强制依赖方向） |
| Runtime Boundary | **合一**（同一 JVM 进程） |
| Deployment Boundary | **合一**（`erp-app` 单一构件） |

即：**Logical Service Boundary First, Physical Service Extraction Later**。

Domain / Module 边界独立而 Runtime / Deployment 合一，**不是架构失败**，而是模块化单体向微服务演进的正常中间态（提示词 §32.1）。

## 为什么不是微服务

团队规模 1–5 人且无独立运维（`A-07`）。当前没有任何模块具备独立扩缩容需求、独立发布频率或独立团队 Ownership——即 `ARCHITECTURE_EVOLUTION.md` 定义的 Extraction Trigger **一个都未出现**。在无 Trigger 时默认保持模块化单体（提示词 §34.3）。

更关键的是：ERP 的库存与财务闭环大量依赖本地事务（如"过账与流水必须同事务"）。过早拆进程会把这些不变量变成分布式事务问题，用极高成本换取当前并不需要的隔离性。

## 让"未来可拆"成为可验证事实而非文档描述

提示词第十四章要求：任何"未来可拆微服务"的结论都不能只停留在文档描述。P0 落地了以下**可执行**保障：

| 保障 | 落地形式 | 测试 |
|---|---|---|
| 依赖方向 | Maven 依赖 + ArchUnit | `ModuleDependencyArchitectureTest`（6 项） |
| 无依赖环 | ArchUnit slices | 同上 |
| 库存不反向依赖采购/销售 | ArchUnit | 同上 + **负向证明** |
| Data Ownership（表前缀） | Mapper XML 扫描 | `TableOwnershipArchitectureTest` + **负向证明** |
| 领域层框架无关 | ArchUnit | `CodingConventionArchitectureTest` + **负向证明** |
| 跨模块协作走事件 | `erp_outbox_message` 表（V1 基线） | P3 起集成测试 |

**负向证明**（`ArchRuleEnforcementNegativeTest`，6 项）是这里的关键：它用故意违规的夹具证明每条规则**确实会拦截**，而不是因为当前没有违规而空跑成假绿。提示词禁止事项第 22 条（架构测试只写在文档里而不执行）与 P0 出口条件 ② 由此满足。

## 后果

- `erp-app` 是**唯一**允许依赖全部模块的模块；业务模块依赖 `erp-app` 会被 ArchUnit 拒绝。
- `erp-kernel` 作为共享内核**刻意保持很小**——共享内核的代价是耦合发布。基础设施代码放在各模块自己的 `infrastructure` 包内，不集中。
- 提取时机由 `ARCHITECTURE_EVOLUTION.md` 的 Extraction Trigger 与评分卡决定，不由代码量决定（提示词禁止事项第 18 条）。
- Phase 13 重新评估时，"继续保持模块化单体"是合法且可能最优的结论。
