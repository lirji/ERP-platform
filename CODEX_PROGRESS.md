# Codex Progress

## 任务目标

接手 Claude 会话 6ce8fae5-f198-48db-ae23-48bd5dbc80c8，完成 P5/P6 内部服务 MVP 闭环后按用户授权继续 P7–P11。P8 仅真实复杂审批需求触发。退货采用独立红字及待退款，保留原收付款事实。

## 已完成

- P5/P6 内部采购到付款、销售到收款完成并交付。
- P7 成本、调拨/盘点/调整、退货/红字/退款完成，146 项回归；合并 main 1720e12。
- P9 五类报表快照、可恢复重建及规模测试完成，151 项回归；合并 af71858。
- P10 业务审计、慢查询、对账/积压监控和告警脚本完成，155 项回归；合并 e9e6029。
- P11 员工生命周期缺口补齐、Compose 应用、真实服务演示工具、CI 工作流已实现。
- P11 本地全量 158 项通过，无失败/错误/跳过；容器重建后 7.440 秒健康（已有库/镜像），演示重复初始化/验证/清理/重复清理/重建全部通过，smoke UP 且业务指标 OK。

## 已修改文件

- P11：erp-iam 员工服务/Mapper/V120；EmployeeLifecycleIT、PermissionEnforcementIT 租户隔离修复。
- deploy、.dockerignore、.env.example、test-data、DemoDataSeed、.github/workflows/erp-ci.yml。
- P11 契约、切片、TEST_RESULT 及进度文档。
- 工作树另有三个 finance Mapper/Repository 纯格式改动，来源未确认，保留且不纳入本任务提交。

## 未完成

- P11 分批提交 b1d930d / 671e88e / e735c06 已推送任务分支，CI 35681340638 全绿；待正常合并/推送 main 并核实主分支 CI。
- 回写最终 Gate/交付证据。
- 首次 CI 35681078016 暴露历史 V2 依赖 V30 空库失败；已增加 B30 累计基线和 FreshSchemaMigrationIT，已通过 /tmp/p11-fresh-verify.log 及远程 CI 35681340638。

## 当前问题

- 生产 OIDC 客户端/回调、销售财务 HTTP 入口和前端仍未实现；内部闭环不等同生产/UI 验收。
- 历史 v1 金额不自动猜测回填；未执行生产部署。
- Git HTTPS token 不含 workflow scope；已验证 SSH 可认证，可用正常 SSH push，禁止强推。

## 下一步建议

1. 当前分支 feat/p11-local-delivery，依据上述未完成内容继续。
2. 真实日志 /tmp/p11-full-verify.log、/tmp/p11-seed-*.log、/tmp/p11-smoke.log，统计已落 TEST_RESULT-P11.json。
3. 不重复实施 P7/P9/P10；保留 finance 的三个未提交格式改动，保护本地 erp-pgdata 卷。

## 恢复 Prompt

读取 CODEX_PROGRESS.md，完成 P11 GitHub Actions 实跑和正常 Git 交付，核验真实运行结果并回写文档。无需等待“继续”。只清理带 ERP_DEMO_P11 标记的 990001 演示租户，不清空共享数据库或本地卷。
