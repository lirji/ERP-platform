# P1-OIDC：补齐 auth-platform 认证链路

状态：DONE。用户 2026-09-23 授权自行查看 auth-platform 对接方案并接入；此前授权的任务分支、验证、正常合并推送适用。涉及 ERP 与明确指定的 auth-platform；仅本地 Casdoor/ERP 验证，不含生产部署。

沿用 P1 认证设计和 auth 接入手册，不引入 SpiceDB、不扩展销售/财务 API 或业务前端。必要附带工作：使真实回调可达的最小登录页、租户显式绑定、可重跑客户端开通及安全验证。

验收：独立 Casdoor 客户端及精确回调；PKCE 真浏览器登录和刷新；受签名/issuer/aud/时间/owner/sub 约束；本地用户/租户禁用与权限拒绝；错误输入不能绑定其他身份；已有全量回归、空库迁移通过；文档、两仓 Git 发布、ERP 远程 CI 完成。

依赖：既有本地 Casdoor :8000、ERP PostgreSQL :45532、已有 demo_operator。生产 HTTPS 域名未提供，生产验证单列，不伪造完成。

验证命令与配置见 docs/security/OIDC.md。证据统一在 .engineering/gates/TEST_RESULT-P1-OIDC.json、DELIVERY_RESULT-P1-OIDC.md。测试阶段保留原工作区 13 个用户修改，实施使用独立 worktree。

验收结果：179 项最终回归、真实 Casdoor 浏览器登录/刷新/拒绝矩阵、两仓 main 发布、ERP 分支/main CI 全部通过。生产待配置项保持独立，证据见上述 Gate 文件。
