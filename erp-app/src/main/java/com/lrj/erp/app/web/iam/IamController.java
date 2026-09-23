package com.lrj.erp.app.web.iam;

import com.lrj.erp.iam.security.RequiresPermission;
import com.lrj.erp.kernel.context.AccessContext;
import com.lrj.erp.kernel.context.AccessContextHolder;
import com.lrj.erp.iam.security.AuthErrorCode;
import com.lrj.erp.kernel.error.DomainException;
import com.lrj.erp.iam.service.RoleManagementService;
import com.lrj.erp.iam.service.RoleModels.LegacyRole;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 旧IAM协议适配；角色SQL由IAM持久化层拥有，保持历史响应字段。 */
@RestController
@RequestMapping("/api/v1/iam")
public class IamController {

    private final RoleManagementService roles;

    public IamController(RoleManagementService roles) {
        this.roles = roles;
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
    public List<LegacyRole> roles() { return roles.legacy(); }
}
