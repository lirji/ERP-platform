# Codex Progress

## 任务目标

按已批准FBL计划连续交付React采购工作台：权限→主数据→采购→角色池审批（禁止自审）→分批收货→库存/应付。用户“开始执行吧”已批准全计划，不重复询问继续。生产OIDC由auth项目生成，本任务不部署生产。

## 已完成

- D0批准；S0 React壳层、真实角色查询及OIDC会话；c6d7ce8已推main，分支CI35815255970/main CI35815664759均SUCCESS。
- S1 普通角色/功能权限/受保护管理员、持久化幂等与前后审计；527ee1d已推main，分支CI35816253796/main CI35816711192均SUCCESS。
- S2A 指定组织/公司范围、授权组织森林、按动作合并角色SQL；189项Maven、12项前端及最终JAR真实浏览器通过。验收见TEST_RESULT-FBL-S2A，待提交/CI。
- 本地已有OIDC管理员demo_operator已通过受控脚本初始化；不输出凭据。浏览器测试角色均无用户绑定，失败遗留角色已通过正式接口停用，保留审计。

## 已修改文件

- S2A：erp-iam范围模型/服务/Mapper及V125；erp-kernel ActionScope与SQL片段及旧DataScope拒绝摘要。
- erp-app角色范围/组织HTTP、严格DTO、真实DB/SQL矩阵测试；erp-web范围选择和回显、独立表单标识。
- 浏览器脚本、范围运行说明、错误码、切片与验收进度。S0/S1变化已在上述提交中。

## 未完成

- S2A Git/CI；下一片S2B（已有用户角色分配、授权修订、命令共享锁/授权排他锁、撤权竞争）。
- S3单位→S4商品→S5 SKU→S6供应商→S7A仓库→S7B仓库授权→S8草稿→S9提交→S10A批准/S10B驳回重提→S11收货→S12库存→S13取消/关闭→S14应付→S15全链路/Runtime/CI。
- 全部功能未完成，不将S0–S2A局部通过称为采购闭环交付。

## 当前问题

- 无业务/权限阻塞。任务分支feat/fbl-business-workbench；Git使用已有SSH凭据（HTTPS OAuth缺workflow scope）；不强推。
- 8500原容器保留；18500为本次临时JVM，基于本地erp库运行，后台Outbox/监控关闭。数据库迁移当前V125。
- 凭据仍在本机0600文件~/.config/erp-platform/oidc-local.json，不提交/打印。
- 验证命令使用set -e，逐个检查退出码；曾因测试泛型错误构建失败后继续打包导致旧静态包，已修复并逐文件核对JAR与dist一致，最终浏览器重新通过。
- Vite主包体积提示保留，未承诺未测性能。

## 下一步建议

1. 提交S2A，推任务分支并观察精确提交的CI；通过后正常快进推main，不混入未验证下一片。
2. 连续实施S2B，复用现有管理幂等/审计和ActionScope；员工离职禁用也需参与授权锁协议。
3. 按切片实现→真实验证→文档/进度→Git交付，完成后自动进入下一片。

## 恢复 Prompt

读取本文件、.engineering/PROGRESS_STATE.md、docs/design/erp-platform/first-business-loop/IMPLEMENTATION_SLICES.md、HTTP_CONTRACTS.md与最近TEST_RESULT。从S2A交付/S2B继续，不重新规划全部项目，不等待“继续”。React、角色待办池、禁止自审、普通角色保护、全计划实施与正常Git推main已获授权。保留现有数据库/用户改动，不输出secret，不生产部署。
