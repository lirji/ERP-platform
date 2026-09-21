# IMPLEMENTATION SLICES — P1 Platform Kernel

> Owner: `implementation-slicing` · 状态 `APPROVED`
> 上游：`CONTRACTS`（P1.1）· `DOMAIN_MAP` / `BOUNDED_CONTEXT_MAP`（Phase 1 已产出，**不重做**）
> 纵向切片：每片自成可验证单元，**不按技术分层横切**。

## P1.2 说明

平台内核的领域与架构在 Phase 1 已由 `backend-architecture-design` 产出
（`DOMAIN_MAP.md` §3 明确 `erp-iam` / `erp-approval` / `erp-document` 用四层而非六边形；
`WORKFLOW_STATE_MODEL.md` 定义状态机；`CONSISTENCY_MODEL.md` 定义 Outbox）。
本阶段**不重新设计**，只做切片与实现。

## 切片与出口条件映射

| Slice | 内容 | P1 出口条件 | 依赖 |
|---|---|---|---|
| `S1` | kernel 原语：`Money` `Quantity` `ErrorCode` `DomainException` `AccessContext` | —（全部切片前置） | — |
| `S2` | 认证接入 + 功能权限判权（注解 + 拦截器） | ① ② | S1 |
| `S3` | 数据权限 SQL 下推（MyBatis 拦截器） | ③ | S2 |
| `S4` | 编号中心 | ④ | S1 |
| `S5` | 单据状态机 | ⑤ | S1 |
| `S6` | 业务审计（写入路径 AOP） | ⑥ | S2 |
| `S7` | Outbox 投递、重试、DLQ | ⑦ | S1 |

## 可观察验收（逐条对应 ROADMAP P1 Exit Criteria）

| # | 验收 | 证明方式 |
|---|---|---|
| ① | OIDC 登录拿到 `AccessContext` | 集成测试：带令牌请求 `/api/v1/iam/me` 返回正确上下文 |
| ② | 无权限用户调 API 返回 403 | 集成测试：缺权限点 → 403 + `ERP-AUTH-4001` |
| ③ | `org_path` 数据权限生效，**且 SQL 中确实出现前缀条件** | **断言生成的 SQL**，不只断言结果行数 |
| ④ | 并发 50 线程取号无重复、无空洞 | **真实 PostgreSQL + 真实事务 + 真实并发**（Testcontainers） |
| ⑤ | 非法状态迁移被拒 + 并发迁移只有一个成功 | 单元测试（非法迁移）+ 集成测试（并发乐观锁） |
| ⑥ | 审计含前值/后值/IP/traceId | 集成测试断言 `doc_audit_log` 行内容 |
| ⑦ | Outbox 投递失败可重试并进 DLQ | 集成测试：注入失败投递器，断言 retry_count 递增并最终 `DEAD` |

## 反空跑要求（Vacuous Verification Detection）

P0 已经发生过一次「规则空跑成假绿」（ArchUnit 常量内联）。本阶段沿用同一纪律：

- 凡断言 SQL 的测试，必须先断言**捕获到了 SQL**（捕获为空即失败）；
- 凡扫描类测试，必须断言**扫描到的对象数 > 0**；
- 并发测试必须断言**确实并发**（无重复的同时，断言成功计数 == 线程数）。
