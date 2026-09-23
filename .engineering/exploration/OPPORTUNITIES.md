> 日期：2026-09-23；protocol: `capability-exploration-report/v1`；baseline: `engineering-baseline/v1`。
> explorationSummary：现状：业务内核和本地工程验证较完整，面向真实用户的 ERP 产品入口与生产运行闭环仍不完整。优先补齐业务 API/页面、可信授权与主数据管理、生产运行验收；暂不拆微服务或新增无实际需求的中间件。

# Opportunities

P0 表示目标用户试用/生产启用前的准入事项，不表示当前已确认生产事故。各项是候选计划，不是实施授权或正式技术选型。

## Must Fix

### O01 · 业务 API 与工作台

- capability：业务 API 与工作台（G01/G08）
- currentState：服务已实现但无业务入口
- problem：日常操作闭环缺失
- evidence：[E02](EVIDENCE_INDEX.md#e02)、[E03](EVIDENCE_INDEX.md#e03)、[E06](EVIDENCE_INDEX.md#e06)、[E12](EVIDENCE_INDEX.md#e12)、[E20](EVIDENCE_INDEX.md#e20)
- whyNeeded：复用业务规则，把项目交付为可使用产品
- proposedCapability：按采购→库存→销售→财务垂直切片交付接口/页面/契约与 E2E
- value：businessValue=可开展真实业务；engineeringValue=验证协议边界与可维护性
- complexity：高
- risk：把内部信任参数暴露到客户端
- dependency：O02、O03
- class：`MUST_FIX`
- priority：`P0`
- triggerCondition：计划交付给真实业务用户
- status：`RECOMMENDED`

### O02 · 数据权限与可信服务端装配

- capability：数据权限与可信服务端装配（G03）
- currentState：指定范围降级、仓库权限与表覆盖未完整
- problem：接入页面后会放大授权遗漏
- evidence：[E07](EVIDENCE_INDEX.md#e07)、[E16](EVIDENCE_INDEX.md#e16)、[E17](EVIDENCE_INDEX.md#e17)、[E31](EVIDENCE_INDEX.md#e31)
- whyNeeded：授权必须先于可用业务入口
- proposedCapability：实现角色范围明细/有效集合；服务器读取额度及快照；验证读写及报表权限
- value：businessValue=防止跨公司/仓库访问与业务绕过；engineeringValue=建立真实入口负向测试
- complexity：中高
- risk：范围合并语义改变现有可见数据
- dependency：主数据权威与现有 IAM 规则
- class：`MUST_FIX`
- priority：`P0`
- triggerCondition：开放任一新业务接口或跨组织用户
- status：`RECOMMENDED`

### O03 · 主数据与 IAM 管理

- capability：主数据与 IAM 管理（G02/G04）
- currentState：SKU/员工服务及基础表存在
- problem：供应商客户等缺管理闭环
- evidence：[E04](EVIDENCE_INDEX.md#e04)、[E05](EVIDENCE_INDEX.md#e05)、[E34](EVIDENCE_INDEX.md#e34)
- whyNeeded：消除依赖手工改库的日常操作
- proposedCapability：先供应商/客户/仓库/单位与用户角色，再按使用场景补其他资料
- value：businessValue=管理员可自行维护数据；engineeringValue=权威校验与引用快照一致
- complexity：中高
- risk：历史引用被修改、启停影响在途单据
- dependency：现有权限/审计；O02 联合实施
- class：`MUST_FIX`
- priority：`P0`
- triggerCondition：开始真实录单与多人协作
- status：`RECOMMENDED`

### O04 · 生产运行准入

- capability：生产运行准入（G09/G10/G11）
- currentState：本地 OIDC/容器/指标已验收
- problem：生产身份、管理面、恢复与通知缺实证
- evidence：[E18](EVIDENCE_INDEX.md#e18)、[E21](EVIDENCE_INDEX.md#e21)、[E22](EVIDENCE_INDEX.md#e22)、[E26](EVIDENCE_INDEX.md#e26)、[E30](EVIDENCE_INDEX.md#e30)
- whyNeeded：真实数据上线需要运行责任闭环
- proposedCapability：auth 项目生成生产客户端回调；ERP 消费配置；隔离指标；恢复演练、告警送达及回退验收
- value：businessValue=降低中断和数据不可恢复风险；engineeringValue=部署证据可审计
- complexity：中高
- risk：环境操作需隔离目标，发布授权单独确认
- dependency：真实 HTTPS 域名/目标环境、备份设施与责任人
- class：`MUST_FIX`
- priority：`P0`
- triggerCondition：上线或保存需恢复承诺的真实数据前
- status：`BLOCKED`

### O09 · 历史事实核对与回填

- capability：历史事实核对与回填（G12）
- currentState：v2 新链路完整
- problem：v1 金额和成本事实未知
- evidence：[E13](EVIDENCE_INDEX.md#e13)、[E30](EVIDENCE_INDEX.md#e30)
- whyNeeded：不能把缺金额当零或重复生成账务
- proposedCapability：先只读盘点历史数据，可信来源映射，分批幂等回填及数量金额对账
- value：businessValue=避免迁入不完整往来账；engineeringValue=保留来源和恢复检查点
- complexity：高
- risk：伪造金额、重复账务、覆盖原记录
- dependency：历史数据存在性、可信金额来源及业务核对
- class：`MUST_FIX`
- priority：`P1`
- triggerCondition：迁入 v1 历史业务数据
- status：`NEEDS_MORE_EVIDENCE`

### O13 · 验收基线与能力状态对齐

- capability：验收基线与能力状态对齐（G16）
- currentState：历史 Gate 与范围已变化
- problem：阶段完成易被误读为产品完整
- evidence：[E24](EVIDENCE_INDEX.md#e24)、[E27](EVIDENCE_INDEX.md#e27)、[E28](EVIDENCE_INDEX.md#e28)、[E30](EVIDENCE_INDEX.md#e30)
- whyNeeded：后续切片应建立可信起点
- proposedCapability：以最终 HEAD 及当前工作区为基线，下一实施切片执行必要验证；维护当前能力与证据索引，历史 Gate 保留
- value：businessValue=明确交付剩余项；engineeringValue=避免旧结论覆盖新代码
- complexity：低中
- risk：误提交用户改动或抹掉历史证据
- dependency：变更归属确认、后续开发切片
- class：`MUST_FIX`
- priority：`P1`
- triggerCondition：恢复开发或形成下一次交付结论
- status：`RECOMMENDED`

## Natural Evolution

### O05 · 审批操作闭环

- capability：审批操作闭环（G05）
- currentState：已有内置决策服务
- problem：缺待办与分配
- evidence：[E14](EVIDENCE_INDEX.md#e14)、[E15](EVIDENCE_INDEX.md#e15)
- whyNeeded：人员必须知道谁处理什么
- proposedCapability：补简单任务归属、待办、处理资格、驳回反馈与业务联动
- value：businessValue=审批可实际协作；engineeringValue=保留 ApprovalPort 并验证并发/重复决策
- complexity：中
- risk：自审、错单、越权审批
- dependency：O02、O03、O01 的审批入口
- class：`NATURAL_EVOLUTION`
- priority：`P1`
- triggerCondition：采购/销售交付多人使用
- status：`RECOMMENDED`

### O06 · 预占滞留治理

- capability：预占滞留治理（G06）
- currentState：取消可释放、无自动到期
- problem：计划中回收尚未实现
- evidence：[E09](EVIDENCE_INDEX.md#e09)、[E10](EVIDENCE_INDEX.md#e10)、[E28](EVIDENCE_INDEX.md#e28)
- whyNeeded：避免长期无效订单锁住可用库存
- proposedCapability：先确认订单有效期；再以有界任务、幂等、并发协调实现回收及审计
- value：businessValue=释放无效占用；engineeringValue=可验证出库/取消/回收竞争
- complexity：中
- risk：误释放有效承诺库存
- dependency：订单到期与续期业务规则
- class：`NATURAL_EVOLUTION`
- priority：`P1`
- triggerCondition：存在失效订单或承诺自动到期
- status：`NEEDS_MORE_EVIDENCE`

### O07 · 采购申请转单

- capability：采购申请转单（G07）
- currentState：只有申请表
- problem：缺完整内控链
- evidence：[E27](EVIDENCE_INDEX.md#e27)、[E28](EVIDENCE_INDEX.md#e28)
- whyNeeded：满足先申请再采购场景
- proposedCapability：实现明细、审批、合并转单、剩余量与引用追踪
- value：businessValue=支持内控；engineeringValue=复用状态/编号/审批机制
- complexity：中
- risk：多申请转单重复或超量
- dependency：O03、O05、采购入口
- class：`NATURAL_EVOLUTION`
- priority：`P1`
- triggerCondition：企业要求申请先行
- status：`DISCOVERED`

### O08 · 文件导入导出与附件

- capability：文件导入导出与附件（G08/G13）
- currentState：内部批量/快照端口已有
- problem：无用户文件操作
- evidence：[E04](EVIDENCE_INDEX.md#e04)、[E20](EVIDENCE_INDEX.md#e20)、[E28](EVIDENCE_INDEX.md#e28)
- whyNeeded：批量录入、凭证归档需求
- proposedCapability：先 CSV/Excel 校验下载流程；附件复用 dev_infra 存储并继承单据权限
- value：businessValue=减少人工录入，保留凭证；engineeringValue=任务有界、错误可追踪
- complexity：中
- risk：导出泄漏、文件权限与恶意上传
- dependency：O02、O03、O01；附件存储范围
- class：`NATURAL_EVOLUTION`
- priority：`P1`
- triggerCondition：用户需要批量录入或合同/发票凭证
- status：`DISCOVERED`

### O12 · 供应商对账与销售报价

- capability：供应商对账与销售报价（G14）
- currentState：应付和订单基础已有
- problem：业务增强未实现
- evidence：[E28](EVIDENCE_INDEX.md#e28)、[E06](EVIDENCE_INDEX.md#e06)、[E12](EVIDENCE_INDEX.md#e12)
- whyNeeded：仅真实协作流程需要时补
- proposedCapability：对账差异走调整单；报价转单重新校验
- value：businessValue=增强采购/销售协作；engineeringValue=复用已有金额与状态机制
- complexity：中
- risk：对账后改账、过期报价仍沿用价格
- dependency：O01/O03 先可用，业务规则明确
- class：`NATURAL_EVOLUTION`
- priority：`P2`
- triggerCondition：存在明确对账或报价客户需求
- status：`DISCOVERED`

## Platformization Opportunities

### O10 · 轻量运维操作与数据生命周期

- capability：轻量运维操作与数据生命周期（G10/G15）
- currentState：CLI、死信、报表检查点已存在
- problem：分散操作缺责任和保留政策
- evidence：[E19](EVIDENCE_INDEX.md#e19)、[E20](EVIDENCE_INDEX.md#e20)、[E21](EVIDENCE_INDEX.md#e21)
- whyNeeded：运营需求出现后减少高风险手工步骤
- proposedCapability：在现有 ERP 内统一查询重建进度、指定死信处理及操作审计；明确归档范围
- value：businessValue=异常可追踪处理；engineeringValue=复用现有端口，避免独立平台
- complexity：中
- risk：错误重放或过早清理
- dependency：运营频率、O02、业务保留依据
- class：`PLATFORMIZATION`
- priority：`P2`
- triggerCondition：已有多人运维且重复操作成本明确
- status：`DISCOVERED`

## Exploration Opportunities

### O11 · 容量、混合负载与公平性验证

- capability：容量、混合负载与公平性验证（G17）
- currentState：报表基准已有
- problem：整系统真实基线未确定
- evidence：[E19](EVIDENCE_INDEX.md#e19)、[E25](EVIDENCE_INDEX.md#e25)、[E26](EVIDENCE_INDEX.md#e26)
- whyNeeded：用测量决定下一步架构
- proposedCapability：从真实流量模型补热点并发、故障恢复、租户竞争与持续刷新实验
- value：businessValue=给容量承诺提供依据；engineeringValue=有证据地定位瓶颈
- complexity：中
- risk：合成数据和线上模式偏离
- dependency：目标用户数/负载与隔离测试环境
- class：`EXPLORATION`
- priority：`P2`
- triggerCondition：容量承诺、多实例上线或观测到瓶颈
- status：`NEEDS_MORE_EVIDENCE`

### O14 · AI 只读业务辅助

- capability：AI 只读业务辅助（AI）
- currentState：未发现 AI 能力
- problem：尚无明确问题或评测集
- evidence：[E01](EVIDENCE_INDEX.md#e01)、[E29](EVIDENCE_INDEX.md#e29)
- whyNeeded：目前没有需要 AI 才能解决的阻塞
- proposedCapability：未来可研究权限内文档/单据辅助查询，建立事实引用和离线评测
- value：businessValue=价值待需求验证；engineeringValue=探索不侵入交易内核
- complexity：中高
- risk：越权检索、幻觉金额及提示注入
- dependency：业务需求、授权数据、评测与成本预算
- class：`EXPLORATION`
- priority：`P3`
- triggerCondition：传统检索确实不能满足且具备评测数据
- status：`NOT_RECOMMENDED_NOW`

## Not Recommended Now

| 建设项 | 暂缓原因 | 重新评估触发条件 |
|---|---|---|
| 微服务拆分、独立文件/通知/任务平台 | 当前缺口主要是产品入口；已有模块边界，没有独立容量/组织证据 | 明确独立扩缩、故障或交付边界并有量化收益 |
| Redis、多级缓存、ES | 尚未证明当前查询需要；授权、库存和资金不能靠陈旧缓存裁决 | 查询基线证明瓶颈且一致性边界可定义 |
| Kafka/RabbitMQ 替换进程内 Outbox | 已有可靠落库和重试，无实际跨进程消费者需求 | 外部订阅、吞吐/顺序/积压目标明确 |
| workflow-platform 高级工作流（原 P8） | 简单审批待办尚未产品化；会签/加签等未触发 | 具体多级、会签、转交业务被确认 |
| 通用配置中心和超收开关 | 与当前不允许超收的明确业务决策冲突 | 新业务明确改变规则并完成兼容及审批设计 |
| 总账、凭证、税务、银行、多币种折算 | 原系统边界明确后置，无真实对接对象 | 正式扩大产品范围并获得规则/契约 |
| AI 自动审批/自动修改库存金额 | 无需求/评测，交易风险高 | 即使探索也先限只读辅助，保留确定性业务规则 |

附件、报价、采购申请都应按实际业务触发；不能把“能力目录里出现过”当成必须一次全部上线的授权。
