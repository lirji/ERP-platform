# 本地数据库演示工具

前提：Java 21、Maven、已迁移至 V120 的本地 PostgreSQL（`deploy/up.sh` 会迁移）。沿用 `.env` 的 ERP 数据库配置，显式环境变量优先。只允许 local/dev/test 环境和本地数据库地址，拒绝生产命名库；不要把生产库映射成本地目标。

```bash
./test-data/init-test-data.sh
./test-data/verify-test-data.sh
./test-data/cleanup-test-data.sh
```

工具只管理 `tenant_id=990001` 且 `code=ERP_DEMO_P11` 的专用演示租户。ID/标记冲突时停止；不覆盖其他租户数据。清理只删除本工具专用租户，不能用于已混入真实业务的租户。无全库清空、无 Docker 卷删除。会话锁串行化该租户的工具命令；不要在清理期间手工操作该租户业务。

数据包括公司、部门、员工、用户、角色、供方、客户、商品、SKU、仓库和库位。库位作为停用参考，实际库存仍遵守现有 A-11 的默认库位 0。`demo_operator` 是数据库身份，不是可登录 OIDC 账号，不生成或提供虚构密码。

采购 20 件、单价 10，真实审批/收货产生应付 200，付款并核销；销售 5 件、单价 25，真实审批/预占/发货/签收产生应收 125，收款并核销。余额为 15 件、库存成本 150。五类报表从权威数据生成快照。重复 init 不重复生成单据或现金；中断可重跑，遇到人工修改的数据会失败而不覆盖。

`DemoDataSeed` 通过显式 Maven Failsafe 命令执行，普通 `mvn verify` 不运行种子。只发布本租户 Outbox，调用真实内部服务，不跳过业务校验。编号规则先提交供独立取号事务读取；订单和库存业务随后以事务执行。

验证分为数据库数量/成本/账款与内部服务链路。`APP_VERIFY=PASS_INTERNAL_SERVICES` 不表示 HTTP/UI 已实现；`HTTP_BUSINESS=NOT_IMPLEMENTED` 明确当前边界。数据均存数据库，没有页面硬编码 Mock。
