# 首批HTTP契约草案

Owner：public-engineering-workflow:contracts。Revision：FBL-1，状态APPROVED（未声明接口已存在）。用户2026-09-23“开始执行吧”已批准本版本，可以按切片实施。批准后本文件只补充本批新接口；旧接口继续遵循[跨切面契约](../contracts/CONTRACTS.md)、[P1](../contracts/API_P1_PLATFORM_KERNEL.md)、[P2](../contracts/API_P2_MASTER_DATA.md)。不改旧事件、OIDC及已上线响应。

## C-COMMON：协议、类型与命令

前缀`/api/v1`；JSON UTF-8；Authorization Bearer必需。新增接口拒绝未知写字段及tenantId/operatorId/orgPath/supplierRef/skuRef等可信字段。companyId/warehouseId为资源选择，需服务端验证授权和一致性，不是身份声明。

新DTO的ID、version均为十进制字符串；金额/单价/数量为十进制字符串（金额4位、单价/数量6位上限），避免JS精度损失。旧`/iam/me`、`/iam/roles`数值序列化保持原样；前端旧接口适配必须检查安全整数范围，超限停止该操作而不是静默舍入。枚举用稳定code；未知响应枚举只读降级，未知请求枚举400。

- ListQuery：page整数默认1、size默认20上限200；sort仅本接口允许字段，追加id稳定排序；q最多128字符；companyId/warehouseId/status/enabled为相应列表的可选过滤。非法400，越权范围不得被用于扩大结果。
- Page<T>：`{list:T[],total:整数,page:整数,size:整数}`。总数使用同样权限过滤。
- RecordMeta：`id,version,createdAt`；createdAt为带时区ISO8601。无时间字段的旧表需实现时追加或明确DTO不返回，不能伪造时间。
- 本批写命令必填`Idempotency-Key`（UUID），与旧“可选头”是未实现的新端点的本批约束；已发布接口不收紧。修改/状态转换体必填expectedVersion，新建无该字段。
- 新建201 + Location；修改/状态转换200；返回更新后的资源或下述专用响应。重放返回首次HTTP状态和业务响应，不多产生业务效果。详情和命令响应Cache-Control:no-store。
- DTO文本长度遵循对应DDL且下述限制取更严；空字符串不替代必填。路径ID须正数，分页有界；请求单据最多200行。
- GET权限失败：无token401，缺功能权限403，目标不在范围404；列表按允许范围返回集合。命令版本/非法状态/同key不同内容409；业务超量或缺必需配置422；不可预期500且脱敏。
- Error沿既有`{code,message,traceId,details?}`；既有码优先。新增拟登记：ERP-HTTP-3001缺幂等键/400、ERP-HTTP-2001键冲突/409、ERP-HTTP-2002命令处理中/409、ERP-IAM-4010受保护授权/403、ERP-APR-4010不得自审/403、ERP-APR-2002审批归属或状态冲突/409。冻结时与ERROR_CODES校验去重，未登记不能在实现中随意另起同义码。

## C-IAM：管理员操作路径

所有管理写操作额外要求受保护`iam:security:admin`；该权限不出现在普通可授catalog。GET /iam/me与GET /iam/roles保持原响应，不把已有数组改成分页；S0先展示旧角色数组，不伪装服务端分页；S1新增角色目录后切换工作台分页。

| 方法与路径 | 权限 | 请求 | 返回 |
|---|---|---|---|
| GET /iam/role-directory | iam:role:read | ListQuery，sort=code/name/id | Page<Role> |
| GET /iam/roles/{id} | iam:role:read | — | Role |
| POST /iam/roles | iam:role:write | RoleCreate | 201 Role |
| PUT /iam/roles/{id}/permissions | iam:role:write | expectedVersion, permissions:string[] | Role |
| PUT /iam/roles/{id}/data-scope | iam:role:write | expectedVersion, scope:RoleScope | Role |
| PUT /iam/roles/{id} | iam:role:write | expectedVersion,name,enabled | Role |
| GET /iam/permissions | iam:role:read | — | Permission[]（固定有界目录） |
| GET /iam/users | iam:user:read | ListQuery，sort=username/id | Page<UserSummary> |
| PUT /iam/users/{id}/roles | iam:user:assign | expectedVersion,roleIds:string[] | UserSummary |
| GET /iam/orgs/tree | iam:org:read | — | OrgNode[]；仅可管理/引用范围，服务端防无界树 |

scopeState为VALID/UNCONFIGURED；历史指定范围无明细时显示UNCONFIGURED，详情scope可为null且该角色不授予业务数据访问，不能伪造范围或降级放行；管理员补齐后VALID。新建普通角色默认为VALID。

RoleCreate=`{code,name}`，code1–64/name1–128；初始enabled=true、permissions=[]、scope={type:SELF,orgIds:[],companyIds:[],warehouseMode:NONE,warehouseIds:[]}。Role=`{id,version,code,name,enabled,protected,permissions,scope,scopeState}`；数组去重且权限只取目录值，最多200项。RoleScope.type沿既有六种，SPECIFIED_ORG需要1–200个同租户组织ID，SPECIFIED_COMPANY需要1–200个公司组织ID，其他类型相应数组必须空。仓库模式NONE/SELECTED/ALL；SELECTED需1–200个仓库ID，其余数组空。生效语义见BACKEND_DELTA，前端展示选择原值而非退化摘要。

Permission=`{code,label,group}`。UserSummary=`{id,version,username,displayName,companyId,orgId,enabled,roleIds}`，不返回external_id/令牌；分配角色上限50，仅本租户启用普通角色；不修改受保护管理员绑定。OrgNode=`{id,parentId,type,code,name,enabled,children}`；返回超过2000节点时返回明确422错误，不能静默截断造成授权误选，S1验证边界。本批没有组织move/create或身份bind写接口。

## C-MD：采购必需主数据

资源：units/products/skus/suppliers/warehouses。每类均提供：

| 操作 | 路径 | 权限 | 结果 |
|---|---|---|---|
| 分页 | GET /masterdata/{resource} | masterdata:{singular}:read | Page<该资源Read> |
| 详情 | GET /masterdata/{resource}/{id} | 同read | 该资源Read |
| 新建 | POST /masterdata/{resource} | masterdata:{singular}:write | 201 该资源Read |
| 更新 | PUT /masterdata/{resource}/{id} | 同write | 该资源Read |
| 启停 | PUT /masterdata/{resource}/{id}/enable或disable | 同write | 该资源Read |

singular为unit/product/sku/supplier/warehouse。更新/启停均带expectedVersion；启停体仅该字段。分页q按code/name查询、sort=code/name/id；仓库可加companyId，全部支持enabled。选单使用enabled=true和有界分页，不另建无界options API。

| Resource | Create字段（未注明可选均必填） | Update字段（另加expectedVersion） |
|---|---|---|
| unit | code,name | name；code仅未引用可改 |
| product | code,name；categoryId可选 | name、可选categoryId；code仅未引用可改 |
| sku | productId,baseUnitId,code,name,batchManaged；spec/barcode可选 | 同Create；引用后code/baseUnitId/batchManaged及有历史影响的归属禁止修改 |
| supplier | code,name；contact/phone/settlementMethodId可选 | 同Create，code引用后不可改 |
| warehouse | companyId,code,name | name；code/companyId仅无任何引用、库存及授权影响时允许，首批UI不提供迁仓 |

Read为id/version/enabled + 对应业务字段；可选引用选择项：GET /masterdata/categories 和 /masterdata/settlement-methods，仅Page<{id,code,name,enabled}>，分别masterdata:category:read及masterdata:settlement-method:read，首批只读，不要求新增维护页。已有值即使未配置可选目录权限，详情也不泄漏范围外引用信息。

主数据编码/字段长度以V50为上限；禁用后不用于新单，已有有效采购履约沿快照执行；SKU新增引用必须全部同租户、有效SPU/单位。仓库companyId必须COMPANY且启用。引用关键字段受限；不物理删除。缺配置返回明确422，不让用户看到外键异常。手机号按授权显示且审计脱敏。

## C-PO：采购草稿与状态

| 方法与路径 | 权限 | 请求 | 返回 |
|---|---|---|---|
| GET /purchase/orders | purchase:order:read | ListQuery，sort=orderNo/id，过滤status/companyId/warehouseId | Page<OrderSummary> |
| GET /purchase/orders/{id} | purchase:order:read | — | OrderDetail |
| POST /purchase/orders | purchase:order:write | OrderCreate | 201 OrderDetail |
| PUT /purchase/orders/{id} | purchase:order:write | OrderCreate+expectedVersion | OrderDetail；仅DRAFT |
| POST /purchase/orders/{id}/submit | purchase:order:write | expectedVersion | {order:OrderDetail,approvalInstanceId} |
| POST /purchase/orders/{id}/cancel | purchase:order:write | expectedVersion,reason | OrderDetail |
| POST /purchase/orders/{id}/close-remaining | purchase:order:write | expectedVersion,reason | OrderDetail |

OrderCreate=`{companyId,supplierId,warehouseId,lines:[{skuId,quantity,unitPrice}]}`；1–200行、quantity>0、unitPrice>=0。同SKU可多行但各行身份独立；客户端不提交序号/编号/快照/金额/操作者。服务端校验公司/仓库关联及当前操作权限、读取本位币/编号配置、生成SKU/供应商引用快照。金额累计规则沿既有v2事件，不能把库存6位成本当成4位应付金额。

OrderSummary=`{id,version,orderNo,companyId,supplierId,supplierName,warehouseId,state,currency,totalAmount,createdAt,allowedActions:string[]}`。supplierName来自单据快照。OrderDetail另含`lines,approvalHistory,receiptIds`；line=`{id,lineNo,skuId,skuCode,skuName,unitName,batchManaged,orderedQty,receivedQty,outstandingQty,unitPrice,closed}`，快照字段由服务端保存；approvalHistory=`[{id,status,submittedBy,decidedBy?,decidedAt?,rejectReason?}]`，仅已授权单据可读。receiptIds受边界约束，收货详情仍再判权。allowedActions是提示，不替代执行时重校验。

审批状态沿现有机：DRAFT→APPROVING→APPROVED；REJECT回DRAFT。submit每次创建新的PENDING实例，旧驳回留存；编辑清理未引用的草稿行、不得改写已收货事实。cancel仅无收货且状态可取消，挂起实例同事务WITHDRAWN；close只在PROCESSING/PARTIAL_FINISHED，有剩余时关闭，保留已收。

## C-APR：角色待办池（用户已确认）

GET `/approval/inbox`，权限purchase:order:approve，ListQuery（sort=createdAt/id），返回Page<ApprovalTask>。ApprovalTask=`{instanceId,orderId,orderNo,companyId,warehouseId,submittedBy,submittedAt,orderVersion}`。只返回PENDING且对应采购APPROVING、调用人拥有审批权限与该单范围且不是submitted_by的记录；不按角色名硬编码。任务查询由模块端口组合并在SQL执行同一业务授权条件，不能先分页再内存过滤。

POST `/purchase/orders/{id}/approval-decisions`，权限purchase:order:approve，写头幂等键；体`{expectedVersion,instanceId,decision:APPROVE|REJECT,reason?}`；REJECT必须1–512字原因，APPROVE可省。返回OrderDetail（审批员需具备order:read才能进入详情；审批写仍独立授权）。提交人自审403，实例跨单/已处理409，跨租户/范围404；订单锁→实例锁，实例决策和订单迁移同事务。前端不可传approvedBy，也不提供绕过采购编排的通用审批写入口。

## C-RCV：收货与投影状态

POST `/purchase/orders/{id}/receipts`，权限purchase:receipt:write，体`{expectedVersion,lines:[{orderLineId,quantity,batchNo?}]}`，1–200行、行ID不可重复、quantity正且不超过剩余。批次管理SKU必须填写非空batchNo（1–64，禁止用系统占位符）；非批次SKU由服务端规范为既有占位符。首批库位使用现有无库位桶，不能接受任意locationId。

201返回ReceiptDetail并Location；同key重放返回原receiptId/原响应，不另建收货行。两次不同key只在业务还有剩余且version正确时可分别生效。

GET `/purchase/receipts/{id}`，权限purchase:receipt:read，返回ReceiptDetail=`{id,receiptNo,orderId,orderNo,companyId,warehouseId,currency,amount,createdAt,lines:[{id,orderLineId,skuId,batchNo,quantity,amount}],payableProjection:{status:PENDING|AVAILABLE|FAILED}}`。状态只表示该收货来源的AP生成结果，不泄漏完整财务数据；查看AP仍需finance:payable:read。

PENDING：未生成且待投递/重试；AVAILABLE：finance已存在对应来源单；FAILED：未生成且该财务事件已DEAD或检测到无法继续的已登记错误。不用“超过30秒”推定失败；不把整个事件多消费者完成状态等同财务是否落库。前端每2秒查询、最多30次/离开页面停止，之后显示仍在处理并允许手动刷新，无永久轮询。

## C-INV：库存查询

GET `/inventory/balances`，inventory:balance:read；ListQuery（sort=skuId/id），可选companyId/warehouseId/skuId/batchNo；返回Page<Balance>。Balance=`{id,companyId,warehouseId,locationId,skuId,batchNo,onHand,reserved,available}`，available由服务端派生，不落权威可写字段。

GET `/inventory/transactions`，inventory:transaction:read；同过滤可加sourceDocType/sourceDocId、sort=createdAt/id；返回Page<StockEntry>。StockEntry=`{id,companyId,warehouseId,locationId,skuId,batchNo,direction,quantity,sourceDocType,sourceDocId,sourceLineId,createdAt}`。方向/数量的正负语义沿现有库存契约映射，不把成本值当本位币售价。两接口仓库/公司范围按BACKEND_DELTA的聚合策略；不能靠created_by过滤库存余额。来源单链接必须另行授权。

## C-AP：应付只读

GET `/finance/payables`，finance:payable:read；ListQuery，sort=documentNo/id；可选companyId、supplierId、sourceReceiptId。返回Page<Payable>。
GET `/finance/payables/{id}`，相同权限，返回Payable。
Payable=`{id,version,documentNo,companyId,supplierId,currency,amount,paidAmount,writtenOffAmount,creditedAmount,sourceType,sourceId,sourceNo}`。字段从finance权威模型映射，不新增客户端算法推导“待付款”或“可退款”；本批不加付款/核销按钮。无来源应付的列表可以空，但收货详情必须明确PENDING/FAILED；不得将空列表展示为已结清。

## 契约追踪与兼容门禁

C-IAM→S0/S1/S2；C-MD→S3–S7；C-PO→S8/S9/S13；C-APR→S10；C-RCV→S11；C-INV→S12；C-AP→S14。全部链路→S15。

冻结前核对：已有/iam/roles数组、/iam/me字段、OIDC/auth路径保持；新增字符串ID不会改变既有响应；目录端点与新业务权限点登记唯一；指定组织子树、角色动作范围、仓库聚合授权是明确增量，须完成兼容测试而不是只改文档宣称生效。新的DTO、错误码与权限目录在各Owner实施时同步为类型和契约测试，本轮仅文档定义。
