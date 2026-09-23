> 日期：2026-09-23；protocol: `capability-exploration-report/v1`；baseline: `engineering-baseline/v1`。
> explorationSummary：现状：业务内核和本地工程验证较完整，面向真实用户的 ERP 产品入口与生产运行闭环仍不完整。优先补齐业务 API/页面、可信授权与主数据管理、生产运行验收；暂不拆微服务或新增无实际需求的中间件。

# Current Capability Overview

本报告分析当前工作区（main，HEAD `53290422976b54cbb7eeec36f2ae55b1c3d7faf2`），不是只依据计划勾选状态。12 个 Maven 模块组成模块化单体，`erp-app` 为运行入口；模块数不代表独立服务数。扫描包含生产 Java、Mapper XML、迁移、测试、CI、运行脚本及规划/交付记录。

开始扫描时有 13 个既有未提交文件；结束校验时它们已变为 HEAD 内容，HEAD 未改变。本轮未操作这些文件，也未执行 Git 回退；这属于本轮写入范围之外的并发工作区变化。最终核查跟踪文件无差异，报告关键源码证据不受影响。本轮仅生成五份报告，未重跑测试、修改源码、变更运行环境或 Git 发布。历史 179 项测试仍按历史证据引用。

成熟度按本报告限定范围判定：ABSENT 未发现实现；BASIC 有基础结构；PARTIAL 链路不完整；MATURE 在明确范围内有实现和验证证据，不表示已生产验收。所有“未发现”限于本仓库扫描范围，不排除仓库外已有运维能力。

| 模块 | 当前职责 |
|---|---|
| erp-kernel | 上下文、错误、状态机制、Outbox、跨模块端口 |
| erp-numbering / erp-document | 取号、审计、单据关系 |
| erp-iam / erp-masterdata / erp-approval | 授权与员工、SKU/基础资料、内置审批 |
| erp-inventory / erp-procurement / erp-sales / erp-finance | 库存及成本、采购、销售、往来结算 |
| erp-reporting / erp-app | 快照读模型、运行装配和 HTTP 安全边界 |

## Business

| 能力 | 已实现 | 尚缺/边界 | 成熟度 | Evidence |
|---|---|---|---|---|
| 采购到应付 | 订单→审批→分批收货→库存→Outbox→应付 | 无业务 HTTP/页面；申请非完整业务链 | PARTIAL | [E06](EVIDENCE_INDEX.md#e06)、[E12](EVIDENCE_INDEX.md#e12)、[E27](EVIDENCE_INDEX.md#e27) |
| 销售到应收 | 信用校验→审批→预占→出库→应收→收款核销 | 无业务入口；权威主数据装配需补齐 | PARTIAL | [E07](EVIDENCE_INDEX.md#e07)、[E09](EVIDENCE_INDEX.md#e09)、[E12](EVIDENCE_INDEX.md#e12) |
| 库存与成本 | 库存桶、不可变流水、幂等、移动加权成本 | 过期预占未回收；操作台缺失 | PARTIAL | [E08](EVIDENCE_INDEX.md#e08)、[E09](EVIDENCE_INDEX.md#e09)、[E10](EVIDENCE_INDEX.md#e10) |
| 退货与仓储作业 | 采购/销售退货、红字退款配套；调拨在途、盘点、调整 | 缺业务操作入口 | PARTIAL | [E11](EVIDENCE_INDEX.md#e11)、[E32](EVIDENCE_INDEX.md#e32)、[E33](EVIDENCE_INDEX.md#e33) |
| 往来财务 | AR/AP、收付款、核销/反核销 | 历史 v1 金额不能自动恢复；不是总账系统 | PARTIAL | [E12](EVIDENCE_INDEX.md#e12)、[E13](EVIDENCE_INDEX.md#e13)、[E29](EVIDENCE_INDEX.md#e29) |
| 主数据 | SKU 增改启停、引用保护、批量校验；其他基础表 | 供应商/客户/仓库等管理服务和工作台未齐 | PARTIAL | [E04](EVIDENCE_INDEX.md#e04)、[E05](EVIDENCE_INDEX.md#e05) |

## Platform

编号、状态机制、审计、单据关系以及审批 Port 已存在。IAM 有身份查询、角色查询和员工生命周期服务，但没有完整组织/用户/角色/授权维护入口。内置审批可提交与决策，缺待办、处理人分配及业务用户操作闭环；这不等于必须接入 BPM。 Evidence：[E01](EVIDENCE_INDEX.md#e01)、[E02](EVIDENCE_INDEX.md#e02)、[E14](EVIDENCE_INDEX.md#e14)、[E15](EVIDENCE_INDEX.md#e15)、[E34](EVIDENCE_INDEX.md#e34)。

## Engineering

已有 Maven 多模块、ArchUnit、真实 PostgreSQL 集成测试、迁移校验、Docker/CI、可重复种子数据。领域链路测试不等于浏览器业务 E2E。历史 OIDC 门禁记录 179 项通过；本轮没有重新执行测试。接口交付时应扩展现有测试而非再建一套质量平台。 Evidence：[E23](EVIDENCE_INDEX.md#e23)、[E24](EVIDENCE_INDEX.md#e24)。

## Reliability

已有约束/锁/幂等、Outbox 有界重试和死信、报表检查点、业务补偿。缺仓库内可审查的备份恢复演练证据、生产发布回退验收和运维任务负责人闭环。Outbox 整批持锁、消费者事务与连接池在多实例负载下需测量，不能直接推断已故障。 Evidence：[E08](EVIDENCE_INDEX.md#e08)、[E12](EVIDENCE_INDEX.md#e12)、[E19](EVIDENCE_INDEX.md#e19)、[E20](EVIDENCE_INDEX.md#e20)、[E26](EVIDENCE_INDEX.md#e26)。

## Observability

已有 traceId、提交后业务审计日志、慢 SQL、积压/死信/账实一致性探针、处置手册和告警检查脚本。尚无通知人员的渠道配置与到达验收；指标接口生产隔离待完成。 Evidence：[E21](EVIDENCE_INDEX.md#e21)、[E18](EVIDENCE_INDEX.md#e18)。

## Data

PostgreSQL 权威数据、Flyway、字段注释、引用快照、五类可恢复报表均已有。报表显式刷新、变更可能 STALE，各类不共享结账时点；未核销账龄不等于合同逾期。批量 SKU 入参并不是 CSV/Excel 文件导入产品；历史事件回填和保留归档政策待业务数据确认。 Evidence：[E04](EVIDENCE_INDEX.md#e04)、[E13](EVIDENCE_INDEX.md#e13)、[E20](EVIDENCE_INDEX.md#e20)、[E25](EVIDENCE_INDEX.md#e25)。

## Security

真实 OIDC/JWKS、本地身份绑定、租户与 SQL 数据范围、员工离职禁用存在。指定公司/组织仍降级；表覆盖和仓库授权需要补齐。业务入口不能直接相信客户端传来的租户、操作者、信用额度、主数据快照。指标匿名在当前回环部署下不是已证实公网泄漏，但对外暴露前必须隔离。 Evidence：[E16](EVIDENCE_INDEX.md#e16)、[E17](EVIDENCE_INDEX.md#e17)、[E18](EVIDENCE_INDEX.md#e18)、[E22](EVIDENCE_INDEX.md#e22)、[E31](EVIDENCE_INDEX.md#e31)。

## Integration

auth-platform 已完成本地接入；生产客户端与回调由 auth 项目负责生成，ERP 负责消费配置及端到端验证。当前集成以进程内 Outbox 事件/端口为主；附件、通知、银行税务没有完整接入，后两者原本后置。 Evidence：[E18](EVIDENCE_INDEX.md#e18)、[E19](EVIDENCE_INDEX.md#e19)、[E29](EVIDENCE_INDEX.md#e29)、[E30](EVIDENCE_INDEX.md#e30)。

## AI

未发现 AI 模型、RAG、向量库或自动决策业务链路；当前进销存及结算闭环不依赖 AI。这是有意保留的探索空间，不是发布缺陷。未来仅在真实检索/辅助需求出现时评估，不能让模型直接修改库存、金额或授权。 Evidence：[E01](EVIDENCE_INDEX.md#e01)、[E29](EVIDENCE_INDEX.md#e29)。

## Capability Maturity Summary

| Dimension | Maturity | 主要差距 |
|---|---|---|
| BUSINESS | PARTIAL | 服务存在，用户操作链路缺失 |
| PLATFORM | PARTIAL | 见 Platform 的限定范围 |
| ENGINEERING | PARTIAL | 见 Engineering 的限定范围 |
| RELIABILITY | PARTIAL | 见 Reliability 的限定范围 |
| OBSERVABILITY | PARTIAL | 见 Observability 的限定范围 |
| DATA | PARTIAL | 见 Data 的限定范围 |
| SECURITY | PARTIAL | 见 Security 的限定范围 |
| INTEGRATION | PARTIAL | 见 Integration 的限定范围 |
| AI | ABSENT | 见 AI 的限定范围 |

### 关键链路与断点

1. OIDC 登录→令牌校验→本地身份→权限→`/me` 已有本地验证；生产域名与客户端绑定待真实环境。
2. SKU 校验→引用快照→采购订单→收货→库存→应付服务链已存在；人工业务录入入口缺失。
3. 客户授信→销售→预占→出库→应收→收款核销已有实现；可信客户资料读取不能交给浏览器决定。
4. 退货→逆向库存→红字/退款存在；需补操作端、权限和跨链路验收。
5. 调出→在途→调入及盘点→审批→差异入账存在；需作业台及权限范围验证。
6. 来源事件→Outbox→财务/关系投影有重试；死信处置及历史数据恢复需运营闭环。
7. 权威模块导出→分批快照→发布指针→分页/账龄存在；缺业务查询/下载入口和刷新责任。
8. 指标探针→告警脚本→人工处置目前断在外部接收与到达验证。

详细缺口见 [CAPABILITY_GAPS](CAPABILITY_GAPS.md)，实施顺序见 [EVOLUTION_ROADMAP](EVOLUTION_ROADMAP.md)。
