package com.lrj.erp.iam.security;

import com.lrj.erp.kernel.context.AccessContext;
import com.lrj.erp.kernel.context.AccessContextHolder;
import com.lrj.erp.kernel.error.DomainException;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 判权决策。<b>不依赖 Web 框架</b>——本模块是库模块，刻意不引入 spring-web：
 * 判权是授权语义，不是 HTTP 语义。Web 适配（HandlerInterceptor）放在 erp-app。
 *
 * <p>这样拆还有个实际好处：判权可以在非 HTTP 入口（定时任务、消息消费）复用，
 * 不必为了调用它而伪造一个 HttpServletRequest。
 */
@Component
public class PermissionChecker {

    /**
     * 校验当前上下文是否具备全部所需权限点。
     *
     * @throws DomainException {@code ERP-AUTH-0001} 无上下文 · {@code ERP-AUTH-4001} 权限不足
     */
    public void requireAll(String... permissions) {
        Optional<AccessContext> ctx = AccessContextHolder.find();
        if (ctx.isEmpty()) {
            throw new DomainException(AuthErrorCode.NO_CREDENTIAL);
        }
        List<String> missing = Arrays.stream(permissions)
                .filter(p -> !ctx.get().hasPermission(p))
                .toList();
        if (!missing.isEmpty()) {
            // details 只给缺失项，不回显用户已有权限——那会把权限模型暴露给攻击者
            throw new DomainException(AuthErrorCode.PERMISSION_DENIED, Map.of("required", missing));
        }
    }
}
