# PROGRESS STATE

> 最后更新：2026-09-22 · Codex 接手 Claude 会话后回写
> 用途：跨会话 / 跨客户端（Claude · Codex · Cursor）恢复。
> **Reality is authoritative**：恢复时先验真实仓库与 Runtime，再信本文件。

## 现在在哪

| 项 | 值 |
|---|---|
| System Version | Engineering Skill System **2.0.0**（`v2.0.0-final`，架构 FROZEN） |
| 当前 Phase | **P6 AR/AP/Settlement 已完成并交付**，两条后端 MVP 闭环通过；实现 ad38b1f，合并 067b732 已推送 origin/main |
| 分支 | P5 分支与 P6 分支均已合并推送 main；P5 edd85db，P6 067b732 |
| Driver | Codex 本地直接执行；未发现可恢复 Runtime run_id，不沿用历史 health 断言 |
| Drift | `NO_DRIFT` —— 三端 `_protocol` 为同一符号链接目标 `~/.cursor/skills/_protocol` |

## P6 当前验收

用户 2026-09-22 明确本轮做到 P6 MVP，不扩展 P7–P11。
七条出口全部 PASS，见 `.engineering/gates/GATE-P6-20260922.md`。
`mvn -q clean verify`：33 单元/架构 + 87 集成 = **120**，失败/错误/跳过均 0。
后台 Outbox：20 样本、1 秒间隔，P95 1.13772545 秒（仅本地观测）。
源码指纹和逐套件统计见 TEST_RESULT-P6.json。
生产 OIDC、销售/财务 HTTP 入口、历史 v1 财务补账仍未完成；没有执行生产部署。

## P5 历史验收

五项阶段出口及双向追溯全部 PASS，见 `.engineering/gates/GATE-P5-20260922.md`。
`mvn -q clean verify` 退出码 0；33 单元/架构 + 71 集成 = **104**，无失败或跳过。
P5 当时尚未完成 P6；当前 P6 结果见上节。生产 OIDC、HTTP 销售入口仍未完成。

## P4 出口条件实测结果（GATE-P4-20260921）

| # | 条件 | 结果 |
|---|---|---|
| ① | 分 3 次收货，库存增加量 == 收货总量 | PASS |
| ② | **超收一律被拒**（用户决策，无配置开关） | PASS |
| ③ | 少收后关闭剩余，已收部分保留 | PASS |
| ④ | 已收货的订单不能取消 | PASS |
| ⑤ | 单据图双向可追溯 | PASS |
| ⑥ | 重复提交收货单不二次入库 | PASS |

`mvn clean verify` EXIT 0（连跑两次）· 单元/架构 33 + 集成 59 = **92**

> 本阶段补齐了 P1 遗留的 CAP-P08（单据关系图）与 CAP-P10（审批中心）——
> 二者列在 ROADMAP 的 P1 能力集但无对应出口条件，此前未落地。详见 GATE-P4 §3。

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
| `Q-09` | 超收比例 / 是否允许负库存 —— **P4 之前**必须答复，已答复：不允许超收、不允许负库存；无放开开关 |
| `SPECIFIED_ORG/COMPANY` | 角色上暂无明细配置表，命中时按 `DEPT_AND_BELOW` 处理，待 P2 补 |
| `Q-02`/`Q-03` | 库位精度、是否对接 `wms-platform` —— P3 前答复较好 |

## 已完成阶段说明（P5 Order to Ship）

依赖 P3（已满足）。能力 CAP-C06、CAP-S06：订单→审批→预占→拣货→出库→发货→签收。
P3 的预占链（预占→部分消耗→释放）已就绪，P5 直接建立其上。

出口条件中易被糊弄的两条：
- 取消订单必须**同时**断言预占表与余额都正确，而不只看其一；
- 信用超限被拦截后，审批放行才可下单。

P6 两条内部业务闭环已验证并交付，本次目标已完成；后续生产能力与 P7–P11 不属于本次目标。

可并行推进 `BLOCK-P1-01` 的 OIDC 接入（需用户先在 Casdoor 注册客户端）。

## 已答复的问题

- `Q-09` 超收 → **不允许超收**（用户 2026-09-21）。不设配置开关，
  由 CHECK 约束与 UPDATE 上界条件双保险。负库存同样禁止（P3 CHECK 约束已硬性保证）。

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
