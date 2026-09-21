# PROGRESS STATE

> 最后更新：2026-09-21 · 由 Claude Code 会话写入
> 用途：跨会话 / 跨客户端（Claude · Codex · Cursor）恢复。
> **Reality is authoritative**：恢复时先验真实仓库与 Runtime，再信本文件。

## 现在在哪

| 项 | 值 |
|---|---|
| System Version | Engineering Skill System **2.0.0**（`v2.0.0-final`，架构 FROZEN） |
| 当前 Phase | **P3 Inventory Core 已完成**（GATE-P3-20260921 = PASS_WITH_ASSUMPTIONS），下一步 P4 / P5（可并行） |
| 分支 | `feat/p3-inventory-core` → `main`，已推送 `origin/main` |
| Driver | `claude-code-local`，probed `health: READY` |
| Drift | `NO_DRIFT` —— 三端 `_protocol` 为同一符号链接目标 `~/.cursor/skills/_protocol` |

## P3 出口条件实测结果（GATE-P3-20260921）

| # | 条件 | 结果 |
|---|---|---|
| ① | INV-01 余额 == 流水代数和（1000 次随机过账） | PASS |
| ② | INV-02 并发 50 线程无负库存、无超卖 | PASS |
| ③ | INV-04 同一来源行过账 10 次只一次效果（含并发） | PASS |
| ④ | 预占→部分消耗→释放 链正确，释放不超额 | PASS |
| ⑤ | 每条流水可反查来源单据行 | PASS |
| ⑥ | `available` 无对应数据库字段 | PASS |
| ⑦ | 对账脚本跑通 | PASS |

`mvn clean verify` EXIT 0 · 单元/架构 31 + 集成 50 = **81**
Smoke：app 启动 UP · Flyway v60 · 对账通过

## P2 出口条件实测结果（GATE-P2-20260921）

| # | 条件 | 结果 |
|---|---|---|
| ① | 编码租户内唯一（50 线程并发只成功一条） | PASS |
| ② | 停用只切断新引用，已有单据不受影响 | PASS |
| ③ | 改名后历史单据显示快照值 | PASS |
| ④ | 被引用后关键字段（编码、基本单位）锁定 | PASS |
| ⑤ | 批量导入 1000 行，失败整批回滚 | PASS |

`mvn clean verify` EXIT 0（连跑两次）· 单元/架构 29 + 集成 40 = **69**
12 张 md_* 表 / 107 条注释 / 无注释字段数 0

## P1 出口条件实测结果（GATE-P1-20260921）

| # | 条件 | 结果 |
|---|---|---|
| ① | OIDC 登录拿到 AccessContext | **PARTIAL** —— 装配链路已通过完整 HTTP 栈验证，但 OIDC 令牌校验未接入，见 `BLOCK-P1-01` |
| ② | 无权限用户 403 | PASS |
| ③ | 数据权限下推且断言 SQL | PASS |
| ④ | 并发 50 线程取号 | PASS |
| ⑤ | 非法迁移被拒 + 并发只一个成功 | PASS |
| ⑥ | 审计含前后值/IP/traceId | PASS |
| ⑦ | Outbox 重试与死信 | PASS |

`mvn clean verify` EXIT 0 · 单元/架构 29 + 集成 27 = **56**（架构测试含 6 项负向证明）

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
| **`BLOCK-P1-01`** | **生产无认证路径**：临时请求头通道默认关闭且可伪造，OIDC 未接入。Casdoor 已实测可达（`:8000`，discovery 正常），接入前需在 Casdoor 注册 ERP 客户端（需用户决定客户端标识与回调地址）。**关闭前不得对外暴露** |
| `Q-09` | 超收比例 / 是否允许负库存 —— **P4 之前**必须答复，当前按"默认禁止、配置可放开"继续 |
| `SPECIFIED_ORG/COMPANY` | 角色上暂无明细配置表，命中时按 `DEPT_AND_BELOW` 处理，待 P2 补 |
| `Q-02`/`Q-03` | 库位精度、是否对接 `wms-platform` —— P3 前答复较好 |

## 下一步（P4 / P5，可并行）

二者都只依赖 P3（已满足）且互不依赖：
- **P4 Procure to Receive**（CAP-C05、CAP-S09）：申请→审批→订单→收货→入库。
  需 `Q-09`（超收比例 / 是否允许负库存）答复；未答复按「默认禁止、配置可放开」继续。
- **P5 Order to Ship**（CAP-C06、CAP-S06）：订单→审批→预占→拣货→出库→发货→签收。

可并行推进 `BLOCK-P1-01` 的 OIDC 接入（需用户先在 Casdoor 注册客户端）。

P4/P5 出口条件中最容易被糊弄的一条：**单据图双向可追溯**——
从入库单可反查采购订单，从采购订单可正查入库单，两个方向都要断言。

## 历史：P1 Platform Kernel

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
