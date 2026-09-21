# ADR-002 把构建清单纳入可写 Resource Scope

- 状态：**ACCEPTED**（2026-09-21）
- 相关：`U-04`、`PLANNER_HANDOFF.md` §6、提示词 §36.1 / §36.2
- 变更对象：`~/.cursor/skills/_protocol/RESOURCE-SCOPE-POLICY-v1.json`（三端共享符号链接）

## 背景

Engineering Skill System 2.0 的 `RESOURCE-SCOPE-POLICY-v1.json` 只定义四个写动作：
`write_design_docs` / `modify_product_code` / `modify_tests` / `modify_runtime_files`。

**实测证据**（本轮复现，非沿用 checkpoint）：

```
classify('pom.xml')          -> None
classify('erp-app/pom.xml')  -> None
classify('build.gradle')     -> None
```

`BUILD_CONFIGURATION` 在整个 `_protocol` 树中**不存在**。按 §36.2 Fail Closed，作用域未知必须阻断而不是"当普通代码继续写"，因此 P0 的 13 模块骨架（需写 13 个 `pom.xml`）在 `CS-M0-02` 处被阻塞。

系统本身**读** `pom.xml` 很充分（`containerization-runtime.py` 解析 artifactId、java.version、spring-boot-maven-plugin），但从未被授予**写**的权限——这是一处读写不对称的能力缺口，不是漂移：三端 `_protocol` 为同一符号链接目标，`diff` 无差异。

## 决策

把 Maven / Gradle / npm 构建清单的 glob 加入**既有的** `modify_runtime_files` 写动作，而不是新建一个写动作。

## 理由

决定性证据是策略自身的分类先例：**`Makefile` 与 `**/Makefile` 早已属于 `modify_runtime_files`**。Makefile 就是构建清单，说明该动作的语义本就覆盖构建文件；Maven / Gradle / npm 清单只是被遗漏了。因此这是**一致性修复**，而非扩张授权。

同时它满足提示词 §36.1 的核心要求：构建清单**不得**被当作普通 Source Code —— `modify_product_code` 与 `modify_runtime_files` 是两个不同的 grant，授予后者不等于授予前者。

## 被否决方案

**新增 `modify_build_manifests` 独立写动作**。它在治理上更精确——修改依赖（供应链风险）与修改 Dockerfile 确实是不同性质的权限。但 `modify_runtime_files` 这个动作名出现在约 20 个 protocol 文件中（驱动清单、能力目录、风险策略、编排能力、修复策略等），新增一个动作要同步全部位置才自洽，对一个 `status: RELEASED` / 架构 `FROZEN` 的系统属于高风险改动。

**记为后续项**：若将来出现"允许改 compose 但不允许动依赖"的真实需求，应在 v2.1 引入该独立动作。本 ADR 明确记录这一取舍，不假装它不存在。

## 验证

- 变更前基线：`test-native-policy-boundary` `test-kernel-authorization` `test-execution-authorization` `test-policy-governance` `test-multi-client-governance` 共 **159 项全绿**。
- 变更后同一组：**159 项仍全绿**。
- `classify('pom.xml') -> modify_runtime_files`；`.java` 仍为 `modify_product_code`，测试仍为 `modify_tests`，`_protocol/**` 仍为 `None`（hard_deny 未被削弱）。

## 后果

- 修复通过共享符号链接**同时对 Claude / Codex / Cursor 生效**，不会产生三端事实分裂（提示词 §38）。
- 授予 `modify_runtime_files` 的 ChangeSet 自此也能修改依赖版本。这是本决策接受的扩权；依赖变更的审查仍由开发规范 §设计 29（核查兼容性、许可证、已知漏洞）承担。
- `PROJECT_BOOTSTRAP_REPORT.json` 中 `U-04` 的 `unknowns` 条目可在下次 replan 时关闭。
