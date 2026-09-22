# P11 本地运行与全链路交付

- P11.0：修复原计划明确存在的员工→用户链缺口（iam_employee / EmployeeService / 离职禁用验证）。归属 backend-implementation，不借种子工具修改生产规则。
- P11.1：沿用 deploy/compose.yaml 补应用镜像、非root运行、持久化、真实健康检查和本地启动脚本。
- P11.2：独立演示租户的 init/verify/cleanup；1租户、1公司、1部门、1员工/用户/角色、1供方/客户/商品/SKU/仓库/库位，采购→付款和销售→收款各1链，真实服务过账，数据入库。
- P11.3：CI 使用真实 PostgreSQL 验证 P2P/O2C；构建镜像、本地启动、重跑种子/清理/再次初始化、最终 Gate 和 Git 交付。

没有生产部署授权；不新增 Redis/MQ/ES 或前端空壳。OIDC 客户端/生产业务HTTP仍是已记录历史边界。
