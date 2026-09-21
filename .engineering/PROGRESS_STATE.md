# PROGRESS STATE

> 最后更新：2026-09-21 · 由 Claude Code 会话写入
> 用途：跨会话 / 跨客户端（Claude · Codex · Cursor）恢复。
> **Reality is authoritative**：恢复时先验真实仓库与 Runtime，再信本文件。

## 现在在哪

| 项 | 值 |
|---|---|
| System Version | Engineering Skill System **2.0.0**（`v2.0.0-final`，架构 FROZEN） |
| 当前 Phase | **P0 Foundation 已完成**，下一步 P1 Platform Kernel |
| 分支 | `feat/p0-foundation` 已合并进 `main`（本地） |
| Driver | `claude-code-local`，probed `health: READY` |
| Drift | `NO_DRIFT` —— 三端 `_protocol` 为同一符号链接目标 `~/.cursor/skills/_protocol` |

## P0 出口条件（ROADMAP §P0）实测结果

| # | 条件 | 结果 | 证据 |
|---|---|---|---|
| ① | `mvn verify` 退出码 0 | **PASS** | `mvn -q clean verify` → exit 0 |
| ② | ArchUnit 存在且**能拦截**（负向证明） | **PASS** | 22 项测试，其中 6 项负向证明 |
| ③ | compose 起库 + `/actuator/health` = UP | **PASS** | `{"status":"UP"}`；liveness/readiness 均 UP |
| ④ | 日志中可见 traceId | **PASS** | 日志行 `[traceId=PROVE-TRACEID-IN-LOG]`；另有 4 项 `TraceIdFilterTest` 固化 |
| ⑤ | 版本兼容核验写入 ADR | **PASS** | `ADR-003` |

## 已解决的阻塞

- **`U-04`**（构建清单无可写作用域）→ 已解决，见 `ADR-002`。扩展 `RESOURCE-SCOPE-POLICY-v1.json`，
  把 Maven/Gradle/npm 清单纳入既有 `modify_runtime_files`。变更前后该系统自身 159 项治理测试均全绿。
- **`Q-01`**（数据库选型）→ 用户决策 PostgreSQL 16，见 `ADR-001`。

## 已关闭

- `RISK-VER-01`（Spring Boot 3.3.x 维护状态）→ **用户知情接受**，维持 3.3.11，不做进一步升级。
  仅保留"出现影响本系统的 3.3.x CVE 时重新评估"这一条件。见 `ADR-003`。

## 未决 / 需要用户处理

| 项 | 说明 |
|---|---|
| ~~`origin` remote~~ | 已由用户确认，2026-09-21 推送完成（传输改用 HTTPS，原因见下） |
| `Q-09` | 超收比例 / 是否允许负库存 —— **P4 之前**必须答复，当前按"默认禁止、配置可放开"继续 |
| `Q-02`/`Q-03` | 库位精度、是否对接 `wms-platform` —— P3 前答复较好 |

## 下一步（P1 Platform Kernel）

依赖 P0（已满足）。目标：组织/权限/编号/单据模型/状态机/审计/单据图/审批端口可用。
**按 ARTIFACT_INDEX，P1 之前应先产出 `CONTRACTS`**（API 路径、错误码表、事件 schema、字段校验），
Owner 为 `public-engineering-workflow:contracts`，然后 `implementation-slicing` 切片。

P1 出口条件中两条最容易被糊弄，需特别注意：
- 数据权限必须**断言生成的 SQL**中确实出现 `org_path` 前缀条件，不能只断言返回结果；
- 并发 50 线程取号无重复无空洞，必须用真实数据库集成测试证明。

## 恢复命令

```bash
cd /Users/liruijun/personal/LLM/erp-platform
docker compose -f deploy/compose.yaml --env-file .env up -d   # 需先由 .env.example 复制出 .env
mvn clean verify
java -jar erp-app/target/erp-app-0.1.0-SNAPSHOT.jar
curl -s localhost:8500/actuator/health
./test-data/init-test-data.sh verify
```
