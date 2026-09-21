package com.lrj.erp.kernel.context;

import java.util.Set;

/**
 * 一次请求的全部权限事实，由认证与授权装配一次，向下传递。
 *
 * <p><b>业务代码不得重新推导其中任何字段</b>。尤其是 {@code tenantId} 与 {@code dataScope}：
 * 它们只能来自已验证的身份令牌。若从请求体接受 tenantId，等于把越权做成了一个入参。
 *
 * @param orgPath 组织物化路径，形如 {@code /1/23/456/}，<b>首尾都带斜杠</b>。
 *                缺少尾斜杠时 {@code /1/2/} 会前缀匹配到 {@code /1/23/}，
 *                即把兄弟部门的数据判成子部门的。形态由 {@link #AccessContext} 校验保证。
 */
public record AccessContext(
        long tenantId,
        long userId,
        long companyId,
        String orgPath,
        Set<String> permissions,
        DataScope dataScope,
        String timezone,
        String traceId) {

    public AccessContext {
        if (tenantId <= 0) {
            throw new IllegalArgumentException("tenantId 必须为正：租户缺失意味着查询将不带租户过滤");
        }
        if (orgPath == null || !orgPath.startsWith("/") || !orgPath.endsWith("/")) {
            throw new IllegalArgumentException(
                    "orgPath 必须首尾都带斜杠（形如 /1/23/456/），实际：" + orgPath);
        }
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }

    /** 是否拥有指定功能权限点，如 {@code purchase:order:approve}。 */
    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }
}
