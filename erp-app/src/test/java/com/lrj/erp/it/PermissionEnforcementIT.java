package com.lrj.erp.it;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * P1 出口条件 ①②：装配出 AccessContext；无权限用户调 API 返回 403。
 *
 * <p>走完整 HTTP 栈（过滤器 → 拦截器 → Controller → 全局异常处理），
 * 而不是直接调用 PermissionChecker——要验证的正是这条链路接对了没有。
 */
@DisplayName("功能权限判定")
@AutoConfigureMockMvc
@TestPropertySource(properties = "erp.security.dev-headers.enabled=true")
class PermissionEnforcementIT extends AbstractPostgresIT {

    private static final String TENANT_CODE = "acme";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM iam_user_role");
        jdbc.update("DELETE FROM iam_role_permission");
        jdbc.update("DELETE FROM iam_user");
        jdbc.update("DELETE FROM iam_role");
        jdbc.update("DELETE FROM iam_org");
        jdbc.update("DELETE FROM iam_tenant WHERE code = ?", TENANT_CODE);

        jdbc.update("INSERT INTO iam_tenant (id, code, name, timezone, enabled) "
                + "VALUES (700, ?, 'ACME 公司', 'Asia/Shanghai', TRUE)", TENANT_CODE);
        jdbc.update("INSERT INTO iam_org (id, tenant_id, parent_id, org_path, code, name, org_type) "
                + "VALUES (7001, 700, NULL, '/7001/', 'HQ', '总部', 'COMPANY')");
        jdbc.update("INSERT INTO iam_org (id, tenant_id, parent_id, org_path, code, name, org_type) "
                + "VALUES (7002, 700, 7001, '/7001/7002/', 'SALES', '销售部', 'DEPT')");

        // 有权限的用户
        jdbc.update("INSERT INTO iam_user (id, tenant_id, company_id, org_id, username, display_name, enabled) "
                + "VALUES (7101, 700, 7001, 7002, 'manager', '张经理', TRUE)");
        jdbc.update("INSERT INTO iam_role (id, tenant_id, code, name, data_scope_type, enabled) "
                + "VALUES (7201, 700, 'ROLE_MGR', '销售经理', 'DEPT_AND_BELOW', TRUE)");
        jdbc.update("INSERT INTO iam_role_permission (tenant_id, role_id, permission) "
                + "VALUES (700, 7201, 'iam:role:read')");
        jdbc.update("INSERT INTO iam_user_role (tenant_id, user_id, role_id) VALUES (700, 7101, 7201)");

        // 无该权限的用户（有角色，但角色里没有 iam:role:read）
        jdbc.update("INSERT INTO iam_user (id, tenant_id, company_id, org_id, username, display_name, enabled) "
                + "VALUES (7102, 700, 7001, 7002, 'clerk', '李职员', TRUE)");
        jdbc.update("INSERT INTO iam_role (id, tenant_id, code, name, data_scope_type, enabled) "
                + "VALUES (7202, 700, 'ROLE_CLERK', '普通职员', 'SELF', TRUE)");
        jdbc.update("INSERT INTO iam_role_permission (tenant_id, role_id, permission) "
                + "VALUES (700, 7202, 'purchase:order:read')");
        jdbc.update("INSERT INTO iam_user_role (tenant_id, user_id, role_id) VALUES (700, 7102, 7202)");

        // 已停用的用户
        jdbc.update("INSERT INTO iam_user (id, tenant_id, company_id, org_id, username, display_name, enabled) "
                + "VALUES (7103, 700, 7001, 7002, 'disabled', '已离职', FALSE)");
    }

    @Test
    @DisplayName("① 登录身份可装配出 AccessContext，含权限点与数据范围")
    void 装配AccessContext() throws Exception {
        mockMvc.perform(get("/api/v1/iam/me")
                        .header("X-Tenant-Code", TENANT_CODE)
                        .header("X-Username", "manager"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(7101))
                .andExpect(jsonPath("$.tenantId").value(700))
                .andExpect(jsonPath("$.orgPath").value("/7001/7002/"))
                .andExpect(jsonPath("$.timezone").value("Asia/Shanghai"))
                .andExpect(jsonPath("$.dataScope").value("DEPT_AND_BELOW"))
                .andExpect(jsonPath("$.permissions[0]").value("iam:role:read"));
    }

    @Test
    @DisplayName("② 有权限的用户可以访问")
    void 有权限可访问() throws Exception {
        mockMvc.perform(get("/api/v1/iam/roles")
                        .header("X-Tenant-Code", TENANT_CODE)
                        .header("X-Username", "manager"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("② 无权限用户调 API 返回 403 且错误码为 ERP-AUTH-4001")
    void 无权限返回403() throws Exception {
        mockMvc.perform(get("/api/v1/iam/roles")
                        .header("X-Tenant-Code", TENANT_CODE)
                        .header("X-Username", "clerk"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ERP-AUTH-4001"))
                .andExpect(jsonPath("$.details.required[0]").value("iam:role:read"))
                // traceId 必须回传：用户报障时凭它定位请求
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("未登录访问受保护接口返回 401")
    void 未登录返回401() throws Exception {
        mockMvc.perform(get("/api/v1/iam/roles"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ERP-AUTH-0001"));
    }

    @Test
    @DisplayName("已停用用户被拒，错误码 ERP-AUTH-4004")
    void 停用用户被拒() throws Exception {
        mockMvc.perform(get("/api/v1/iam/me")
                        .header("X-Tenant-Code", TENANT_CODE)
                        .header("X-Username", "disabled"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ERP-AUTH-4004"));
    }

    @Test
    @DisplayName("不存在的用户与凭证无效返回同一错误，避免成为用户名枚举接口")
    void 用户不存在不泄漏() throws Exception {
        mockMvc.perform(get("/api/v1/iam/me")
                        .header("X-Tenant-Code", TENANT_CODE)
                        .header("X-Username", "no-such-user"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ERP-AUTH-0002"));
    }
}
