package com.lrj.erp.it;

import com.lrj.erp.document.repository.AuditLogMapper;
import com.lrj.erp.it.support.SqlCaptor;
import com.lrj.erp.kernel.context.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P1 出口条件 ③：org_path 数据权限生效，<b>且 SQL 中确实出现前缀条件</b>。
 *
 * <p>本测试同时断言两件事：
 * <ol>
 *   <li><b>结果正确</b>：A 部门用户看不到 B 部门的记录；</li>
 *   <li><b>机制正确</b>：生成的 SQL 里确实有 org_path 前缀条件与租户条件。</li>
 * </ol>
 * 只断言 ① 是不够的——数据权限完全失效时，若库里恰好只有该部门数据，结果照样"正确"。
 */
@DisplayName("数据权限下推到 SQL")
class DataScopePushdownIT extends AbstractPostgresIT {

    private static final long TENANT = 900L;
    private static final long OTHER_TENANT = 901L;
    private static final String ORG_A = "/1/23/";        // 华东销售部
    private static final String ORG_A_CHILD = "/1/23/456/";
    private static final String ORG_A_SIBLING = "/1/234/"; // 注意：前缀相近的兄弟部门
    private static final String ORG_B = "/1/99/";

    @Autowired private AuditLogMapper auditLogMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private SqlCaptor sqlCaptor;

    @BeforeEach
    void seed() {
        AccessContextHolder.clear();
        jdbc.update("DELETE FROM doc_audit_log WHERE tenant_id IN (?,?)", TENANT, OTHER_TENANT);
        insert(TENANT, 11L, ORG_A,         "A-own");
        insert(TENANT, 12L, ORG_A_CHILD,   "A-child");
        insert(TENANT, 13L, ORG_A_SIBLING, "A-sibling");
        insert(TENANT, 14L, ORG_B,         "B-other");
        insert(OTHER_TENANT, 15L, ORG_A,   "other-tenant");
        sqlCaptor.clear();
    }

    @AfterEach
    void tearDown() {
        AccessContextHolder.clear();
    }

    private void insert(long tenant, long user, String orgPath, String businessId) {
        jdbc.update("""
                INSERT INTO doc_audit_log
                    (tenant_id, user_id, business_type, business_id, action,
                     company_id, org_path, created_by)
                VALUES (?, ?, 'PURCHASE_ORDER', ?, 'CREATE', 9001, ?, ?)
                """, tenant, user, businessId, orgPath, user);
    }

    private void login(DataScope scope, String orgPath, long userId) {
        AccessContextHolder.set(new AccessContext(
                TENANT, userId, 9001L, orgPath,
                Set.of("document:audit:read"), scope, "Asia/Shanghai", "trace-test"));
    }

    @Test
    @DisplayName("DEPT_AND_BELOW：看得到本部门及子部门，看不到兄弟部门与其他部门")
    void 本部门及子部门() {
        login(DataScope.of(DataScopeType.DEPT_AND_BELOW), ORG_A, 11L);

        List<Map<String, Object>> rows = auditLogMapper.findByBusinessType("PURCHASE_ORDER");
        List<String> ids = rows.stream().map(r -> (String) r.get("business_id")).sorted().toList();

        // ① 结果正确
        assertEquals(List.of("A-child", "A-own"), ids,
                "应当只看到本部门与子部门；兄弟部门 /1/234/ 不得因前缀相近被匹配进来");

        // ② 机制正确 —— 断言生成的 SQL
        String sql = sqlCaptor.lastSqlContaining("doc_audit_log");
        assertNotNull(sql, "没有捕获到任何 SQL —— 断言将在空对象上进行，属于空跑");
        assertTrue(sql.contains("org_path"),
                "生成的 SQL 中必须出现 org_path 条件，实际 SQL：\n" + sql);
        assertTrue(sql.contains("'/1/23/%'") || sql.contains("/1/23/%"),
                "必须是前缀匹配 '/1/23/%'，实际 SQL：\n" + sql);
        assertTrue(sql.contains("tenant_id"),
                "租户条件必须始终存在，实际 SQL：\n" + sql);
    }

    @Test
    @DisplayName("DEPT：只看本部门，不含子部门")
    void 仅本部门() {
        login(DataScope.of(DataScopeType.DEPT), ORG_A, 11L);

        List<String> ids = auditLogMapper.findByBusinessType("PURCHASE_ORDER")
                .stream().map(r -> (String) r.get("business_id")).toList();
        assertEquals(List.of("A-own"), ids);

        String sql = sqlCaptor.lastSqlContaining("doc_audit_log");
        assertNotNull(sql, "未捕获 SQL");
        assertTrue(sql.contains("org_path"), "SQL 应含 org_path 等值条件：\n" + sql);
    }

    @Test
    @DisplayName("SELF：只看自己创建的")
    void 仅本人() {
        login(DataScope.of(DataScopeType.SELF), ORG_A, 12L);

        List<String> ids = auditLogMapper.findByBusinessType("PURCHASE_ORDER")
                .stream().map(r -> (String) r.get("business_id")).toList();
        assertEquals(List.of("A-child"), ids);

        String sql = sqlCaptor.lastSqlContaining("doc_audit_log");
        assertNotNull(sql, "未捕获 SQL");
        assertTrue(sql.contains("created_by"), "SQL 应含 created_by 条件：\n" + sql);
    }

    @Test
    @DisplayName("ALL：本租户全部可见，但租户条件依然存在（不得跨租户）")
    void 全部数据仍受租户约束() {
        login(DataScope.of(DataScopeType.ALL), ORG_A, 11L);

        List<String> ids = auditLogMapper.findByBusinessType("PURCHASE_ORDER")
                .stream().map(r -> (String) r.get("business_id")).sorted().toList();
        assertEquals(List.of("A-child", "A-own", "A-sibling", "B-other"), ids,
                "ALL 应看到本租户全部数据");
        assertFalse(ids.contains("other-tenant"), "ALL 绝不意味着跨租户");

        String sql = sqlCaptor.lastSqlContaining("doc_audit_log");
        assertNotNull(sql, "未捕获 SQL");
        assertTrue(sql.contains("tenant_id"),
                "即使 ALL，租户条件也必须在 SQL 中，实际：\n" + sql);
    }

    @Test
    @DisplayName("兄弟部门前缀不得被误匹配：/1/23/ 不能匹配到 /1/234/")
    void 兄弟部门不被前缀误匹配() {
        login(DataScope.of(DataScopeType.DEPT_AND_BELOW), ORG_A, 11L);

        List<String> ids = auditLogMapper.findByBusinessType("PURCHASE_ORDER")
                .stream().map(r -> (String) r.get("business_id")).toList();
        assertFalse(ids.contains("A-sibling"),
                "org_path 尾斜杠的意义就在这里：/1/23/% 不得匹配 /1/234/。"
                + "缺尾斜杠会让兄弟部门数据泄漏，且极难发现");
    }
}
