# 业务可观测性与告警处置

本地入口：`http://127.0.0.1:8500/actuator/metrics`。只使用现有 Actuator/Micrometer；没有新增 Prometheus/Grafana 容器。应用默认绑定回环地址，Compose 通过回环宿主端口暴露；生产认证仍需独立完成，不把这些管理接口公开到互联网。

执行 `python3 scripts/check-business-health.py` 检查告警（正常退出0，告警/指标不可用退出1）；可由现有调度器定期运行并收集JSON。尚未配置外部告警接收渠道，不宣称会自动通知人员。

| 指标 | 阈值 | 处置 |
|---|---|---|
| erp.outbox.dead | >0 | 查 DEAD 的事件类型/聚合/last_error；按聚合核对来源及下游幂等事实，修复后仅重试指定ID，禁止全表重置或删死信 |
| erp.outbox.oldest.seconds | >30s | 确认调度开启、数据库锁/连接池、下游失败和 next_retry_at；以真实 published_at 验证恢复，不把进程存活当投递成功 |
| erp.outbox.sample.age.seconds | >30s 或未知 | 查探针错误和数据库连接；未知不是健康 |
| erp.integrity.mismatches (inventory) | >0 | 用 scripts/reconcile-inventory.sh 定位来源桶，沿来源单据/流水查漏记或人工改写；保全流水，业务补偿/经审核数据修复，禁止直接把余额覆盖为流水和 |
| erp.integrity.mismatches (ar/ap) | >0 | 对照原单、现金、核销与反核销、红字记录；不得删原现金或核销来凑余额，使用明确补偿并再次对账 |
| erp.integrity.completed.age.seconds | >3600s 或未知 | 完整扫描每轮每1000行一批，默认1s间隔；200万桶理论至少约2000s，需按实际规模设阈值并留余量；查慢查询和 probe.errors，避免加并发压垮数据库 |

探针各批次事务超时5秒，失败保留上次完整结果及其年龄，未完成首轮显示未知；完整一轮后的差异是逐桶观察汇总，不宣称跨全库单一时点快照。只使用 inventory/ar/ap 三个固定标签，单据/租户不进入指标标签。重启后从首批重新对账，不影响业务事实。

业务日志 logger=ERP_BUSINESS_AUDIT，字段 tenantId/userId/businessType/businessId/documentNo/traceId/action；状态及资金成功日志在事务提交后输出。核销/退款的 documentNo 为其关联往来/红字单号，业务记录主键在 businessId；它们没有独立业务编号。无请求追踪上下文时 traceId 为 `-`，不伪造跨操作关联；HTTP 链路沿用 TraceIdFilter。日志不包含账户、业务载荷或原因正文。可按 `tenantId=...`、`documentNo=...`、`traceId=...` 搜索。

慢查询默认阈值500ms，记录 statementId/elapsedMs，SQL 保留在对应 Mapper XML，参数不落普通日志。排查时对具体语句用获授权的测试数据 EXPLAIN；不能把日志阈值当性能承诺。

配置：erp.monitoring.enabled / interval-ms（100–60000，默认1000）/ slow-query-ms（1–60000，默认500）。后台调度池2线程，Outbox与监控不共用单线程；所有指标都来自权威模块端口，不跨业务表。
