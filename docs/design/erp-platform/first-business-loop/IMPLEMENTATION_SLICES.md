# 首批采购业务闭环实施切片

Owner：implementation-slicing。计划ID：FBL，revision 3（用户批准执行，原ID/依赖不变），2026-09-23。
输入：[范围/门禁](BRIEF.md)、[前端](FRONTEND_ARCHITECTURE.md)、[后端增量](BACKEND_DELTA.md)、[HTTP契约](HTTP_CONTRACTS.md)。

**2026-09-23用户“开始执行吧”批准本方案并授权按切片连续实施。** 用户已批准规划范围、审批规则、React选型及新增契约/权限设计。FBL-D0门禁完成；后续每片仍须实现和独立验证才能DONE。既有阶段结果与ID保留。

## 依赖与执行规则

FBL-D0为设计冻结门禁，不是业务实现。下表S0等ID的完整名称均带FBL-前缀。每个用户可观察结果包含必要的后端、对应页面和窄验证；多个Skill依次执行同一切片，不表示一次调用实现全部项目。依赖满足且D0完成后，由进度Owner更新状态。

Runtime现有Java/PostgreSQL/OIDC始终复用；表中“无新增”不表示不依赖现有数据库。所有新增迁移在执行时由唯一Owner分配实际版本号，共享壳层/安全/契约/迁移链未隔离，首批不安排并行Agent。

| ID | 可观察结果 | Needs | Owner及影响路径 | 契约/迁移Owner | 验收 | Pass / Runtime | 状态 |
|---|---|---|---|---|---|---|---|
| FBL-D0 | 新增设计与契约冻结，明确可实施版本 | — | public-engineering-workflow；本目录 | 本批契约；无迁移 | 用户确认或既有有效授权明确覆盖新增规则；文档一致性检查通过 | design review；无运行变化 | DONE（用户开始执行授权） |
| FBL-S0 | 已绑定管理员进入工作台，查看真实角色列表 | D0 | frontend/backend/runtime；erp-web、erp-app/web/iam、erp-iam查询 | 既有GET /iam/roles；无schema变更 | OIDC登录/刷新/退出、真实角色列表、401/403、键盘可达；旧/me与/roles响应不变 | 实现→类型/构建/HTTP/浏览器验证；React/TS/Vite/Ant Design静态资产随JVM构建 | DONE |
| FBL-S1 | 安全管理员创建普通角色并分配功能权限 | S0 | iam/backend/frontend；erp-iam、erp-kernel命令登记、erp-app、erp-web/features/iam | C-IAM Role/role-directory；IAM版本/保护与kernel命令迁移 | 同key只建一个角色；并发修改409；普通用户/保护角色修改拒绝；已有管理员受控初始化验证；管理SQL归持久化层 | 实现→真实DB/HTTP/页面；无新增中间件 | DONE |
| FBL-S2A | 管理员配置指定组织/公司范围，角色详情准确回显 | S1 | iam+kernel上下文+IAM页面 | C-IAM RoleScope；IAM范围明细迁移 | 多角色集合无降级；同权限OR；异权限不交叉扩大；空集合拒绝 | 实现→SQL断言/负向角色矩阵；无新增 | TODO |
| FBL-S2B | 管理员把普通角色分配给已有用户并看到权限生效 | S2A | iam/backend/frontend | C-IAM users；用户授权版本/修订机制 | 角色更改后下一次请求可观察；受保护绑定拒绝；并发授权/命令顺序可解释 | 实现→真实身份/事务竞争/页面；无新增 | TODO |
| FBL-S3 | 维护员维护基本单位 | S2B | masterdata/backend/frontend | C-MD unit；md_unit版本迁移 | 新建/改名/启停；重复编码拒绝；受引用关键字段不可改；所有写幂等 | 实现→DB/HTTP/表单；无新增 | TODO |
| FBL-S4 | 维护员维护商品/SPU，为SKU提供真实选择 | S3 | masterdata/backend/frontend | C-MD product/category read；md_product版本迁移 | 可创建无分类商品；选择有效分类；禁用商品不能用于新SKU | 实现→引用完整性/页面；无新增 | TODO |
| FBL-S5 | 维护员维护SKU并看到引用限制 | S4 | erp-masterdata/SkuService及HTTP/前端 | C-MD sku；既有md_sku复用 | 真实商品/单位选择；编码/单位/批次开关保护；版本冲突及停用行为 | 实现→现有规则回归+HTTP/页面；无新增 | TODO |
| FBL-S6 | 维护员维护供应商 | S5 | masterdata/backend/frontend | C-MD supplier/settlement-method read；按需新增约束 | 新建/启停/查询；引用后改名不改旧单快照；错误或跨租户结算方式拒绝 | 实现→DB/HTTP/页面；无新增 | TODO |
| FBL-S7A | 维护员建立公司所属仓库 | S6 | masterdata/backend/frontend | C-MD warehouse；仓库版本迁移 | 公司必须有效；跨租户/错误组织类型拒绝；历史引用保护 | 实现→DB/HTTP/页面；无新增 | TODO |
| FBL-S7B | 管理员限制角色仓库范围并验证实际选择结果 | S7A | iam+masterdata端口/app组合/前端 | C-IAM仓库范围；IAM明细迁移 | NONE默认拒绝；显式集合正确；角色范围AND仓库后OR，不出现跨角色组合放大 | 实现→授权SQL/选择器/页面；无新增 | TODO |
| FBL-S8 | 采购员保存草稿并查询详情 | S7B | erp-procurement/erp-masterdata、app、web/purchase | C-PO create/read；采购幂等/查询索引 | 编号/本位币配置可用；1–200行；可信快照；重复命令只一单；不同公司/仓库拒绝 | 实现→事务/HTTP/浏览器；复用初始化工具 | TODO |
| FBL-S9 | 采购员提交草稿，看到审批中 | S8 | procurement/approval/backend/frontend | C-PO submit；审批/采购schema按需 | 订单与PENDING实例原子生成；expectedVersion冲突；重试不产生多个实例 | 实现→事务失败注入/页面；无新增 | TODO |
| FBL-S10A | 审批员从角色待办池批准采购单 | S9 | approval/procurement/app/web | C-APR inbox/approve；审批索引 | 提交人无待办且直接自审403；串单实例拒绝；两人竞争一次成功；无孤立批准状态 | 实现→DB并发/SQL/HTTP/页面；无新增 | TODO |
| FBL-S10B | 驳回→修改草稿→重提可操作 | S10A | procurement/approval/app/web | C-APR reject+C-PO update | 驳回回DRAFT，原因可见；原编号/历史实例保留；修改受version保护；重提新实例 | 实现→完整失败恢复路径；无新增 | TODO |
| FBL-S11 | 仓管员分批收货并查看收货单 | S10B | procurement/inventory/app/web | C-RCV；来源金额/批次规则复用 | 不超收；禁止跨单行/重复行；同key仅一收货；审批前拒绝；并发与累计舍入正确 | 实现→真实DB/HTTP/页面；既有Outbox | TODO |
| FBL-S12 | 仓管员核对库存桶及来源流水 | S11 | inventory查询/app/web | C-INV；查询索引 | 每批库存增量=流水；库仓范围正确，聚合数据不错误按created_by过滤；稳定分页 | 实现→DB/越权/页面；无新增 | TODO |
| FBL-S13 | 采购员取消未收货单或关闭剩余 | S12 | procurement/approval/app/web | C-PO cancel/close | 有收货不可取消；取消撤回PENDING；关闭不改已收金额；审批/收货/取消竞争安全 | 实现→状态/并发/页面；无新增 | TODO |
| FBL-S14 | 财务查询员查看由收货产生的应付 | S13 | finance端口/采购来源状态/app/web | C-AP+C-RCV projection | 正常生成、PENDING、DEAD后FAILED、恢复后AVAILABLE；重复事件仅一AP；财务权限独立 | 实现→Outbox故障恢复/HTTP/页面；无新增 | TODO |
| FBL-S15 | 首批完整业务链与可重复本地交付通过验收 | S14 | implementation-validation / runtime / docs / CI | 本批全部；只补必要测试/运行文档 | 真实OIDC多角色完整链、跨租户公司仓库矩阵、种子可重复清理、前端构建、Maven回归及镜像smoke；证据绑定提交 | 独立验证→文档→进度→Git/CI；不生产部署 | TODO |

各片只有实现和验证完成才能DONE；S2/S7/S10子片保持稳定ID。D0已经批准，当前执行S0，然后S1–S2B形成管理员授权闭环。若单片差异过大，在原ID下拆分并记录依赖，不重新规划全部任务。

## 验证与交付责任

每片backend-implementation→frontend-implementation（按实际范围）→implementation-validation；S0运行集成调用runtime-and-deploy。验证需检查真实可观察结果，不仅验证文件存在或fixture服务调用成功。测试数据写隔离数据库，禁止硬编码页面数据。

每片完成后project-documentation同步本批权威文档、update-progress-docs推进状态。开发任务按用户持续授权独立分支、按完整逻辑提交，必要验证通过后正常合并推送main；task-git-delivery/ci-cd-gate负责证据，失败不绕过保护。当前IMPLEMENTATION_AUTHORIZED，用户持续授权正常提交/合并/推送main；不创建release或生产部署。

## 门禁、阻塞及延后

- D0：已获用户批准，无待确认设计门禁。
- S0：React/react-dom、Ant Design、React Router、Vite及React插件/Node版本须实际锁定及验证，不预先宣称兼容构建通过。
- S8：初始化公司/管理员/本位币/编号可用性需在授权隔离环境验证；没有现成值明确报错，不能猜本位币。
- 生产域名/客户端/恢复演练/告警送达由后续运行准入任务处理，不阻塞本地采购闭环。
- 采购申请、预占过期、销售/客户、付款核销、附件与高级工作流不随本表自动进入实现。

SKILL_HANDOFF：protocol=skill-contract/v1；status=COMPLETED（正式实施计划）；gate=PASS；produced=本计划；unresolved=无设计门禁；recommended_next=S0验收后S1。执行进度见PROGRESS_STATE与每片TEST_RESULT。
