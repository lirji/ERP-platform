package com.lrj.erp.app.security;


import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 注册判权拦截器。
 *
 * <p>只拦 {@code /api/**}：actuator 的健康探针不应要求业务权限，
 * 否则容器编排的存活检查会因为没有登录态而判定应用不健康。
 */
@Configuration
public class WebSecurityConfig implements WebMvcConfigurer {

    private final PermissionInterceptor permissionInterceptor;

    public WebSecurityConfig(PermissionInterceptor permissionInterceptor) {
        this.permissionInterceptor = permissionInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(permissionInterceptor).addPathPatterns("/api/**");
    }
}
