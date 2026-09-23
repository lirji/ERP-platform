# auth-platform OIDC 接入

2026-09-23 · 切片 P1-OIDC。依据 auth-platform 的《新项目接入指南》《统一登录平台接入手册》，沿用 ERP SECURITY_ARCHITECTURE：认证由 Casdoor 提供，RBAC / DataScope 留在 ERP。

## 已实现的边界

- `GET /login`、`GET /auth/callback`：同源最小登录页，授权码 + PKCE（S256），库负责 state 校验。没有销售/财务业务 UI。
- `GET /auth/config`：公开 `enabled / issuer / clientId / publicBaseUrl`，不含 client secret，禁止缓存。
- 浏览器 `oidc-client-ts` 3.2.1，通过 Maven WebJar 同源提供；无 CDN 依赖，无额外 Node 构建链。WebJar 动态传递依赖排除，浏览器 bundle 已内联所需代码。
- API 验证 RS256/JWKS、精确 issuer、audience、exp/nbf、非空且有界的 sub/owner。JWKS 连接 2 秒、读取 3 秒，使用库的缓存和轮换机制，不自行解析公钥。
- `iam_tenant.(oidc_issuer,oidc_owner)` 显式映射本地租户，`iam_user.external_id` 精确映射稳定 subject。用户名修改不影响身份；请求头不能覆盖；不会根据 JWT name 自动创建/认领用户。
- 每次请求读取本地用户/租户 enabled 和现有角色权限，因此本地离职禁用不等待令牌过期。
- OIDC 与开发身份头不能同时启用；默认二者关闭，业务入口拒绝。既有本机 health/info/metrics 探活口径保留，不能据此开放生产管理端点。
- Token 存 sessionStorage，不输出页面或日志。用户退出清理本地 ERP 会话，不结束其他项目的 SSO，不宣称撤销已签发 token。页面加载遇到过期令牌或 API 401 时，最多刷新并重试一次。

## 本地开通与连接

连接 ID：`erp-local-casdoor`。本地 issuer `http://localhost:8000`，独立 client/org 均为 `erp-platform`，常规 ERP origin `http://localhost:8500`。

1. 在 auth-platform 执行 `python3 deploy/erp-platform-provision.py`。脚本仅操作本地 ERP 命名空间，允许回调 `http://localhost:8500/auth/callback`；隔离联调可用 `ERP_PUBLIC_BASE_URL=http://localhost:18500`。重跑不重置密码，已有用户/应用不匹配时拒绝覆盖。
2. 凭据在 `~/.config/erp-platform/oidc-local.json`（0600），由 auth 管理，含本地验证账号、稳定 sub 和 client secret。真实值不进入 Git、报告或 CI。浏览器及 ERP 验签不需要 client secret。
3. 先启动新版 ERP，Flyway 应用 V121（只增加租户身份绑定字段/约束；旧版本可共存）。已有本地演示数据由 `test-data/init-test-data.sh` 管理。
4. 显式 crosswalk 使用 `deploy/sql/bind-oidc-identity.sql`，给 psql 传入 `tenant_code / username / issuer / owner / subject`。演示目标为 `ERP_DEMO_P11 / demo_operator`，subject 从受控凭据读取。脚本在事务中锁定目标，重复绑定幂等，冲突拒绝且回滚，不允许自动覆盖。
5. `.env` 配置 `ERP_OIDC_ENABLED=true`、`ERP_OIDC_ISSUER=http://localhost:8000`、`ERP_OIDC_CLIENT_ID=erp-platform`、`ERP_PUBLIC_BASE_URL=http://localhost:8500`。宿主 JVM 的 `ERP_OIDC_JWKS=http://localhost:8000/.well-known/jwks`；Docker Desktop 容器改为 `http://host.docker.internal:8000/.well-known/jwks`。issuer 始终是令牌中的浏览器地址，不能改成容器内部地址。
6. 访问 `http://localhost:8500/login`。登录成功后页面显示真实 `/api/v1/iam/me` 的权限上下文，不是硬编码 Mock。

演示清理会删除演示账号，再初始化后必须重新执行显式 crosswalk；不能把历史用户名自动继承外部身份。

## 验证与恢复

- `mvn -q clean verify`：真实 PostgreSQL + RSA/JWKS/真实 HTTP 大令牌测试；CI 无需连接共享 Casdoor，也不携带本机凭据。
- `scripts/oidc-browser-smoke.cjs`：本地 Chrome/Playwright 真 Casdoor 登录、PKCE、refresh、API、未绑定拒绝、无效回调和本地退出。通过外部已安装的 Playwright 执行，例如 `NODE_PATH=<playwright 所在 node_modules> ERP_SMOKE_BASE=http://localhost:8500 node scripts/oidc-browser-smoke.cjs`，默认目标 18500。无权访问生产环境。
- 代码回退保留 V121 新列兼容旧版；不要删除历史迁移或清空数据库。关 `ERP_OIDC_ENABLED` 只会关闭认证接入、业务入口继续拒绝，不能开启开发头作为生产兜底。
- 冲突绑定需要人工确认真实身份，不能自动改 sub；租户/用户禁用继续通过本地 IAM 生命周期处理。

## 生产待配置

尚无生产域名和部署目标，未注册虚构的生产回调、未部署生产。上线时须由 auth 注册精确 HTTPS origin + `/auth/callback`，ERP 同步 issuer/JWKS/client/origin 和受控 crosswalk；同时配置入口/管理端点隔离。生产域名联调通过后才能关闭生产验收项。

## 依赖依据

沿用 Boot 3.3.11 管理的 Spring Security 6.3.9，不借本任务升级全仓依赖。Spring 与 oidc-client-ts 均为 Apache-2.0。2026-09-23 OSV 查询：oidc-client-ts 3.2.1 未返回已知公告；spring-security-oauth2-jose 6.3.9 命中 GHSA-cvc6-q2cp-2xhw（issuer 配置误用）。本实现使用 withJwkSetUri 并显式 JwtValidators.createDefaultWithIssuer，错误 issuer 有负向测试，不依赖 discovery 自动校验。此查询不是完整供应链无漏洞保证，原 Boot 维护期风险仍按 ADR-003 管理。

参考：[Spring JWT 验证](https://docs.spring.io/spring-security/reference/6.5/servlet/oauth2/resource-server/jwt.html)、[issuer 公告](https://spring.io/security/cve-2026-22748/)、[浏览器 OIDC 库](https://github.com/authts/oidc-client-ts)、[WebJar](https://central.sonatype.com/artifact/org.webjars.npm/oidc-client-ts/3.2.1)。
