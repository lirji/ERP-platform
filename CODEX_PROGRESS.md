# Codex Progress

## 任务目标

按已批准FBL方案连续交付React采购工作台：权限→主数据→采购→角色待办审批→收货→库存/应付。生产OIDC由auth项目负责；本任务不部署生产。

## 已完成

- D0已批准；React、角色池审批且禁止自审为已确认决定，无需重复确认。
- S0 React工作台、真实角色查询、既有OIDC共享会话与刷新/退出、Maven/Docker/CI前端构建已实现。
- 前端8项行为测试通过；Maven全量180项通过；npm audit无已知漏洞。
- 真实Casdoor登录、令牌刷新、键盘刷新角色、退出验证通过；最终镜像复验通过。

## 已修改文件

- `erp-web/`；`erp-app/pom.xml`及工作台路由、安全资源白名单、登录入口和HTTP测试。
- `deploy/Dockerfile`、`.github/workflows/erp-ci.yml`、忽略规则及浏览器smoke脚本。
- FBL设计、工程进度及S0验收/运行文档。

## 未完成

- S0已提交c6d7ce8，分支CI 35815255970通过并正常快进推送main；main CI 35815664759通过。S1角色/功能权限/受保护管理员/持久化幂等及审计均已实现，185项后端+11项前端+真实浏览器通过，等待Git交付；S2A及后续未实现。
- 下一片S2A：指定组织/公司范围配置、按动作合并角色范围与SQL拒绝矩阵。
- 生产HTTPS客户端/回调不在本地闭环验收范围，交由auth项目生成。

## 当前问题

- 无实施阻塞；分支`feat/fbl-business-workbench`，设计提交`3dbbd86`。
- 8500现有容器未替换；18500为本次临时S0验证容器，后台任务关闭。
- 精确依赖见package-lock；Node仅构建使用。Vite主包仍有体积提示，未承诺性能预算。

## 下一步建议

1. 完成S0镜像复验、验收落盘和按授权Git交付。
2. 继续S1→S2A→S2B及后续切片，每片实现/验证/同步进度，不等待“继续”。
3. 保留既有数据库与凭据文件；不输出secret，不强推，不部署生产。

## 恢复 Prompt

读取本文件、.engineering/PROGRESS_STATE.md及docs/design/erp-platform/first-business-loop/IMPLEMENTATION_SLICES.md和HTTP_CONTRACTS.md，从未完成片继续。设计、React与审批规则均已获批准；连续执行，每片测试和Git交付后自动进入下一片。

<details>
<summary>前任务OIDC历史交接（仅历史证据，不是本轮待办）</summary>

# Codex Progress

## 任务目标

P1-OIDC：按 auth-platform 真实对接方案自主完成 ERP OIDC 客户端、回调与本地真实验证。用户已授权两个项目的必要接入操作及正常 Git 交付；未授权生产部署。原 P7–P11 已完成，历史证据见 `.engineering/gates/DELIVERY_RESULT-P7-P11.md`。

## 已完成

- auth 独立客户端/组织 erp-platform，本地账号与精确回调开通、重复开通通过；凭据仅保存在本机 0600 文件。
- ERP resource-server 验签、issuer/aud/时效/sub/owner 校验，V121 显式租户绑定及稳定用户 sub 映射，复用本地 RBAC/DataScope。
- 最小 /login 与 /auth/callback，真实 Casdoor PKCE 登录和绑定身份查询通过；篡改令牌、未绑定身份、错误 state 拒绝。
- 最终 mvn clean verify：179 项通过，失败/错误/跳过均 0；真实 PostgreSQL、RSA/JWKS、Tomcat 大令牌与密钥轮换已验证。
- crosswalk 重复执行通过、冲突拒绝；Docker 镜像构建与 8500 应用健康、业务监控通过。
- 8500 真浏览器 PKCE 登录、refresh 后 API 200、退出、无效回调可重试及未绑定账号拒绝全部通过。
- auth f02ff04 → main c07741a、ERP 23347b0 → main a97339f 已推送；ERP 分支 CI 35810514966 和 main CI 35810843126 均 SUCCESS，源码后的回写仅文档。

## 已修改文件

- erp-app 的安全配置/过滤器、OIDC 登录控制器、静态回调页、依赖、配置和认证测试。
- erp-iam 的 UserAuthMapper/AccessContextAssembler 与 V121。
- deploy/compose.yaml、deploy/sql/bind-oidc-identity.sql、.env.example、scripts/oidc-browser-smoke.cjs。
- 安全架构、P1 契约、选型、docs/security/OIDC.md 与本次计划/验收/交付记录。
- auth-platform：deploy/erp-platform-provision.py、docs/ERP接入.md、接入指南和进度。

## 未完成

本轮 OIDC 接入、验证与正常 Git 交付已完成。
- 生产 HTTPS 域名/回调及生产联调未提供目标，未宣称完成；销售财务 HTTP、完整业务前端、历史 v1 金额回填仍在原产品待办。

## 当前问题

- 无实施阻塞。原 ERP 工作区有 13 个用户改动，全部保留且不纳入交付。
- 实施 worktree：../erp-platform-oidc（feat/erp-oidc-auth）；auth：../auth-platform-erp-oidc（feat/erp-oidc-client）。
- 真实本地数据保留；仅绑定 ERP_DEMO_P11/demo_operator，不清空数据库卷。
- 临时隔离 JVM 18500 已停止；8500 容器为最终本地入口。

## 下一步建议

1. 本轮无需重复实施；入口 http://localhost:8500/login，配置和凭据引用见 docs/security/OIDC.md。
2. 生产环境由实际 HTTPS 域名和部署目标驱动配置/验收；未执行生产部署。
3. 销售/财务 HTTP、完整业务前端及历史财务补账按新的明确范围继续，不自动扩展本任务。

## 恢复 Prompt

读取 CODEX_PROGRESS.md 与 .engineering/gates/DELIVERY_RESULT-P1-OIDC.md。本轮两仓 OIDC 接入与 main 发布、ERP 远程 CI 已完成，不重复开户或重跑实现；保留原 13 个用户改动和 PostgreSQL 卷。按新需求继续，生产域名/环境尚待实际目标，不打印凭据。

</details>
