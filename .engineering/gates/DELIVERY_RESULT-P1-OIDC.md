# P1-OIDC 交付记录

2026-09-23。当前：本地验收 PASS，Git/远程 CI 待完成。

- ERP 基线 90eb19b；任务分支 feat/erp-oidc-auth。
- auth 基线 e306644；任务分支 feat/erp-oidc-client。先交付 auth 开通工具，再交付 ERP 消费方；没有代码构建依赖，运行时要求客户端/显式绑定已存在。
- 最终 Maven 全量 179 项回归，无失败/错误/跳过；本地 Casdoor/浏览器/refresh/拒绝矩阵、Compose app 和业务监控通过，见 TEST_RESULT-P1-OIDC.json。
- 同一 agent 执行差异复核：没有 username 自动认领、JWT 必校验 issuer/aud/exp/sub/owner、数据库绑定唯一约束与冲突回滚、默认拒绝/配置互斥、凭据不进浏览器配置或 Git；无独立并行评审。
- 原 ERP 工作区 13 个用户文件改动完整保留，未纳入本次交付。
- 生产 HTTPS 域名/回调及生产环境未提供，未部署。完整 ERP 业务前端、销售财务 HTTP、历史财务回填不在本次接入范围。
