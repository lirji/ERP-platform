package com.lrj.erp.app.security;

import com.lrj.erp.app.observability.TraceIdFilter;
import com.lrj.erp.app.web.FilterErrorWriter;
import com.lrj.erp.kernel.error.DomainException;
import com.lrj.erp.iam.service.AccessContextAssembler;
import com.lrj.erp.kernel.context.AccessContext;
import com.lrj.erp.kernel.context.AccessContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 为每个请求建立 {@link AccessContext}。
 *
 * <p>排在 {@link TraceIdFilter} 之后：traceId 要先于一切可能失败的组件建立，
 * 否则认证失败这类最需要排障的请求反而没有 traceId。
 *
 * <p>OIDC 路径只读取 Spring Security 已验签的 JWT，通过 issuer/owner/sub 绑定本地身份。
 * 开发头通道仅供明确启用的本地测试，不能与 OIDC 共存。
 */
@Component
public class AccessContextFilter extends OncePerRequestFilter {

    private final AccessContextAssembler assembler;
    private final FilterErrorWriter errorWriter;
    private final boolean devHeadersEnabled;

    public AccessContextFilter(AccessContextAssembler assembler,
                               FilterErrorWriter errorWriter,
                               @org.springframework.beans.factory.annotation.Value(
                                       "${erp.security.dev-headers.enabled:false}") boolean devHeadersEnabled) {
        this.assembler = assembler;
        this.errorWriter = errorWriter;
        this.devHeadersEnabled = devHeadersEnabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            var authentication = org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication();
            if (authentication instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken token) {
                var jwt = token.getToken();
                try {
                    AccessContextHolder.set(assembler.assembleOidc(jwt.getIssuer().toString(),
                            jwt.getClaimAsString("owner"), jwt.getSubject(), MDC.get(TraceIdFilter.MDC_TRACE_ID)));
                } catch (DomainException ex) {
                    errorWriter.write(response, ex);
                    return;
                }
            } else if (devHeadersEnabled) {
                String tenantCode = request.getHeader("X-Tenant-Code");
                String username = request.getHeader("X-Username");
                if (tenantCode != null && username != null) {
                    try {
                        AccessContextHolder.set(assembler.assemble(
                                tenantCode, username, MDC.get(TraceIdFilter.MDC_TRACE_ID)));
                    } catch (DomainException ex) {
                        // 过滤器里的异常到不了 @RestControllerAdvice，必须就地写响应，
                        // 否则认证失败会变成带堆栈的 500
                        errorWriter.write(response, ex);
                        return;
                    }
                }
            }
            chain.doFilter(request, response);
        } finally {
            // 必须清理：线程池复用会让下一个请求继承上一个的身份，
            // 在多租户系统里等于跨租户数据泄漏
            AccessContextHolder.clear();
        }
    }
}
