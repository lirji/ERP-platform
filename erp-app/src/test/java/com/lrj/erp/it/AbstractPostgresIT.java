package com.lrj.erp.it;

import com.lrj.erp.app.ErpApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * 集成测试基类：连接一个<b>真实的 PostgreSQL 16</b>，并在独立数据库 {@code erp_it} 上运行。
 *
 * <p><b>为什么不用 Testcontainers</b>：本机 Docker Desktop 29.7.2 的 API 代理对
 * docker-java 的 {@code /info} 调用返回 400（同一 socket 用 curl 访问任意 API 版本均为 200），
 * 三种 ClientProviderStrategy 全部失败。这是 Docker Desktop 与 docker-java 的互操作问题，
 * 不是本项目的问题，也不值得在 ERP 仓库里绕。
 *
 * <p>出口条件要求的是「真实 PostgreSQL + 真实事务 + 真实并发」——{@code deploy/compose.yaml}
 * 提供的 postgres:16-alpine 完全满足，且与生产镜像版本一致。Testcontainers 只是取得真实数据库的
 * 一种便利手段，不是要求本身。
 *
 * <p>代价：集成测试依赖本地 compose 已启动。<b>不可达时显式失败并给出可操作提示</b>，
 * 绝不静默跳过——跳过的测试是假绿，正是本项目反复防范的空跑。
 *
 * <p>隔离：使用独立数据库 {@code erp_it}，不触碰开发者的 {@code erp} 库。
 */
@SpringBootTest(classes = ErpApplication.class, properties = {"erp.outbox.scheduling-enabled=false", "erp.monitoring.enabled=false"})
public abstract class AbstractPostgresIT {

    private static final String HOST = System.getenv().getOrDefault("ERP_IT_DB_HOST", "127.0.0.1");
    private static final String PORT = System.getenv().getOrDefault("ERP_IT_DB_PORT", "45532");
    private static final String USER = System.getenv().getOrDefault("ERP_IT_DB_USER", "erp");
    private static final String PASSWORD = System.getenv().getOrDefault("ERP_IT_DB_PASSWORD", "erp_local_dev");
    private static final String ADMIN_DB = System.getenv().getOrDefault("ERP_IT_ADMIN_DB", "erp");
    private static final String IT_DB = "erp_it";

    static {
        ensureTestDatabase();
    }

    /** 确保 erp_it 库存在；顺带把"数据库不可达"变成一条可操作的错误而不是一串 JDBC 堆栈。 */
    private static void ensureTestDatabase() {
        String adminUrl = "jdbc:postgresql://%s:%s/%s".formatted(HOST, PORT, ADMIN_DB);
        try (Connection conn = DriverManager.getConnection(adminUrl, USER, PASSWORD);
             Statement st = conn.createStatement()) {

            try (ResultSet rs = st.executeQuery(
                    "SELECT 1 FROM pg_database WHERE datname = '" + IT_DB + "'")) {
                if (!rs.next()) {
                    // CREATE DATABASE 不能在事务中执行，这里连接为自动提交
                    st.executeUpdate("CREATE DATABASE " + IT_DB);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("""
                    集成测试需要一个真实的 PostgreSQL，但连接 %s 失败。
                    请先启动本地数据库：
                        docker compose -f deploy/compose.yaml --env-file .env up -d
                    如数据库位置不同，可用环境变量覆盖：
                        ERP_IT_DB_HOST / ERP_IT_DB_PORT / ERP_IT_DB_USER / ERP_IT_DB_PASSWORD
                    """.formatted(adminUrl), e);
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> "jdbc:postgresql://%s:%s/%s".formatted(HOST, PORT, IT_DB));
        registry.add("spring.datasource.username", () -> USER);
        registry.add("spring.datasource.password", () -> PASSWORD);
        // 每次从零跑全部迁移：这同时验证了迁移脚本本身在空库上可执行
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.clean-disabled", () -> "false");
    }
}
