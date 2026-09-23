# P1-OIDC 交付记录

2026-09-23。当前：本地验收、代码发布和 ERP 远程 CI 全部 PASS；本轮接入交付完成。

- ERP 基线 90eb19b；任务分支 feat/erp-oidc-auth。
- auth 基线 e306644；任务分支 feat/erp-oidc-client。先交付 auth 开通工具，再交付 ERP 消费方；没有代码构建依赖，运行时要求客户端/显式绑定已存在。
- 最终 Maven 全量 179 项回归，无失败/错误/跳过；本地 Casdoor/浏览器/refresh/拒绝矩阵、Compose app 和业务监控通过，见 TEST_RESULT-P1-OIDC.json。
- 同一 agent 执行差异复核：没有 username 自动认领、JWT 必校验 issuer/aud/exp/sub/owner、数据库绑定唯一约束与冲突回滚、默认拒绝/配置互斥、凭据不进浏览器配置或 Git；无独立并行评审。
- 原 ERP 工作区 13 个用户文件改动完整保留，未纳入本次交付。
- 生产 HTTPS 域名/回调及生产环境未提供，未部署。完整 ERP 业务前端、销售财务 HTTP、历史财务回填不在本次接入范围。

## Git 与 CI

- auth 实现提交 `f02ff04`，main 合并 `c07741a` 已推送。开通/重复开通及本地真实联调通过；现有 auth CI 未匹配该脚本路径，未冒称运行了远程检查。auth 的 CODEX_PROGRESS.md 被该仓库忽略，已更新本地原工作区记录，没有强制纳入版本控制。
- ERP 实现提交 `23347b0`，main 合并 `a97339f` 已推送。
- [任务分支 CI 35810514966](https://github.com/lirji/ERP-platform/actions/runs/35810514966) SUCCESS，绑定 `23347b0`；已下载 JUnit artifact 核对 179 项回归 + 1 项显式种子验证，失败/错误/跳过均 0。
- [main CI 35810843126](https://github.com/lirji/ERP-platform/actions/runs/35810843126) SUCCESS，绑定 `a97339f`，测试、镜像、健康、重复种子/清理等全部步骤通过。
- 此后只回写本文与进度/测试记录，源码指纹不变；文档路径过滤不会触发新的 CI，不把文档提交冒充已跑的 CI 目标。
- 原 13 项用户改动合并前后逐字节一致（差异 SHA256 `e71934934b30669e58a70194bffaadd5357b1608a192b5379d95dd7660535c02`）。
- 最终本地入口 `http://localhost:8500/login`；隔离验证 JVM 18500 已停止。现有 PostgreSQL 卷和演示业务数据保留。
