# ADR-003 版本基线与兼容性核验

- 状态：**ACCEPTED**（2026-09-21）
- 相关：`TECH_SELECTION.md`（多处标注「待 Phase 0 核验」）、ROADMAP P0 出口条件 ⑤

## 背景

`TECH_SELECTION.md` 选定 Spring Boot **3.3.5**，理由是与 `workflow-platform`（README 明示 3.3.5）、`oa-platform` 保持一致，便于未来共用 SDK；同时明确标注：

> ⚠️ **待 Phase 0 核验**：需对照官方支持矩阵确认 3.3.5 的维护状态；本规划未核验，不得写成已确认。

本 ADR 即该核验的结果。

## 核验结果

| 项 | 核验结论 | 证据 |
|---|---|---|
| JDK | **21.0.11 LTS**，本机实测 | `java -version` |
| Maven | **3.9.12**，本机实测 | `mvn -v` |
| Spring Boot 3.3.5 | **不是 3.3.x 的最新补丁**；该线最新为 **3.3.11**，当前 Spring Boot 主线已到 **4.1.x** | 版本目录查询 |
| Spring Boot 3.3.11 | 可解析 | Maven Central 返回 200 |
| MyBatis-Plus | `mybatis-plus-spring-boot3-starter` **3.5.9** 可解析（3.5.12 亦可） | Maven Central 返回 200 |
| Flyway | 由 Spring Boot 3.3.11 的 BOM 托管，**不显式覆盖版本** | 未在 `pom.xml` 指定 |
| PostgreSQL | 运行时实测 **16.15** | 启动日志 `Database: jdbc:postgresql://...(PostgreSQL 16.15)` |
| ArchUnit | **1.4.1** | 本地仓库已缓存，构建通过 |

## 决策

**采用 Spring Boot 3.3.11**，而不是规划书写的 3.3.5。

理由：`3.3.5 → 3.3.11` 是同一 minor 线内的补丁升级，**完整保留** TECH_SELECTION 选择 3.3.x 的全部理由（与 `workflow-platform` / `oa-platform` 同 minor，P8 接入与 SDK 共用不受影响），同时补上 6 个补丁版本的缺陷与安全修复。停留在 3.3.5 没有任何技术收益，只是规划书写作时的快照。

这属于「Phase 0 核验并据此修正补丁版本」，在 `backend-implementation` 的授权范围内；**不构成对已批准架构决策的推翻**。

## 版本风险：用户已决策接受

> **`RISK-VER-01`：Spring Boot 3.3.x 很可能已退出 OSS 维护。**

主线已至 4.1.x，按 Spring 的常规支持节奏，3.3 线的 OSS 支持窗口应已关闭，3.3.11 很可能是该线最后一个公开补丁。这意味着后续安全修复不会再进入 3.3.x。

**决策（2026-09-21，用户确认）**：**维持 3.3.11，不做进一步升级**，也不保留"P1 结束前必须复核"的强制决策点。

理由：3.3.x 与 `workflow-platform`（3.3.5）、`oa-platform` 同线，P8 接入与未来共用 SDK 的成本最低；
MyBatis-Plus 当前的稳定 starter 明确命名为 `spring-boot3`，跨到 4.x 需要重新核验持久层兼容性。
用户在知悉维护状态的前提下选择保持现状，属于**知情接受**，不是遗漏。

**保留的唯一触发条件**：出现影响本系统的 3.3.x 未修复 CVE 时重新评估升级。
这是新事实触发的重新评估，不与上述决策冲突。

## 后果

- 根 `pom.xml` 的 parent 版本为 `3.3.11`；MyBatis-Plus 与 ArchUnit 版本在 `<properties>` 中集中管理，子模块不写版本。
- 本 ADR 的核验结论覆盖 `TECH_SELECTION.md` 中相应的「待核验」标注；该文档中 Spring Boot 版本应读作 3.3.11。
- `RISK-VER-01` 需登记进 `RISK_REGISTER.md`。
