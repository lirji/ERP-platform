package com.lrj.erp.app.security;

import com.lrj.erp.iam.security.PermissionChecker;
import com.lrj.erp.iam.security.RequiresPermission;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * {@link RequiresPermission} 的 Web 适配：在进入 Controller 前调用判权决策。
 * 决策本身在 {@link PermissionChecker}（erp-iam），这里只做 HTTP 侧的接线。
 */
@Component
public class PermissionInterceptor implements HandlerInterceptor {

    private final PermissionChecker checker;

    public PermissionInterceptor(PermissionChecker checker) {
        this.checker = checker;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod method) {
            RequiresPermission required = method.getMethodAnnotation(RequiresPermission.class);
            if (required != null) {
                checker.requireAll(required.value());
            }
        }
        return true;
    }
}
