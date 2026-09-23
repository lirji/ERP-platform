# ERP React 工作台

S0提供统一登录会话；S1增加角色目录分页、新建、改名/启停和功能权限配置。管理写入要求受保护安全管理员；数据范围及用户授权按S2实施。

## 构建与校验

Node 24.12.0、npm 11；在本目录执行`npm ci`、`npm run lint`、`npm run typecheck`、`npm test`、`npm run build`。
根目录`mvn clean verify`自动安装锁定前端依赖并将dist打进erp-app JAR。Docker多阶段构建中Node仅生成静态资源，最终镜像仍仅含JRE。

## 登录与运行

使用已登记的同源ERP地址`/login`登录，再进入`/workbench/`。工作台复用oidc-client-ts authority/clientId与sessionStorage；令牌失效返回登录，403单独显示。HashRouter刷新不会请求后端业务路由。API仍需服务端鉴权。

`npm run dev`仅用于布局开发，未配置OIDC代理；真实联调使用JAR/容器同源地址（本地8500，隔离验证18500），不能假设5173已登记回调。

根目录`node scripts/workbench-browser-smoke.cjs`默认验证18500，可用`ERP_SMOKE_BASE=http://localhost:8500`切换已登记入口。需本机Chrome及既有0600凭据文件`~/.config/erp-platform/oidc-local.json`；凭据不进入仓库或日志。

S1工作台使用`GET /api/v1/iam/role-directory`真实分页及字符串ID；旧`GET /api/v1/iam/roles`数组仍兼容，旧/me继续校验JS安全整数。写入使用UUID幂等键，网络失败保留键与输入，版本冲突不覆盖他人结果。

管理员初始化使用`deploy/sql/bootstrap-security-admin.sql`，显式给定tenant_code/username且必须已有有效OIDC绑定。脚本不创建身份或输出凭据；普通管理UI不可授予iam:security:admin，也不能编辑受保护角色。
