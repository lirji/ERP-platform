> 日期：2026-09-23；protocol: `capability-exploration-report/v1`；baseline: `engineering-baseline/v1`。
> explorationSummary：现状：业务内核和本地工程验证较完整，面向真实用户的 ERP 产品入口与生产运行闭环仍不完整。优先补齐业务 API/页面、可信授权与主数据管理、生产运行验收；暂不拆微服务或新增无实际需求的中间件。

# Capability Gaps

以下为 capabilityGaps。现有服务、DDL、测试和可供用户使用的能力分开统计；不提供误导性的“完成百分比”。

## G01 · 业务 HTTP 与前端

- capability：业务 HTTP 与前端
- currentState：仅 IAM/登录入口；核心业务是 Java 服务
- targetState：人员可按权限完成建单、审批、收发货、结算、查询
- gap：缺 DTO/校验/错误协议、分页查询、页面、浏览器闭环
- evidence：[E02](EVIDENCE_INDEX.md#e02)、[E03](EVIDENCE_INDEX.md#e03)、[E06](EVIDENCE_INDEX.md#e06)、[E12](EVIDENCE_INDEX.md#e12)；confidence：`FACT`
- impact：业务人员无法日常使用；不能宣称 ERP 产品交付完成

## G02 · 完整主数据管理

- capability：完整主数据管理
- currentState：SKU 服务与多类基础表存在
- targetState：供应商、客户、仓库/库位、单位等可维护且引用受控
- gap：多类实体管理服务/接口/页面及完整校验未齐
- evidence：[E04](EVIDENCE_INDEX.md#e04)、[E05](EVIDENCE_INDEX.md#e05)、[E07](EVIDENCE_INDEX.md#e07)；confidence：`FACT`
- impact：实际运营仍依赖预置数据；销售额度等应由服务器读取

## G03 · 授权与可信请求边界

- capability：授权与可信请求边界
- currentState：租户与 RBAC 基础存在，指定范围被降级
- targetState：指定组织/公司准确生效；仓库/报表/写操作权限覆盖
- gap：角色明细配置、过滤范围、API 权限和可信资料装配不完整
- evidence：[E16](EVIDENCE_INDEX.md#e16)、[E17](EVIDENCE_INDEX.md#e17)、[E31](EVIDENCE_INDEX.md#e31)、[E07](EVIDENCE_INDEX.md#e07)；confidence：`FACT`
- impact：新增接口若机械透传入参，会形成越权或授信绕过风险；尚非已证实外部可利用漏洞

## G04 · IAM 管理工作台

- capability：IAM 管理工作台
- currentState：身份/角色只读、员工生命周期服务存在
- targetState：组织、用户绑定、角色授权可审计维护
- gap：缺完整维护接口与页面
- evidence：[E02](EVIDENCE_INDEX.md#e02)、[E34](EVIDENCE_INDEX.md#e34)、[E16](EVIDENCE_INDEX.md#e16)；confidence：`FACT`
- impact：账号可登录不代表管理员能维护完整业务权限

## G05 · 审批工作台与责任分配

- capability：审批工作台与责任分配
- currentState：提交/通过/驳回、实例状态已有
- targetState：待办归属、处理资格、业务状态联动可验证
- gap：任务分配/查询及前端缺失；复杂会签另行触发
- evidence：[E14](EVIDENCE_INDEX.md#e14)、[E15](EVIDENCE_INDEX.md#e15)；confidence：`FACT`
- impact：无法交给真实审批人操作；先补简单流程而非采购 BPM 平台

## G06 · 预占到期与异常滞留

- capability：预占到期与异常滞留
- currentState：预占/消耗/取消释放已做
- targetState：有明确到期策略并与取消/出库并发协调
- gap：无到期字段、回收任务或相应验收；需先确认哪些订单允许自动释放
- evidence：[E09](EVIDENCE_INDEX.md#e09)、[E10](EVIDENCE_INDEX.md#e10)、[E28](EVIDENCE_INDEX.md#e28)；confidence：`FACT`
- impact：长期未处理订单可能持续占用；不能未经业务规则自动释放有效订单

## G07 · 采购申请

- capability：采购申请
- currentState：pur_request 表已建
- targetState：申请明细→审批→转单/合并，引用可追溯
- gap：没有完整服务与转单链路
- evidence：[E27](EVIDENCE_INDEX.md#e27)、[E28](EVIDENCE_INDEX.md#e28)；confidence：`FACT`
- impact：原 SHOULD_HAVE 尚未交付；不阻断允许直接采购的 MVP

## G08 · 导入导出与报表产品化

- capability：导入导出与报表产品化
- currentState：SKU List 批量校验；快照服务及 CLI 已有
- targetState：文件上传/校验结果/下载、分页快照与权限可用
- gap：缺文件协议、业务入口、导出授权与使用流程
- evidence：[E04](EVIDENCE_INDEX.md#e04)、[E20](EVIDENCE_INDEX.md#e20)、[E02](EVIDENCE_INDEX.md#e02)；confidence：`FACT`
- impact：不可将内部 ReportSource 导出端口等同用户可下载报表

## G09 · 生产身份与部署验收

- capability：生产身份与部署验收
- currentState：OIDC 本地完成，Compose 回环运行
- targetState：真实 HTTPS 域名、auth 生成生产客户端回调、凭据投放及负向验收
- gap：生产环境参数与实际发布证据尚缺；管理面需隔离
- evidence：[E18](EVIDENCE_INDEX.md#e18)、[E22](EVIDENCE_INDEX.md#e22)、[E30](EVIDENCE_INDEX.md#e30)；confidence：`NEEDS_VERIFICATION`
- impact：上线前门槛；不是重新实现 OIDC，也不由 ERP 自行生成 auth 客户端

## G10 · 告警到人及处置闭环

- capability：告警到人及处置闭环
- currentState：有指标、检查脚本、处置说明
- targetState：定期采集、触发、送达、认领和恢复验证
- gap：仓库文档明确未配置外部告警渠道
- evidence：[E21](EVIDENCE_INDEX.md#e21)；confidence：`FACT`
- impact：探针发现异常也未必有人及时处理

## G11 · 备份、恢复及发布回退

- capability：备份、恢复及发布回退
- currentState：有持久化与本地 CI，NFR 目标假设
- targetState：明确备份责任/策略，在隔离目标恢复并验证业务；可回退发布
- gap：未找到本项目恢复演练/生产回退证据，外部平台情况未知
- evidence：[E22](EVIDENCE_INDEX.md#e22)、[E23](EVIDENCE_INDEX.md#e23)、[E26](EVIDENCE_INDEX.md#e26)；confidence：`NEEDS_VERIFICATION`
- impact：不能承诺现有 RPO/RTO 达标；真实数据启用前应补齐

## G12 · 历史 v1 财务/成本治理

- capability：历史 v1 财务/成本治理
- currentState：新链路使用完整 v2，历史无金额事件不能推导财务
- targetState：盘点是否存在历史欠账，按可信凭证回填并对账
- gap：无通用可信回填来源及已完成对账证据
- evidence：[E13](EVIDENCE_INDEX.md#e13)、[E30](EVIDENCE_INDEX.md#e30)；confidence：`NEEDS_VERIFICATION`
- impact：仅当有历史数据需迁入时触发；不得虚构金额或重放制造重复账

## G13 · 附件与通知

- capability：附件与通知
- currentState：初始能力列出，未发现实现
- targetState：附件继承单据权限；通知有收件人及幂等
- gap：附件上传关联、授权下载和业务通知未交付
- evidence：[E28](EVIDENCE_INDEX.md#e28)、[E02](EVIDENCE_INDEX.md#e02)；confidence：`FACT`
- impact：合同/发票附件等真实需求出现再做；优先复用现有平台

## G14 · 供应商对账与销售报价

- capability：供应商对账与销售报价
- currentState：应付/销售订单基础存在
- targetState：对账期确认与差异处理；报价转订单重新校验
- gap：未发现对应服务与流程
- evidence：[E28](EVIDENCE_INDEX.md#e28)、[E06](EVIDENCE_INDEX.md#e06)、[E07](EVIDENCE_INDEX.md#e07)、[E12](EVIDENCE_INDEX.md#e12)；confidence：`FACT`
- impact：原 COULD_HAVE 增强，不是当前全部必须补齐

## G15 · 数据生命周期与任务运营

- capability：数据生命周期与任务运营
- currentState：报表有取消/清理；业务流水需保留
- targetState：分类保留/归档/恢复策略及受控死信、重建操作
- gap：没有全局可验收的保留策略；不能从局部 purge 推定治理完成
- evidence：[E19](EVIDENCE_INDEX.md#e19)、[E20](EVIDENCE_INDEX.md#e20)、[E26](EVIDENCE_INDEX.md#e26)；confidence：`INFERRED`
- impact：长期增长与敏感导出需治理；期限必须来自业务依据

## G16 · 交付事实与能力计划一致性

- capability：交付事实与能力计划一致性
- currentState：阶段 Gate 与初始能力集范围不同
- targetState：按能力标明实现层次、验收版本和当前范围
- gap：旧文档仍有未接 OIDC 或超收可配置描述；当前能力与历史阶段出口未统一映射
- evidence：[E24](EVIDENCE_INDEX.md#e24)、[E27](EVIDENCE_INDEX.md#e27)、[E28](EVIDENCE_INDEX.md#e28)、[E30](EVIDENCE_INDEX.md#e30)；confidence：`FACT`
- impact：阶段 PASS 不能被解读为所有能力完成；应保留历史 Gate 并更新当前索引

## G17 · 生产容量与租户公平性

- capability：生产容量与租户公平性
- currentState：已有专门报表规模测试、资源上限
- targetState：实际混合负载/并发写/热点/多租户/依赖故障测试
- gap：缺覆盖真实场景的容量基线和多实例运行证据
- evidence：[E19](EVIDENCE_INDEX.md#e19)、[E22](EVIDENCE_INDEX.md#e22)、[E25](EVIDENCE_INDEX.md#e25)、[E26](EVIDENCE_INDEX.md#e26)；confidence：`NEEDS_VERIFICATION`
- impact：不可把单项报表性能转换为整系统 SLA；先测再扩容

## Current Risks

优先风险为 G03 的真实用户授权覆盖、G09 的生产管理面边界、G10/G11 的异常响应及恢复能力。当前回环部署不能等同公网暴露；本报告没有确认正在发生的越权、丢数据或账实不符事故。G01 是产品可用性断点。

## Missing Capabilities

业务工作台、完整业务 HTTP、预占到期回收、附件接入、业务通知、报价/供应商对账未形成可操作流程。缺少实现不自动意味着都必须马上建设；范围和触发条件见候选建设项。

## Partial Capabilities

主数据、IAM 管理、指定范围数据权限、审批、采购申请、导入导出和报表使用流程属于部分完成。OIDC、退货、调拨、盘点、结算并非从零缺失，补的是产品入口或环境验收。业务配置 CAP-P11 也不能照旧计划机械实现：不允许超收已有明确决策及数据库约束；应逐项确认真实可变参数，不建立通用配置中心。

## Scaling Gaps

| 场景 | 现有证据与差距 | 触发后动作 |
|---|---|---|
| 当前 | 没有真实业务峰值/并发基线；有合成数据报表基准 | 先建立用户操作闭环、SQL/接口基线与业务 SLO |
| 3x（假设） | 相对基线尚未确定；连接池、热点库存、Outbox 同库竞争需测 | 用同一负载分布测 P95/P99、锁等待、积压和恢复，必要时调查询/批次 |
| 10x（假设） | 多实例调度、持续更新导致快照 STALE、租户竞争未验收 | 验证任务并发恢复、刷新成功率、租户隔离与配额，再决定组件扩展 |
| 100x（假设） | 当前资料不足以证明单体或数据库边界失效 | 先容量实验和成本评估，再决定读写分离、分区/归档或服务提取 |

不能把历史报表测试（200 万库存桶、30 次样本等）解释为写入 TPS、真实租户并发或高可用承诺。缺压测范围不等于没有任何压测。

## Engineering Gaps

新业务入口需要真实身份下的 HTTP/浏览器端到端测试，覆盖越权、重复提交、非法状态、部分履约、核销补偿及可见错误。已有 ArchUnit 和数据库测试继续复用。新增公共接口须配稳定 DTO/契约，不透传内部对象/操作者参数。

局部可维护性问题：`IamController.roles` 直接持有 JdbcTemplate SQL，不符合当前“SQL 集中在持久化层”的约定；随 IAM 管理切片迁回查询持久化层即可，不据此开展全仓重构（E02）。

部署治理、灾备、保留期限、外部告警可能存在于仓库外；本次无远程环境核验，应在交付前索取或生成明确证据，不能按“没搜到脚本”断定组织完全没有这些能力。
