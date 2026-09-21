# 工程约定（八项）

> Owner: P0 Foundation · 状态 `ACTIVE`
> 约定的价值在于**可执行**。下表每一条都标注了它由什么强制；标注"人工 review"的条目，
> 是当前无法机器化的部分，不得因此被当作可选项。
> 提示词第二十二章要求「禁止不同模块形成不同编码风格」。

| # | 约定 | 强制方式 |
|---|---|---|
| 1 | 编码 | ArchUnit + 人工 review |
| 2 | API | 人工 review（P1 起加 OpenAPI 校验） |
| 3 | 错误码 | 人工 review（P1 起加枚举唯一性测试） |
| 4 | 日志 | `TraceIdFilterTest` + 日志 pattern |
| 5 | 事务 | ArchUnit（分层）+ 人工 review |
| 6 | 异常 | 人工 review |
| 7 | 数据库 | `MigrationCommentConventionTest`、`TableOwnershipArchitectureTest` |
| 8 | 幂等 | 唯一索引 + 集成测试（P3 起） |

---

## 1. 编码约定

- 包结构：核心域（inventory / procurement / sales / finance）用六边形 `domain / application / infrastructure / interfaces`；支撑与通用域用 `controller / service / repository / model`。**不混用**——由 `ModuleDependencyArchitectureTest` 的分层规则强制。
- 不为了分层而制造空接口、透传类或提前抽象（开发规范 §设计 1）。
- 命名体现业务含义：`InventoryBalance` 而非 `InvData`；避免 `Util` / `Manager` / `Helper` 这类无语义后缀承载业务规则。
- 优先不可变对象；值对象（`Money`、`Quantity`）必须不可变。
- 中文注释写**为什么**，不复述**做了什么**。

## 2. API 约定

- 风格 REST + JSON，路径 `/api/v1/{context}/{resource}`，如 `/api/v1/inventory/balances`。
  **版本始终显式**（P1.1 契约修正了本条早先的「v1 隐式」写法，理由见 `contracts/CONTRACTS.md` §1.1）。
- 复数资源名、小写连字符；动作类用例用子资源表达：`POST /api/purchase/orders/{id}/approve`。
- 统一响应包装：成功返回数据本体；失败返回统一错误结构（见 §3）。
- 分页统一 `page`（从 1 起）、`size`（上限 200，超出即 400）、返回 `total`。**排序字段走服务端允许列表**，禁止把用户原始输入拼进 ORDER BY（开发规范 §7）。
- 所有写操作接口必须声明幂等语义（见 §8）。
- 版本策略与兼容规则以 [`contracts/CONTRACTS.md`](../contracts/CONTRACTS.md) §1.2 为准（本文件不重复维护，避免两处事实）。

## 3. 错误码约定

- 结构：`ERP-{CONTEXT}-{NNNN}`。**完整错误码表见 [`contracts/ERROR_CODES.md`](../contracts/ERROR_CODES.md)**（单一事实源）。
- 错误响应体：
  ```json
  { "code": "ERP-INV-1001", "message": "可用库存不足", "traceId": "...", "details": {} }
  ```
- **领域异常与系统异常分开**：领域异常携带错误码并映射为 4xx；系统异常统一 5xx 且**不得把堆栈或 SQL 泄漏给调用方**（开发规范 §设计 10）。
- 错误码一经发布不得改变语义；废弃走新增 + 标注。

## 4. 日志约定

- 格式含 `traceId`（P0 已落地）；P10 升级为 JSON 输出。
- 关键业务日志必须可按 `tenantId` / `userId` / `businessType` / `businessId` / `documentNo` / `traceId` 检索（提示词第二十六章）。
- **脱敏**：手机号、银行账号、身份证、价格授权类字段禁止明文进日志。
- 禁止在循环内打 INFO；异常日志必须带上下文（哪张单据、哪个租户），`log.error("失败", e)` 这种无上下文写法视为未完成。

## 5. 事务约定

- 事务边界在**应用服务**（用例）层，不在 Controller，也不在 Repository。
- 一个用例一个事务；**禁止跨 Bounded Context 的大事务**（提示词第三十二/四十六章）。
- 事务内**不做远程调用**——它会把锁持有时间拉长到不可控。跨模块协作走 Outbox 事件。
- 注意 Spring 自调用导致 `@Transactional` 失效；需要时显式拆分 Bean。
- 关键写入必须检查影响行数；乐观锁冲突**不得当作成功**（开发规范 §设计 8）。

## 6. 异常约定

- 分三层：`DomainException`（业务规则不满足，带错误码）、`ApplicationException`（用例编排失败）、系统异常（不可预期）。
- **禁止吞异常**，禁止 `catch (Exception e) { return null; }`，禁止用返回成功掩盖失败。
- 边界统一转换：`@RestControllerAdvice` 是唯一把异常转成 HTTP 响应的地方。
- 外部系统异常必须在适配层转换为本系统语义，不让对方的 DTO 与错误模型污染核心（开发规范 §设计 10）。

## 7. 数据库约定

- **建表必须写表注释与每一个字段注释**（开发规范 §一）——由 `MigrationCommentConventionTest` 强制，缺一即构建失败。
- 迁移用 Flyway，命名 `V{n}__{snake_case}.sql`；**已执行的迁移不得修改**，修正走新迁移。
- 表前缀即数据所有权：`inv_` / `pur_` / `sal_` / `fin_` / `iam_` / `md_` / `apr_` / `doc_` / `rpt_` / `num_`；`erp_` 为平台共享表。跨模块访问由 `TableOwnershipArchitectureTest` 拦截。
- 金额用 `NUMERIC`，禁止 `float` / `double`；数量同理并明确精度与单位。
- 时间统一 `TIMESTAMPTZ`。
- 所有业务表含 `tenant_id` 且查询强制过滤（假设 A-10）。
- SQL 只出现在 Mapper XML；业务层不得拼接 SQL。

## 8. 幂等约定

- 每个写操作明确**幂等键及其作用域**。
- 首选**数据库唯一约束**兜底（如 `fin_account_payable (tenant_id, source_doc_type, source_doc_id)`），而不是只靠应用层判重——并发下先查后写不成立。
- 事件消费幂等：消费方自己保证，不依赖投递方"只发一次"。
- 重试必须有次数上限、退避与截止时间；只对适合重试且已具备幂等保障的动作重试（开发规范 §设计 9）。
