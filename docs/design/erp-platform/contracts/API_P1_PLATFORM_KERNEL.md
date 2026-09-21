# API CONTRACT — P1 Platform Kernel

> Owner: `public-engineering-workflow:contracts` · 状态 `ACTIVE`
> 覆盖 CAP-P01..P04、P06..P11、G01。**CAP-P05 主数据属于 P2**，不在本契约内
> （`ROADMAP` P1 能力列表写作 `CAP-P01..P11`，与 P2 的 `CAP-P05` 重叠；以 P2 为准，此处记录该出入）。
> 跨切面语义（错误、分页、幂等、租户、时间）见 [`CONTRACTS.md`](CONTRACTS.md)，本文件不重复。

---

## 1. AccessContext —— 本阶段最重要的契约

一次请求的**全部**权限事实，由认证与授权装配一次，向下传递，**不在业务代码中重新推导**。

```java
public record AccessContext(
    long          tenantId,      // 租户：只来自已验证令牌，永不来自请求入参
    long          userId,
    long          companyId,     // 当前操作所属法人主体
    String        orgPath,       // 当前用户所在组织路径，如 "/1/23/456/"
    Set<String>   permissions,   // 功能权限点，如 "purchase:order:approve"
    DataScope     dataScope,     // 数据范围
    String        timezone,      // 租户时区（IANA），业务日期归属按它计算
    String        traceId
) { }
```

### 1.1 DataScope

| 类型 | 语义 | 下推为 SQL |
|---|---|---|
| `SELF` | 仅本人 | `created_by = :userId` |
| `DEPT` | 本部门 | `org_path = :orgPath` |
| `DEPT_AND_BELOW` | 本部门及子部门 | `org_path LIKE :orgPath \|\| '%'` |
| `SPECIFIED_ORG` | 指定部门 | `org_path IN (...)` 展开 |
| `SPECIFIED_COMPANY` | 指定公司 | `company_id IN (...)` |
| `ALL` | 全部（仍限本租户） | 不追加组织条件，**但租户条件永远存在** |

**`org_path` 形态**：`/{rootId}/{...}/{selfId}/`，**首尾都带斜杠**。
没有尾斜杠时 `/1/2/` 会前缀匹配到 `/1/23/`——即把兄弟部门的数据判成子部门的。
这是本设计里最容易写错、且错了极难发现的一处，因此形态由约束保证而非约定。

### 1.2 强制规则

1. `tenantId` 与 `dataScope` **只能**来自 `AccessContext`；从请求体接受它们等于把越权做成入参。
2. 数据权限**下推到 SQL**，不在内存过滤。内存过滤在分页下必然出错（先分页后过滤会少数据，先查全量再过滤会拖垮库）。
3. **P1 出口条件**：测试必须断言**生成的 SQL** 中出现 `org_path` 前缀条件，而不是只断言返回的行数正确。
   结果正确可能是数据恰好如此；SQL 正确才证明机制生效。

---

## 2. 认证（CAP-G01）

ERP **不自建认证**，对接 `auth-platform`（Casdoor / OIDC）。ERP 只从令牌取 `subject` 与基础 claims；
**组织、角色、权限、数据范围全部留在 ERP**（`SECURITY_ARCHITECTURE` §边界）。

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/api/v1/iam/me` | 返回当前 `AccessContext` 的可公开部分 + 菜单与按钮权限点 |

`GET /api/v1/iam/me` 响应：

```json
{
  "userId": 4501, "username": "zhangsan", "displayName": "张三",
  "tenantId": 123, "companyId": 9001,
  "orgPath": "/1/23/456/", "orgName": "华东销售部",
  "timezone": "Asia/Shanghai",
  "permissions": ["purchase:order:read", "purchase:order:approve"],
  "dataScope": "DEPT_AND_BELOW"
}
```

> 前端据此置灰按钮。**但前端隐藏不是安全边界**——服务端对每个接口独立判权（`SECURITY_ARCHITECTURE`）。

---

## 3. 组织与用户（CAP-P01、P02）

| 方法 | 路径 | 权限点 | 说明 |
|---|---|---|---|
| `GET` | `/api/v1/iam/orgs/tree` | `iam:org:read` | 组织树；按数据权限裁剪 |
| `POST` | `/api/v1/iam/orgs` | `iam:org:write` | 建组织，`code` 租户内唯一 |
| `PUT` | `/api/v1/iam/orgs/{id}` | `iam:org:write` | 改组织 |
| `PUT` | `/api/v1/iam/orgs/{id}/move` | `iam:org:write` | 移动；**不得移到自己子树下**（`ERP-IAM-2002`） |
| `PUT` | `/api/v1/iam/orgs/{id}/disable` | `iam:org:write` | 停用；有下级或在职员工则拒（`ERP-IAM-2003`） |
| `GET` | `/api/v1/iam/users` | `iam:user:read` | 分页 |
| `POST` | `/api/v1/iam/users` | `iam:user:write` | — |
| `PUT` | `/api/v1/iam/users/{id}/roles` | `iam:user:assign` | 授角色 |

**移动组织的连带影响**：`org_path` 是**物化路径**，移动节点必须同事务更新整棵子树的 `org_path`，
否则数据权限会立刻错判。该操作对子树加锁，并在审计中记录前后路径。

## 4. 角色与权限（CAP-P03、P04）

| 方法 | 路径 | 权限点 |
|---|---|---|
| `GET` | `/api/v1/iam/roles` | `iam:role:read` |
| `POST` | `/api/v1/iam/roles` | `iam:role:write` |
| `PUT` | `/api/v1/iam/roles/{id}/permissions` | `iam:role:write` |
| `PUT` | `/api/v1/iam/roles/{id}/data-scope` | `iam:role:write` |
| `DELETE` | `/api/v1/iam/roles/{id}` | `iam:role:write` |

**权限点命名**：`{context}:{resource}:{action}`，全小写。`action` 取 `read` `write` `approve` `export` `assign`。

## 5. 编号中心（CAP-P06）

| 方法 | 路径 | 权限点 |
|---|---|---|
| `GET` | `/api/v1/numbering/rules` | `numbering:rule:read` |
| `PUT` | `/api/v1/numbering/rules/{businessType}` | `numbering:rule:write` |

**对内接口**（非 HTTP，模块间 Java 接口）：

```java
public interface NumberGenerator {
    /** 取一个单据号。同一 (tenant, businessType, date) 下严格递增、不重复、不空洞。 */
    String next(long tenantId, String businessType, LocalDate businessDate);
}
```

格式：`{PREFIX}{yyyyMMdd}{seq:6}`，如 `PO20260921000001`。

**实现约束（P1 出口条件）**：必须在**真实 PostgreSQL + 真实事务 + 真实并发**下验证。
50 线程并发取号**无重复、无空洞**，并由数据库唯一约束兜底。
纯 Mock 不能证明这一点——它恰恰绕过了要验证的并发语义。

## 6. 单据模型与状态机（CAP-P07）

统一状态集（提示词第十章）：

```
DRAFT → SUBMITTED → APPROVING → APPROVED → PROCESSING
      → PARTIAL_FINISHED → FINISHED
      ↘ CANCELLED   ↘ CLOSED
```

迁移四元组：`(CurrentState, Event, Guard, Action) → NextState`。

| 约束 | 落地 |
|---|---|
| 非法迁移必须拒绝 | `ERP-DOC-2001`，`details.allowed` 给出当前可用事件 |
| 并发迁移只有一个成功 | 乐观锁 `version`，冲突 → `ERP-DOC-2002` |
| 迁移幂等 | 相同事件重复投递不产生二次副作用 |
| 迁移留痕 | 每次迁移写 `doc_state_transition` |

**禁止**在业务代码里写 `if (status == 1)`（提示词禁止事项 7）。状态判断只能经状态机。

## 7. 单据关系图（CAP-P08）

| 方法 | 路径 | 权限点 | 说明 |
|---|---|---|---|
| `GET` | `/api/v1/document/{businessType}/{businessId}/lineage` | `document:lineage:read` | 上下游单据链 |

响应给出 `upstream` 与 `downstream` 两个方向，系统必须能回答：
「这张单来自哪里」与「它后续产生了哪些单」（提示词第九章）。

## 8. 审批（CAP-P10）

业务模块通过 `businessType` + `businessId` 接入，**审批中心不认识任何具体业务模块**。

| 方法 | 路径 | 权限点 |
|---|---|---|
| `GET` | `/api/v1/approval/tasks/mine` | `approval:task:read` |
| `POST` | `/api/v1/approval/instances` | （由业务模块内部调用 `ApprovalPort`） |
| `POST` | `/api/v1/approval/tasks/{id}/approve` | `approval:task:handle` |
| `POST` | `/api/v1/approval/tasks/{id}/reject` | `approval:task:handle` |
| `POST` | `/api/v1/approval/tasks/{id}/transfer` | `approval:task:handle` |
| `POST` | `/api/v1/approval/instances/{id}/withdraw` | `approval:instance:withdraw` |

```java
public interface ApprovalPort {
    String submit(ApprovalRequest request);   // 返回 instanceId
    void   withdraw(String instanceId, long operatorId);
}
```

MVP 为**内置顺序审批**；P8 可切换到 `workflow-platform` 适配器，**内置实现保留为回落**
（ROADMAP P8 出口条件 ①）。端口先行使这次切换不触碰业务模块。

## 9. 业务参数（CAP-P11）

| 方法 | 路径 | 权限点 |
|---|---|---|
| `GET` | `/api/v1/config/params` | `config:param:read` |
| `PUT` | `/api/v1/config/params/{key}` | `config:param:write` |

业务参数进**数据库**（业务人员可维护 + 有审计），技术配置走 env。
同一配置键**不得**有两个权威来源（TECH_SELECTION「配置」行，否决 Nacos）。

P1 需要的最小集：`purchase.over_receipt.allowed`、`purchase.over_receipt.ratio`、`inventory.negative.allowed`
——它们是 P4/P3 的守卫条件，默认值为「禁止」。
