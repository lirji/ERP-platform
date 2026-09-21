package com.lrj.erp.app.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.lrj.erp.kernel.context.RequestMetadata;
import com.lrj.erp.kernel.context.RequestMetadataHolder;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 为每个请求建立 traceId，并写入 MDC 供结构化日志输出。
 *
 * <p>为什么放在过滤链最前（{@code Order} 最小）：traceId 必须早于任何可能失败的组件建立，
 * 否则认证失败、参数校验失败这类最需要排障的请求反而没有 traceId。
 *
 * <p>MVP 阶段不引入 Micrometer Tracing / OpenTelemetry：单进程内靠 traceId + 单据号即可定位，
 * 分布式追踪推迟到多实例或明确 SLA 时（TECH_SELECTION「可观测」行）。
 */
@Component
@Order(Integer.MIN_VALUE)
public class TraceIdFilter extends OncePerRequestFilter {

    /** 入站可携带的追踪头；上游（网关/前端）已生成时复用，避免一次调用出现两个 id。 */
    private static final String TRACE_HEADER = "X-Trace-Id";
    public static final String MDC_TRACE_ID = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String traceId = request.getHeader(TRACE_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        MDC.put(MDC_TRACE_ID, traceId);
        // 来源信息在此一并采集：审计要求记录来源 IP 与终端，而审计发生在更下游的模块，
        // 那里拿不到 HttpServletRequest
        RequestMetadataHolder.set(new RequestMetadata(clientIp(request), request.getHeader("User-Agent")));
        // 回写响应头：调用方（含前端与测试）拿到 traceId 才能据此提工单
        response.setHeader(TRACE_HEADER, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            // 必须清理：线程池复用会让下一个请求继承上一个的 traceId
            MDC.remove(MDC_TRACE_ID);
            RequestMetadataHolder.clear();
        }
    }

    /**
     * 取真实客户端 IP。优先 X-Forwarded-For 的第一段（最初的客户端），
     * 其后各段是途经的代理；没有该头时回落到直连地址。
     *
     * <p>注意：X-Forwarded-For 可被伪造，仅用于审计展示与排障，
     * <b>不得</b>作为任何安全判定的依据。
     */
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
