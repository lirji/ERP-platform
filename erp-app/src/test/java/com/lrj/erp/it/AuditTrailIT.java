package com.lrj.erp.it;

import com.lrj.erp.document.service.AuditRecorder;
import com.lrj.erp.kernel.context.*;
import org.junit.jupiter.api.*;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P1 出口条件 ⑥：审计记录包含前值/后值/IP/traceId。
 *
 * <p>提示词第十三章要求审计能回答：谁、什么时候、操作了什么、
 * 修改前是什么、修改后是什么、来源 IP、来源终端、关联业务单据。逐项断言。
 */
@DisplayName("业务审计")
class AuditTrailIT extends AbstractPostgresIT {

    private static final long TENANT = 800L;

    @Autowired private AuditRecorder recorder;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM doc_audit_log WHERE tenant_id = ?", TENANT);
        AccessContextHolder.set(new AccessContext(
                TENANT, 8101L, 8001L, "/8001/8002/",
                Set.of("purchase:order:write"),
                DataScope.of(DataScopeType.DEPT_AND_BELOW),
                "Asia/Shanghai", "trace-audit-1"));
        RequestMetadataHolder.set(new RequestMetadata("203.0.113.7", "Mozilla/5.0 ERPConsole"));
        MDC.put("traceId", "trace-audit-1");
    }

    @AfterEach
    void tearDown() {
        AccessContextHolder.clear();
        RequestMetadataHolder.clear();
        MDC.clear();
    }

    @Test
    @DisplayName("审计记录完整包含：谁/何时/改了什么/前值/后值/来源IP/终端/traceId")
    void 审计记录完整() {
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("qty", 10);
        before.put("remark", "原备注");
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("qty", 25);
        after.put("remark", "改后备注");

        recorder.record("PURCHASE_ORDER", "PO-1001", "PO20260921000001", "UPDATE", before, after);

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT * FROM doc_audit_log WHERE tenant_id = ? AND business_id = 'PO-1001'", TENANT);

        assertEquals(8101L, ((Number) row.get("user_id")).longValue(), "谁");
        assertNotNull(row.get("occurred_at"), "什么时候");
        assertEquals("PURCHASE_ORDER", row.get("business_type"), "操作了什么");
        assertEquals("PO20260921000001", row.get("document_no"), "关联业务单据");
        assertEquals("UPDATE", row.get("action"));
        // PostgreSQL 的 JSONB 会重新格式化（键后带空格），故比较时去掉空白，
        // 不要对存储层的格式化细节做字面断言
        assertTrue(compact(row.get("before_value")).contains("\"qty\":10"),
                "修改前是什么，实际：" + row.get("before_value"));
        assertTrue(compact(row.get("after_value")).contains("\"qty\":25"),
                "修改后是什么，实际：" + row.get("after_value"));
        assertEquals("203.0.113.7", row.get("source_ip"), "来源 IP");
        assertEquals("Mozilla/5.0 ERPConsole", row.get("user_agent"), "来源终端");
        assertEquals("trace-audit-1", row.get("trace_id"), "traceId 用于串联同一次请求的全部日志");
    }

    private static String compact(Object json) {
        return String.valueOf(json).replaceAll("\\s+", "");
    }

    @Test
    @DisplayName("新建时前值为空，后值有内容——空的应当是 before 而不是 after")
    void 新建时前值为空() {
        recorder.record("PURCHASE_ORDER", "PO-1002", "PO20260921000002", "CREATE",
                null, Map.of("qty", 5));

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT before_value, after_value FROM doc_audit_log WHERE business_id = 'PO-1002'");
        assertNull(row.get("before_value"));
        assertNotNull(row.get("after_value"));
    }

    @Test
    @DisplayName("敏感字段落库前必须脱敏")
    void 敏感字段脱敏() {
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("username", "zhangsan");
        after.put("password", "super-secret");
        after.put("bankAccount", "6222020000000000");

        recorder.record("USER", "U-1", null, "UPDATE", null, after);

        String stored = String.valueOf(jdbc.queryForObject(
                "SELECT after_value FROM doc_audit_log WHERE business_id = 'U-1'", String.class));
        assertFalse(stored.contains("super-secret"), "口令不得明文进审计：" + stored);
        assertFalse(stored.contains("6222020000000000"), "银行账号不得明文进审计：" + stored);
        assertTrue(stored.contains("zhangsan"), "非敏感字段应当保留，否则审计失去价值");
        assertTrue(stored.contains("***"), "脱敏应留下占位以示该字段存在过");
    }

    @Test
    @DisplayName("审计与业务同事务：无 AccessContext 时直接失败，不静默丢审计")
    void 无上下文时失败而非静默丢弃() {
        AccessContextHolder.clear();
        assertThrows(IllegalStateException.class,
                () -> recorder.record("PURCHASE_ORDER", "PO-X", null, "UPDATE", null, Map.of()),
                "静默跳过审计比报错危险得多——事后没人知道缺了记录");
    }
}
