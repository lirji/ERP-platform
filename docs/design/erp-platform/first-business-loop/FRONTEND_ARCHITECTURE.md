# 首批业务前端设计

Owner：frontend-architecture-design。状态：APPROVED（React选型方向已由用户确认，其余设计门禁沿BRIEF）。范围与事实见 [BRIEF](BRIEF.md)；所有接口见 [HTTP_CONTRACTS](HTTP_CONTRACTS.md)，不能按页面自行编造 JSON。

## 模式、选型与兼容

Brownfield：已有 JVM 同源 OIDC 静态页，完整前端未实现；按2026-09-23用户选择改用 React + TypeScript + Vite + Ant Design，TECH_SELECTION已同步。React Router 管页面；自定义Hooks + 类型化fetch 管请求，首次不加通用查询缓存或全局业务状态库。会话通过Context + useReducer管理身份/权限与刷新状态，订单金额和库存由服务器决定。尚无Vue业务代码或锁文件，当前只需更新设计与后续脚手架。

| 选择 | 理由 | 未采用方案 |
|---|---|---|
| React/TS/Vite | 用户已确认React，形成类型化SPA工作台 | Vue旧方案已被用户决定替代；无需SEO/SSR，暂不引入Next.js运行服务 |
| Ant Design | React下统一表单、表格、壳层与反馈 | Element Plus属Vue旧方案；Material为第二体系；Tailwind自造ERP控件成本不必要 |
| React Router HashRouter /workbench/#/... | 同源部署无需吞并后端路由；OIDC回调仍为/auth/callback | history需额外回退规则，本批收益有限 |
| fetch封装与局部状态 | 当前列表与表单足够；切换身份时容易彻底清理 | 不先引入第二数据缓存来源 |

选型资料核对日期2026-09-23：官方 [React与Vite](https://react.dev/learn/build-a-react-app-from-scratch)、[Vite指南](https://vite.dev/guide/)、[Ant Design主题](https://ant.design/docs/react/customize-theme/)、[React Router HashRouter](https://reactrouter.com/api/declarative-routers/HashRouter)。官方React文档说明Vite可建立客户端SPA；本项目选择同源静态部署，路由/请求/错误边界由上述明确设计承接。Ant Design通过ConfigProvider统一主题；HashRouter保留工作台hash路由。现无前端锁文件，不能将官网版本写成已安装版本；FBL-S0锁定React/react-dom配套版本、Ant Design与Router兼容版本、Vite及React插件、Node engines与lockfile，验证peerDependencies和构建后记录精确版本。产品不显示技术版本。

## 角色、主作业与路由

统一侧栏按作业分组，顶部身份/公司信息和当前筛选上下文。角色决定可见导航；导航隐藏不是安全边界。首页进入可访问的首个工作队列（审批员优先待办、采购员订单、仓管员可收货订单、管理员角色目录），无权限时显示申请权限说明，不做卡片导航墙。

| route（/workbench/#下） | 页面/主要操作 | 数据契约 | Slice |
|---|---|---|---|
| /iam/roles | 角色查询、新建、权限/数据范围编辑 | C-IAM（S0旧角色列表，S1新目录分页） | S0,S1,S2 |
| /iam/users | 已有用户搜索、分配普通角色 | C-IAM | S1 |
| /masterdata/units | 单位增改启停 | C-MD | S3 |
| /masterdata/products | 商品/SPU增改启停 | C-MD | S4 |
| /masterdata/skus | SKU列表、编辑、引用限制 | C-MD | S5 |
| /masterdata/suppliers | 供应商增改启停 | C-MD | S6 |
| /masterdata/warehouses | 仓库维护、公司归属 | C-MD | S7 |
| /purchase/orders | 搜索、建单、状态筛选 | C-PO | S8 |
| /purchase/orders/new | 明细编辑、保存草稿 | C-PO | S8 |
| /purchase/orders/:id | 快照、明细、审批记录、提交/取消/关闭 | C-PO | S8,S9,S10,S13 |
| /approval/inbox | 可处理待办、批准/驳回 | C-APR | S10 |
| /purchase/orders/:id/receive | 剩余数量、批次、提交收货 | C-RCV | S11 |
| /purchase/receipts/:id | 收货及应付生成状态 | C-RCV | S11,S14 |
| /inventory/balances | 库存桶、可用/预占/在手 | C-INV | S12 |
| /inventory/transactions | 库存流水及来源单 | C-INV | S12 |
| /finance/payables | 应付查询、来源、已核销与红字金额 | C-AP | S14 |
| /finance/payables/:id | 应付详情与授权来源导航 | C-AP | S14 |

组织树、权限目录、已绑定用户、商品/单位/供应商/仓库选择器复用本批显式查询接口；不暗中要求全租户下载。公司和仓库筛选写URL，只是过滤请求，不能设置用户身份或扩大权限。

## 页面配方及状态

- 列表：Ant Form筛选 + Table + Pagination，一项主操作，二级命令收在更多菜单；固定ID作为行key，金额右对齐。
- 单据：Ant Descriptions头部 + Table明细 + 状态标签/审批记录 + 主命令；收货与审批使用专门确认区，避免误把保存当批准。
- 草稿：行号、SKU搜索、数量、单价；客户端校验帮助输入，合计显示服务器保存结果，非权威预览必须标记；驳回后保留原单号，修改后再次提交。
- 收货：默认不自动填满全部剩余量；展示可收量，用户明确填写数量/批次，零数量不发送；提交后固定幂等键直到结果确定。

| 页面族 | loading/empty | error/forbidden | success/accepted | conflict/stale/recovery |
|---|---|---|---|---|
| IAM/主数据 | 骨架；无记录给有权限者创建入口 | 401重新登录；403说明无权限；网络错保留原筛选 | 保存成功后刷新服务器版本 | 409保留编辑内容并重载对比，禁止静默覆盖 |
| 采购草稿/详情 | 首次加载禁命令；不存在显示404 | 字段422就地标注，5xx显示traceId | 201/200仅代表本次命令完成 | 409刷新状态；未决写请求不得换key重提 |
| 待办 | 加载；“暂无可处理采购单” | 403不显示空队列伪装成功 | 处理成功移出队列，刷新单据 | 已被他人处理为409；权限被撤销时刷新身份 |
| 收货 | 加载剩余量；收齐禁提交 | 超收提示数量；未授权404/403 | 收货成功；AP PENDING独立展示 | 重试同key；未确定结果保留页面及人工重试 |
| 库存/应付 | 表格骨架；明确无匹配数据 | 请求失败不是余额为0/应付为空 | 数据是查询时点的事实 | AP未生成显示处理中；FAILED不显示成功 |

表格编码列120–200px、状态88–120px、数量金额88–128px右对齐、名称至少160px弹性展开、日期148–168px、操作88–140px；超宽横向滚动，不压缩数量。长ID提供带标签的复制操作，不复制token。首批无打印/导出/离线写入按钮。

所有状态带文字，不能只靠颜色。业务命令成功不把异步应付生成同步标绿；本批未定义业务202响应，不捏造异步创建接口。未知状态码/枚举只读降级并提示刷新。

## 状态与数据流

URL：页码/排序/状态/companyId/warehouseId；服务器状态：列表、详情、effective actions；本地UI：抽屉/勾选；临时表单：未保存值；会话：me与token生命周期。请求Hooks用AbortController和请求序号处理路由变化/卸载，防止旧公司结果覆盖新结果；useEffect仅加载读数据并有清理，业务写命令只由明确用户事件触发，避免StrictMode重执行Effect造成重复命令。写后重新读详情与受影响列表；不直接给库存加数量。

现有/login和/auth/callback继续用同一issuer/clientId/publicBaseUrl及sessionStorage；工作台使用相同OIDC配置与userStore key。登录成功转受控本地工作台地址，禁止外部returnTo。刷新single-flight、失败一次清会话；GET至多刷新后重试一次，写请求仅携原Idempotency-Key重试；403不盲目刷新token。退出清除当前会话与工作台数据，不宣称全局SSO撤销。

只开放HTML/指纹静态资源匿名下载，所有业务API保持认证授权；SPA壳层可匿名加载不等于业务可匿名调用。沿用Bearer，无token进入URL、日志或localStorage。

## 模块提案与运行集成

`erp-web/src/{app,api,auth,shared,features/{iam,masterdata,purchase,approval,inventory,finance}}`。页面/组件用.tsx，类型和请求用.ts；路由级React.lazy/Suspense和ErrorBoundary提供加载/渲染错误反馈，接口错误仍走页面状态。领域模块不导入彼此私有组件；api类型按契约统一；shared只含通用反馈/金额展示/页面骨架。

FBL-S0即介入runtime-and-deploy：构建静态资产并随唯一erp-app制品发布到/workbench/，Node仅构建时使用，运行无需额外前端服务。Vite本地5173仅可作开发工具，不注册第二生产回调；真实OIDC验收走8500同源产物。CI新增前端lint/typecheck/build及必要行为测试，接续既有Maven/Compose流程；锁文件可重复安装，不使用CDN临时脚本。

## 视觉、设备和无障碍

使用Ant Design唯一体系，通过ConfigProvider的theme.token映射：canvas #EEF1F5、surface #FFFFFF、ink #111827、muted #4B5563、line #CBD5E1；primary #1D4ED8、hover #1E40AF、success #047857、warning #B45309、danger #B91C1C、pending #4338CA、idle #374151；对应填充文字#FFFFFF。colorPrimary/colorPrimaryHover/colorSuccess/colorWarning/colorError/colorInfo分别对应primary/hover/success/warning/danger/pending；colorBgLayout/colorBgContainer/colorText/colorTextSecondary/colorBorder映射画布/作业面/正文/次要文字/边框。状态控件通过统一StatusTag封装使用同一语义token，不默认沿用浅底标签。App/ConfigProvider包裹消息和弹窗，避免静态反馈脱离主题。

圆角6px，字号12/13/16/20px，间距4/8/16px，桌面控件32px，触控44px，轻阴影0 1px 2px rgb(17 24 39 / 12%)，焦点2px solid #1D4ED8。状态标签用实色或饱和描边，不依赖浅底颜色区分。

桌面1280及以上优先；1024横向滚动明细；窄屏允许查询，不承诺PDA收货。表单有label，错误关联字段，键盘可达，弹窗返回焦点，危险命令需明确说明业务效果；不默认自动确认。姓名、手机号、token不进入普通前端日志。金额使用字符串展示和明确币种，数量不经JS浮点写回；演示记录只来自API。

## 下游要求与自检

角色/仓库授权、审批候选资格与HTTP字段由契约负责；本设计不扩大业务范围。版本锁定、实际浏览器兼容、资产匿名白名单的精确范围由S0验证；没有渲染或UI测试通过声明。

SKILL_HANDOFF：status=COMPLETED（设计文档）；gate=PASS_WITH_ASSUMPTIONS（方案内部）；执行仍依赖BRIEF设计确认。无隐含页面API；无第二设计体系；待办、写入未知结果、409和财务投影延迟均有可见状态。
