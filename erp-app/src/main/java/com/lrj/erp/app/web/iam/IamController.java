package com.lrj.erp.app.web.iam;

import com.lrj.erp.iam.security.RequiresPermission;
import com.lrj.erp.kernel.context.AccessContext;
import com.lrj.erp.kernel.context.AccessContextHolder;
import com.lrj.erp.iam.security.AuthErrorCode;
import com.lrj.erp.kernel.error.DomainException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * IAM 对外接口。契约见 contracts/API_P1_PLATFORM_KERNEL.md §2、§4。
 *
 * <p>Controller 只处理协议与参数；<b>不直接依赖 Mapper</b>（禁止事项第 4 条，
 * 由 CodingConventionArchitectureTest 强制）。这里用 JdbcTemplate 做只读投影查询，
 * 它不是 Mapper，也不承载业务规则。
 */
@RestController
@RequestMapping("/api/v1/iam")
public class IamController {

    private final JdbcTemplate jdbc;

    public IamController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 当前身份与权限；前端据此置灰按钮（但置灰不是安全边界）。 */
    @GetMapping("/me")
    public Map<String, Object> me() {
        AccessContext ctx = AccessContextHolder.find()
                .orElseThrow(() -> new DomainException(AuthErrorCode.NO_CREDENTIAL));
        return Map.of(
                "userId", ctx.userId(),
                "tenantId", ctx.tenantId(),
                "companyId", ctx.companyId(),
                "orgPath", ctx.orgPath(),
                "timezone", ctx.timezone(),
                "permissions", ctx.permissions().stream().sorted().toList(),
                "dataScope", ctx.dataScope().type().code());
    }

    /** 角色列表。需要 {@code iam:role:read}——缺权限时由拦截器拒为 403。 */
    @GetMapping("/roles")
    @RequiresPermission("iam:role:read")
    public List<Map<String, Object>> roles() {
        long tenantId = AccessContextHolder.require().tenantId();
        return jdbc.queryForList(
                "SELECT id, code, name, data_scope_type FROM iam_role WHERE tenant_id = ? ORDER BY id",
                tenantId);
    }
}
