# Planner Handoff

> Owner: `project-bootstrap` · 模式 `GREENFIELD_PLANNING` · 运行模式 `READ_ONLY` + `DESIGN_ONLY`
> **本技能在此 STOP。** 交接 ≠ 授权：本文件只声明每个 ChangeSet 需要的能力，Effective Permission 与审批在执行时由 Runtime 计算。

## 1. 交接内容

| 项 | 值 |
|---|---|
| 目标协议 | `execution-plan/v1` |
| 报告 | `.engineering/bootstrap/PROJECT_BOOTSTRAP_REPORT.json`（`project-bootstrap-report/v1`） |
| 计划源 | `.engineering/bootstrap/EXECUTION_PLAN_SOURCE.json`（内嵌于报告的 `plannerHandoff.executionPlanSource`） |
| Goal | `engineering-goal/v1`，`goal_type = INITIALIZE_PROJECT`，`desired_state = READY`，`execution_mode = PLAN_ONLY` |
| `stopAfterHandoff` | `true` |
| `executes` | `false` |

## 2. Target State（机器可判定）

```yaml
desired_readiness: READY
verifiable: true
gates: [ARCHITECTURE, VALIDATION]
dimensions:
  architecture: { baseline: CURRENT, lifecycle: CURRENT }
  validation:   { status: PASS }
  phase_gates:  { engineering_baseline: PASS }
```

`engineering_baseline` 由 `CS-M0-01` 的 `metadata.establishesGate` 建立；没有它，后续写产品代码的 ChangeSet 在 Router 处不可达。
`analysis` 域标记为 `notApplicable`（`NO_REPOSITORY`：ERP 自身尚无代码仓库），不列为 Planning Gap。

## 3. Planning Gap

| gap_id | domain | current | target |
|---|---|---|---|
| `G-bootstrap` | bootstrap | PLANNED | CURRENT/PASS |
| `G-baseline` | baseline | MISSING | CURRENT/PASS |
| `G-product_lifecycle` | product_lifecycle | MISSING | CURRENT/PASS |

## 4. Objective → Milestone

| Objective | Milestones |
|---|---|
| `OBJ-BASELINE` | M0 |
| `OBJ-FEATURE` | M1 · M2 · M3 · M4 · M5 · M6 · M7 · M8 · M9 |
| `OBJ-VALIDATION` | M10 · M11 |

## 5. 执行视野

**只有 M0 拆到 ChangeSet**（4 条）。M1–M11 停留在 Milestone / WorkPackage 级，到达时由 Runtime `replan`（`planVersion+1`），不预先伪造细粒度计划。

| ChangeSet | changeType | mvpClass | scope | 可执行验证 | 备注 |
|---|---|---|---|---|---|
| `CS-M0-01` | ANALYSIS | MVP | `docs/**` `.engineering/**` | — （纯文档） | **`establishesGate: engineering_baseline`** |
| `CS-M0-02` | CONFIGURATION | MVP | `erp-app/src/main/**` `erp-kernel/src/main/**` `erp-app/src/test/**` | `mvn -q -DskipITs verify` → `COMPILE` `UNIT_TEST` | **被 `U-04` 阻塞，需先做 Discovery** |
| `CS-M0-03` | TEST_PROTECTION | MVP | `erp-app/src/test/**` `erp-kernel/src/test/**` | `mvn -q -Dtest=*ArchitectureTest test` → `COMPILE` `UNIT_TEST` | ArchUnit + 负向用例 |
| `CS-M0-04` | CONFIGURATION | MVP | `deploy/**` `scripts/**` `erp-app/src/main/**` | `mvn -q -DskipITs verify` → `COMPILE` `UNIT_TEST` | compose 仅 PostgreSQL |

`verify.command` 使用的 `mvn` 已在本机实测存在（`mvn -v` → 3.9.12，JDK 21.0.11）。

## 6. 未知项（`unknowns`）

> **2026-09-21 更新：`U-04` 已解决（`ADR-002`）。** 处置方式是扩展 Engineering Skill System 的
> `RESOURCE-SCOPE-POLICY-v1.json`，把 Maven/Gradle/npm 构建清单纳入既有的 `modify_runtime_files`
> 写动作（依据：`Makefile` 早已归入该动作，属于一致性修复而非扩张授权）。
> 变更前后该系统自身的 159 项治理测试均全绿。`CS-M0-02` 的 Discovery 阻塞随之解除，P0 已执行完毕。

| id | 未知 | 影响 | 退出标准 |
|---|---|---|---|
| `U-04` | Runtime 的 resource-scope 策略不把 Maven 构建清单（`pom.xml`）归入任何可写动作——**已实测** `classify("pom.xml") → None`，`classify("erp-app/pom.xml") → None` | `CS-M0-02`（多模块骨架需要写 13 个 `pom.xml`） | 确认 Runtime 是否扩展可写路径以包含构建清单，或由人工一次性创建骨架后把仓库交回 Runtime；结论写入 ADR 并相应调整 `CS-M0-02` 的 scope |

Planner 会把它转成 `type: DISCOVERY` 的 WorkPackage；`CS-M0-02` 在 Discovery 完成并 Re-plan 之前不会执行。
`U-01`（行业特化）、`U-02`（报表口径）、`U-03`（数据迁移范围）**不进机器 `unknowns`**：它们不影响当前执行视野（M0），只记录在 [`OPEN_QUESTIONS.md`](OPEN_QUESTIONS.md)。把它们放进 `unknowns` 会让整个执行视野被保守地挂起。

## 7. 无 BLOCKING_QUESTION

`Q-01..Q-10` 每一项都有安全假设与有界回退路径（见 [`OPEN_QUESTIONS.md`](OPEN_QUESTIONS.md)），不涉及破坏性 / 不可逆 / 安全 / 合规 / 关键数据类阻塞，因此**不触发 Planning Gate 的 HOLD**。

## 8. Planning Gate 实测结果

用 `_protocol/tools/engineering-goal-planner.py` 的 `GreenfieldPlanner.plan_from_bootstrap` 对本报告实跑（见 [`NEXT_ACTIONS.md`](NEXT_ACTIONS.md) 的复现命令）：

| 维度 | 结果 |
|---|---|
| Goal / Target State / Scope / Dependency / Execution / Permission / Validation / Risk / MVP | `PASS` |
| Artifact | `PASS`（本文件与 `NEXT_ACTIONS.md`、`ARTIFACT_INDEX.md` 写出后） |
| Discovery | `WARN REPLAN_REQUIRED_AFTER_DISCOVERY [CS-M0-02]` —— 预期行为，来自 `U-04` |

**Verdict：`PASS_WITH_ASSUMPTIONS`**（可交接；缺失的非关键信息以显式假设承载）。

## 9. 下一步归谁

- 编译、校验、调度、执行、重试、预算、检查点 → **Runtime**。
- 下一步动作 → `engineering-orchestrator` / Workflow。
- 本技能不继续编码；`GREENFIELD_PLANNING` 在此 STOP。
