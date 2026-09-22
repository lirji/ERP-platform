package com.lrj.erp.it;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** 每次用独立空 schema 验证迁移；不再以已有测试库的成功替代新安装证据。 */
class FreshSchemaMigrationIT extends AbstractPostgresIT {
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;
    @Test void freshBaselineMatchesExistingSchemaAndCanBeMigratedAgain() {
        String schema="fresh_"+UUID.randomUUID().toString().replace("-","");
        Flyway flyway=Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").outOfOrder(true).load();
        try {
            flyway.migrate();flyway.validate();
            assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM "+schema+".flyway_schema_history WHERE type='SQL_BASELINE' AND version='30'",Integer.class));
            assertEquals(0,flyway.migrate().migrationsExecuted);
            // 对比实际表列契约；序列值和历史记录不同是预期，业务结构必须一致。
            String columns="SELECT table_name,column_name,data_type,is_nullable,character_maximum_length,numeric_precision,numeric_scale FROM information_schema.columns WHERE table_schema=? AND table_name<>'flyway_schema_history' ORDER BY table_name,ordinal_position";
            assertEquals(jdbc.queryForList(columns,"public"),jdbc.queryForList(columns,schema));
            assertNotNull(jdbc.queryForObject("SELECT to_regclass(?)::text",String.class,schema+".erp_state_transition"));
            assertNull(jdbc.queryForObject("SELECT to_regclass(?)::text",String.class,schema+".doc_state_transition"));
        } finally {
            // schema 名只来自随机 UUID；只移除本测试刚创建的隔离结构，绝不清理 public。
            jdbc.execute("DROP SCHEMA IF EXISTS "+schema+" CASCADE");
        }
    }
}
