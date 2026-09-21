# Next Actions

> Owner: `project-bootstrap`。本轮停止点已到达，以下动作**需要用户决定或授权**后才发生。

## 1. 需要你回答的（按紧急度）

| # | 问题 | 为什么现在问 | 不答的后果 |
|---|---|---|---|
| 1 | **`Q-01` 数据库：PostgreSQL 16（本方案推荐）还是 MySQL 8.4（提示词建议）？** | Phase 0 内改动成本极低，P3 之后急剧升高 | 按 PG16 继续，ADR-001 标 PROPOSED |
| 2 | **`U-04` 骨架创建方式**：Runtime 是否允许写 `pom.xml`？若否，是否由人工一次性创建 13 模块骨架？ | 已实测 Runtime 不把构建清单归入可写路径，`CS-M0-02` 被 Discovery 阻塞 | P0 的骨架 ChangeSet 无法执行 |
| 3 | `Q-09` 超收是否允许及比例；是否允许负库存 | P4 的守卫条件 | 按"默认禁止、配置可放开"继续 |
| 4 | `Q-02` / `Q-03` 库位精度与是否对接 `wms-platform` | 影响库存边界；当前按"维度预留 + 端口预留"继续 | 按假设继续，回退成本低/中 |
| 5 | `Q-04` / `Q-05` / `Q-06` 租户隔离级别 / 计价方法 / 多币种 | 均有安全假设 | 按 A-10 / A-09 / A-08 继续 |
| 6 | `Q-07` 团队规模与期望周期 | 只影响排期，不影响架构 | Roadmap 不给周期 |

## 2. 需要你授权的

| 动作 | 需要的 grant | 当前状态 |
|---|---|---|
| 进入 P0：建骨架、写代码、跑测试 | `modify_product_code` `modify_tests` `execute_tests` `modify_runtime_files` | **未授予**（本轮为 `READ_ONLY` + `DESIGN_ONLY`） |
| 把 ERP 目录初始化为 Git 仓库并建任务分支 | `git_commit` `git_branch` | 未授予；当前 `erp-platform` 不是 Git 仓库 |
| 本地起 PostgreSQL（复用 `dev-infra`） | `execute_local_commands` | 未授予 |

## 3. 复现本轮的机器判定

```bash
# Router 路由判定
cd ~/.cursor/skills/_protocol
python3 tools/intent-mapper.py "从0规划设计并实现一套企业级ERP系统"
python3 tools/skill-router.py route --context <(cat <<'JSON'
{"protocol":"router-context/v1","task_type":"NEW_PROJECT_BOOTSTRAP","target_stage":"PLAN",
 "requested_tracks":["backend"],"artifacts":{},"grants":["read_repo","write_design_docs"]}
JSON
)

# Planning Gate 判定（对本报告实跑）
python3 - <<'PY'
import json, importlib.util
spec = importlib.util.spec_from_file_location('egp', 'tools/engineering-goal-planner.py')
m = importlib.util.module_from_spec(spec); spec.loader.exec_module(m)
rep = json.load(open('/Users/liruijun/personal/LLM/erp-platform/.engineering/bootstrap/PROJECT_BOOTSTRAP_REPORT.json'))
plan = m.GreenfieldPlanner().plan_from_bootstrap(rep, workspace='/Users/liruijun/personal/LLM/erp-platform')
g = plan['planning_gate']
print(g['verdict'], g.get('status'))
for c in g['checks']:
    print(' ', c['status'], c['code'])
PY
```

## 4. 得到答复后的第一步

由 Runtime / `engineering-orchestrator` 加载 `execution-plan/v1`，从 `M-DISCOVERY`（解决 `U-04`）开始，然后 `CS-M0-01` → `CS-M0-02` → `CS-M0-03` / `CS-M0-04`。
**不要跳过 P0 直接写业务代码**：写产品代码的 ChangeSet 在 Router 处需要 `engineering_baseline` 为 PASS。
