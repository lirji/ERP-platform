# 本地运行

当前拓扑只有 Java 21 应用与 PostgreSQL 16；复用现有项目库/卷。未引入 Redis、消息中间件、搜索或前端服务。

1. 从 `.env.example` 创建 `.env` 并设置本地凭据。使用 Compose 时将 `ERP_DB_PORT` 设为 `POSTGRES_HOST_PORT`（默认 45532），两组 DB 名称、用户及密码保持一致。`.env` 不提交。
2. `./deploy/up.sh` 构建镜像并等待健康，启动等待上限 300 秒（镜像构建时间另计）。
3. `./test-data/init-test-data.sh` 初始化独立演示租户，见 [测试数据说明](../test-data/README.md)。
4. `./deploy/smoke.sh` 校验健康和业务监控。首次采样未完成时会明确失败，等一次扫描完成后重试。
5. `./deploy/down.sh` 停止项目容器，保留数据库卷。不要对本地共享数据执行 `down -v`。

应用监听宿主 `127.0.0.1:8500`，数据库监听 `127.0.0.1:45532`。容器内数据库地址为 `postgres:5432`，健康检查使用真实 `/actuator/health`。应用以 UID 10001、只读根文件系统运行，临时目录 128 MiB、内存上限 768 MiB。数据库卷为 `erp-pgdata`，升级时运行 Flyway 增量迁移。

如选择 dev_infra 共享实例，保持 `.env.example` 的 45432，单独运行宿主 JVM（`mvn package -DskipTests` 后执行 app jar）；不要启动本 Compose 的独立数据库。默认端口配置不是把两套实例同时接到同一链路。

业务演示通过真实内部服务完成采购到付款、销售到收款。当前没有销售/财务 HTTP 操作入口或前端，OIDC 客户端尚未接入；本地健康通过不代表可以对外发布。监控处置见 [运维说明](../docs/operations/OBSERVABILITY.md)。未执行生产部署。

## CI

`.github/workflows/erp-ci.yml` 在临时 runner 创建随机数据库凭据，运行全量真实 PostgreSQL 测试，再构建应用、启动并验证重复初始化/清理/重建和监控。测试 XML 保存为 artifact；销毁卷仅发生在一次性 CI 环境。Action 固定到提交，业务变更触发 push/PR 校验。分支保护规则属于仓库管理员配置，本工作流不绕过保护。

## 空库与已有库迁移

历史 V2 重命名状态表依赖 V30 的建表，乱序开关无法解决空库排序。新增 `B30__platform_bootstrap.sql` 按真实依赖汇总 V1/V10/V20/V30/V2：仅空库采用 B30，然后执行 V31 及后续版本；已有迁移历史的数据库继续原路径，原脚本/校验值不变。此行为采用 [Flyway baseline migrations](https://documentation.red-gate.com/flyway/flyway-concepts/migrations/baseline-migrations)，没有执行 repair、删除历史或设置 baseline-on-migrate。

`FreshSchemaMigrationIT` 每次创建随机隔离 schema，验证完整迁移、重复迁移无新增执行，并对比业务表列结构；只清理自身 schema。全新 CI 数据库还实际跑完整业务测试和应用冷启动。

## 统一登录

本地 Casdoor 客户端由 auth-platform 的 `deploy/erp-platform-provision.py` 管理，ERP 提供 `/login`、实际 `/auth/callback` 与资源服务器验签。配置、身份绑定、验证与生产边界见 [OIDC 接入](../docs/security/OIDC.md)。默认不启用 OIDC，业务接口保持拒绝。
