# Security Architecture

> Owner: `backend-architecture-design` · 覆盖提示词第十二、十三、二十七章

## 1. 逐项判定

| 关注点 | 需要？ | 方案 | 执行点 | 引入阶段 |
|---|---|---|---|---|
| Authentication | ✅ | **INTEGRATE**：`auth-platform` / Casdoor，OIDC 授权码 + PKCE | 网关过滤链校验 JWT | Phase 1 |
| SSO | ✅ | 同上（Casdoor 已支持企业 IdP 联邦） | — | Phase 1 |
| RBAC | ✅ | **BUILD in ERP**：User–Role–Permission，权限点为 `模块:资源:动作` | 服务端注解 + 拦截器 | Phase 1 |
| ABAC | ❌ 不引入 | 当前规则用 RBAC + DataScope 可表达 | — | 出现"按单据金额/状态动态授权"时再评估 |
| Tenant Isolation | ✅ | `tenant_id` 强制参与每个查询与唯一索引；MyBatis 拦截器兜底 | 持久层 | Phase 1 |
| Company Isolation | ✅ | `company_id` 由 `AccessContext` 注入过滤 | 查询层 | Phase 1 |
| Organization Isolation | ✅ | `org_path` 前缀匹配 | 查询层 | Phase 1 |
| Warehouse Isolation | ✅ | 用户可访问仓库集合（授权表），库存查询与过账强制校验 | 查询层 + 领域校验 | Phase 3 |
| Data Permission | ✅ | 本人 / 本部门 / 本部门及子部门 / 指定部门 / 指定公司 / 全部 | **查询层统一施加** | Phase 1 |
| API Permission | ✅ | **唯一安全边界** | Controller 前置拦截 | Phase 1 |
| Menu Permission | ✅ | 仅界面呈现 | 前端 + 后端下发菜单树 | Phase 1 |
| Button Permission | ✅ | 仅界面呈现 | 前端 | Phase 1 |
| Field Permission / 脱敏 | ⚠️ 最小集 | 按敏感级别（银行账号、联系方式）序列化时脱敏 | 序列化层 | Phase 2 |
| Audit | ✅ | 操作日志 + 审计日志 + 业务变更日志 | AOP + 领域事件 | **Phase 1（不后补）** |

## 2. 为什么认证外包、授权自建

| 决策 | 依据 |
|---|---|
| 认证 → `REUSE_EXISTING`（auth-platform / Casdoor） | 身份与 SSO 是 Generic 能力，已有成熟内部平台（`auth-platform/README.md`：Casdoor 身份/SSO + `auth-platform-sdk` Starter）。自建登录/SSO 是纯浪费 |
| 授权（RBAC + DataScope）→ **BUILD in ERP** | ERP 的判权是**结构化组织 RBAC + 数据范围下推 SQL**：一次页面渲染要判几十到几百个权限点，且数据权限必须变成 SQL 的 `WHERE` 条件才能不把全表拉回内存。远程 ReBAC（SpiceDB）强在任意对象 ACL 图，弱在这类结构化批量判权与查询下推。同一权衡在 `oa-platform` 已经做过并选择了本地引擎 |
| 例外 | 若未来出现"任意对象级共享/授权"（如单据级细粒度分享），再评估挂 `auth-platform-sdk`，以开关默认关闭的方式试点 |

**边界**：ERP 只从 Casdoor 取 `subject` 与基础 claims；**组织、角色、权限、数据范围全部留在 ERP**（ACL 隔离，见 `BOUNDED_CONTEXT_MAP.md`）。Casdoor 的 org/group 模型不得进入 ERP 领域模型。

## 3. 判权执行路径

```text
HTTP 请求
  → JWT 校验（Casdoor 公钥）
  → 解析 AccessContext { tenantId, companyId, userId, orgId, orgPath, dataScope, permissionCodes }
  → @RequiresPerm("purchase:order:create")  ← API 权限，唯一安全边界
  → 应用服务
  → 持久层：MyBatis 拦截器注入 tenant_id + company_id + org_path 前缀条件
```

- 菜单与按钮权限只影响前端渲染，**后端不信任前端**：同一权限点在 API 上必须再判一次。
- 数据权限的过滤字段使用**服务端允许列表**；动态表名/列名/排序字段禁止拼入用户原始输入（全局开发规范 §7）。
- `AccessContext` 是值对象，异步线程（Outbox 投递、报表投影）必须显式传播租户与追踪上下文，并在结束时清理。

## 4. 其余安全项

| 项 | 措施 |
|---|---|
| SQL Injection | 全部参数绑定；动态排序走允许列表；SQL 只出现在 Mapper XML |
| XSS | 后端输出转义 + 前端不使用 `v-html` 渲染用户输入 |
| CSRF | 前后端分离 + Bearer Token（不依赖 Cookie 会话），无需 CSRF Token；若引入 Cookie 会话则必须补 |
| Replay Attack | 写接口 `Idempotency-Key`；Token 有效期 + 刷新 |
| Rate Limiting | ⚠️ **MVP 不做**：内部系统、无公网入口。触发条件：开放外网入口或出现租户公平性问题 |
| Sensitive Data | 日志**禁止**打印 password / token / secret / 证件号；机密走环境变量与受控凭据，不入库、不提交 |
| 审计不可篡改 | 审计表只追加，应用账号无 UPDATE/DELETE 权限（数据库级 GRANT 控制） |

## 5. 审计记录内容（提示词第十三章）

`doc_operation_log` / `doc_audit_log` / `doc_business_change_log` 至少记录：

```text
谁 userId + userName(快照)
什么时候 occurredAt (UTC) + 业务时间
操作了什么 businessType + businessId + documentNo + action
修改前 beforeValue (JSONB，仅关键字段)
修改后 afterValue (JSONB)
来源IP clientIp
来源终端 userAgent / channel
关联业务单据 relatedDocuments
追踪 traceId + tenantId + companyId
```

核心业务数据必须做到**可追踪、可审计、可解释**：给定一张单据，能回答「它从哪来、它产生了什么、谁在什么时候改了什么、为什么现在是这个状态」。前三个问题由 `doc_document_link` + 审计日志回答，第四个由状态迁移日志回答。
