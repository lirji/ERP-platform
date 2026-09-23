# 首批业务闭环后端设计增量

Owner：backend-architecture-design。状态：APPROVED。基于既有[模块化单体](../BACKEND_ARCHITECTURE.md)及[选型](../TECH_SELECTION.md)。本文件只补本批数据/事务/安全设计，不更改已发布事件语义，不新增运行中间件。

## 事实与设计决策

事实：`PurchaseOrderService.createOrder/submitForApproval/approve/receive/closeRemaining/cancel`存在；草稿修改/驳回编排需补。`StockPostingService`与`SettlementService`承载库存及应付规则；`FinancialPostingListener`消费v2。`IamController.roles`仍在Controller直接查询，随IAM切片迁回持久化层，保留响应。

设计：HTTP Controller仅协议与参数；应用边界装配可信上下文、主数据引用及授权范围，再调用业务用例。复杂SQL留Mapper XML。业务校验不能只放页面；库存/数量/金额保护仍由事务与数据库兜底。类、公共方法、非显然逻辑按项目规范写中文原因注释。

## 边界和权威数据

| Owner | 权威数据/写入口 | 对外协作 |
|---|---|---|
| erp-iam | 角色、角色权限、组织/公司范围、仓库授权ID、用户角色 | 生成按动作判定的授权范围；不跨模块写md_warehouse |
| erp-masterdata | 单位、SPU、SKU、供应商、仓库及引用登记 | 提供启用状态、公司归属和可信快照查询端口 |
| erp-procurement | 订单/行、收货/行、订单状态及审批业务编排 | 本地事务调用库存/审批端口；写Outbox |
| erp-approval | 审批实例与决策记录 | 判定实例归属和PENDING；采购掌握订单状态 |
| erp-inventory | 库存桶/余额/流水/成本 | 过账及受限查询；不读取应付表 |
| erp-finance | 应付及既有财务事实 | 从v2幂等生成应付，按来源提供只读查询 |
| erp-kernel + erp-app | 命令幂等机制与HTTP接线、错误、trace | 仅技术元数据，不建立跨域业务写模型 |

应用层跨模块调用沿既有依赖方向；IAM校验仓库ID通过app组合层调用主数据端口，避免iam↔masterdata循环依赖。采购、库存、财务分别拥有查询SQL，app仅组装协议，不把跨表SQL放Controller。

## 安全模型：按动作合并角色

新业务入口不能沿用“最大范围枚举”表达多个不相包含的范围。对请求权限p，先筛选拥有p的启用角色；每角色生成一项范围表达式；有效范围是这些表达式的并集，外层恒加tenant过滤。不能先取所有角色权限并集、再取所有范围并集后交叉配对，防止“广范围只读角色”扩大“窄范围写角色”。

组织/公司：SELF、DEPT、DEPT_AND_BELOW沿原含义；SPECIFIED_ORG首批按既有SQL实现的指定子树前缀定义（末尾分隔符保护），作为待批准澄清，不声称旧文档IN与前缀完全等价；SPECIFIED_COMPANY为显式公司集合；ALL仅本租户。指定集合不得空；角色被禁用不参与。没有任何适用角色时false，不降级到部门范围。

仓库：每角色仓库模式为NONE/SELECTED/ALL；默认NONE。涉及采购/收货/库存的角色必须显式配置；表达式为该角色的组织/公司谓词 AND 该角色仓库谓词，再跨角色OR，禁止分别并集扩大组合。ALL指本租户且满足该角色主体范围的仓库，并非跨租户。修改公司归属/仓库归属不能借用旧授权。

库存余额是仓库聚合，不具有可靠的created_by/org_path。只有携inventory:balance:read且范围为SPECIFIED_COMPANY或ALL的角色可查询聚合，并同时受仓库集合约束；SELF/DEPT角色默认拒绝该聚合读，不能虚构“本人库存”。流水查询使用同一仓库/公司边界，来源单据详情仍独立判权。应付按自己的公司/组织/创建人字段过滤；从应付跳到收货单须再判采购权限，不通过链接泄漏全文。

新Endpoint逐方法显式权限，不用“登录即放行”兜底；SQL过滤覆盖列表、详情、总数、选择器；命令锁定目标并验证同一范围。更新与权限撤销竞争的线性化：写命令在事务中获取IAM租户授权版本共享锁并重读有效角色，授权修改获取排他锁/递增版本；由提交顺序界定在途命令，不宣称可以撤销已提交效果。首批可用租户级短事务锁，代价是IAM修改等待在途命令；不得在锁内等待远程OIDC或Outbox消费。后续按实测再优化。

既有`/iam/me`和`/iam/roles`保持公开响应兼容；me.dataScope为历史摘要，不作为新业务接口授权依据。新增授权服务/表达式不会把旧构造器强制改成不兼容签名；迁移后的实际角色组合须同时回归旧测试和新HTTP场景。

管理员：本批普通角色维护要求既有角色权限点 AND 受保护`iam:security:admin`，该权限不可由UI授权、移除或自举。普通角色权限从固定catalog允许列表选取。对受保护角色、跨租户用户/组织拒绝写；初始化绑定走受控工具，不新增公开bootstrap接口。日志记录变更差异与操作者，不记录身份令牌。

## 本地事务与失败语义

- 草稿创建/修改：读取有效主数据、注册引用、写订单/明细及命令结果同事务；快照只由服务端生成。编辑仅DRAFT且无收货；保留单据号，历史审批实例不覆盖。金额/单位/币种沿现有规则，业务日期使用AccessContext.timezone而非LocalDate.now默认时区。
- 提交：锁订单及expectedVersion，完成DRAFT→SUBMITTED→APPROVING并建PENDING实例；所有步骤同事务。新的审批实例对应当前草稿，旧实例永久保留。
- 审批：先锁订单、检查版本及状态，再验证实例tenant/type/businessId/PENDING、当前用户资格与submitted_by。通过或驳回同时变更实例和订单；驳回回DRAFT。批准不能只调用通用审批接口留下采购状态不同步。所有竞争固定订单→实例锁序。
- 取消：仅状态机允许且无收货；若有PENDING实例，同事务转WITHDRAWN，禁止遗留可审批孤儿。审批/取消并发只能一方成功。
- 收货：先锁订单与版本、确认APPROVED/PROCESSING/PARTIAL_FINISHED；同一请求行ID不得重复，数量>0，来源行属于订单、非closed且未超收；处理批次规则。写收货、条件累加、库存过账、状态与v2 Outbox原子提交。幂等保护覆盖“创建收货行之前”。
- 关闭剩余：限PROCESSING/PARTIAL_FINISHED，原收到数量/金额不变，剩余关闭并转CLOSED。已批准但零收货走cancel，不偷偷新增状态迁移。
- 应付生成：沿用异步Outbox与finance来源唯一键。AP暂缺不是收货失败；来源模块和finance分别提供端口，app聚合状态，不跨模块写表。

## HTTP命令幂等

新增持久化命令登记，归kernel技术机制所有：唯一(tenant_id, endpoint_key, idempotency_key)，记录actor_id、规范化请求摘要、响应状态/JSON、业务资源ID。摘要包含path、业务入参和expectedVersion，排除token与traceId；不存请求原文及凭据。复用同key须同用户同内容，否则409，不返回其他用户结果。

登记、业务效果和成功响应同本地事务提交。并发同key经唯一约束/锁串行；失败事务回滚，不持久化成功。已提交重试先重新认证和判目标访问权限，再返回首次结果，不能因过期expectedVersion误判失败，也不能在撤权后泄漏首次内容。暂时锁等待/超时返回可诊断冲突或系统错误，客户端保留key；禁止写请求自动生成新key。重放响应沿用原业务状态，traceId响应头标记本次请求，避免伪造链路。

只存成功结果，不缓存400/403/422/500。无保留期限业务依据时不自动清理，避免旧key清理后造成重复效果；记录增长为后续治理问题，不能承诺无限吞吐或默认7天清理。命令幂等不替代业务唯一键与Outbox消费幂等。

## 迁移所有权与兼容

只追加新Flyway文件，不修改V10/V40/V50/V70等已执行迁移。实际版本号在实施前按仓库最新最大版本分配，不预占同一序号。

| 变更 | Owner/Slice | 约束 |
|---|---|---|
| 命令登记、租户授权修订锁行 | kernel/iam，S1 | 初始化既有tenant修订行；唯一键、审计，不含token |
| 普通角色与用户授权版本/保护标记、角色范围详情 | iam，S1/S2串行 | 现有绑定保留，默认deny新增仓库操作；显式回填审核，不默认ALL |
| 缺version的单位/商品/仓库等维护实体 | masterdata，各对应slice | version默认0，旧读写兼容窗口及条件更新检查 |
| 审批/采购新增必要字段与查询索引 | approval/procurement，S9/S10 | 优先复用apr_instance字段；不为角色池引入复杂工作流引擎 |

新表/新字段均有中文注释、租户唯一约束、必要索引，真实PostgreSQL验证新库与旧库升级。每次schema变更一个Owner串行。权限默认收紧需要上线前授权crosswalk；应用回滚时不得丢弃迁移表或已发生采购/库存事实。旧版不认识新授权模型，启用新接口后不能直接以旧版替换对外服务；先关闭新入口并验证兼容回退方案。

## Runtime、验证与未决项

数据库/Outbox/审批同进程，无新增MQ/Redis/配置中心。S0开始构建静态前端到erp-app；后端依赖版本不升级。验证复用ArchUnit、真实数据库事务/并发、MockMvc/真实HTTP与浏览器；既有179项历史结果不代表新行为通过。

应验证跨租户、跨公司/仓库、混合角色、审批自己/串单、请求重放、超收、取消竞争、错误币种/快照伪造、权限撤销与在途命令序列，以及AP延迟/重放。

SKILL_HANDOFF：设计增量完成，内部gate=PASS_WITH_ASSUMPTIONS；新增范围语义/管理权限与HTTP契约待设计确认。未运行迁移/测试/部署。TECH_SELECTION沿用，无新增后端主要技术；前端精确版本由S0核验。
