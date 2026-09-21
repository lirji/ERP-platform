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
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 为每个请求建立 {@link AccessContext}。
 *
 * <p>排在 {@link TraceIdFilter} 之后：traceId 要先于一切可能失败的组件建立，
 * 否则认证失败这类最需要排障的请求反而没有 traceId。
 *
 * <p><b>当前的身份来源</b>：请求头 {@code X-Tenant-Code} + {@code X-Username}。
 * 这是 P1 的<b>临时</b>身份通道，用于在 OIDC 接入完成前驱动授权链路，
 * 只在 {@code erp.security.dev-headers.enabled=true} 时生效，默认关闭。
 *
 * <p><b>它不是认证</b>：请求头可被任意伪造。生产环境必须由 auth-platform(Casdoor)
 * 校验 OIDC 令牌后再装配上下文——那是 P1 剩余工作，见 PROGRESS_STATE。
 * 这里刻意不写成"看起来像认证"的样子，避免日后被误当成安全边界。
 */
@Component
@Order(Integer.MIN_VALUE + 10)
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
            if (devHeadersEnabled) {
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
