package com.lrj.erp.app.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lrj.erp.app.observability.TraceIdFilter;
import com.lrj.erp.kernel.error.DomainException;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 在<b>过滤器</b>中把领域异常写成统一错误响应。
 *
 * <p>为什么需要它：过滤器运行在 DispatcherServlet <b>之外</b>，
 * {@code @RestControllerAdvice} 捕获不到那里抛出的异常。
 * 不处理的话，认证/上下文装配阶段的失败会以原始异常冒泡，
 * 最终变成带堆栈的 500——而这恰恰是 CONTRACTS §2.1 明令禁止的泄漏，
 * 且发生在最敏感的认证路径上。
 */
@Component
public class FilterErrorWriter {

    private final ObjectMapper objectMapper;

    public FilterErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletResponse response, DomainException ex) throws IOException {
        response.setStatus(ex.errorCode().httpStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        String traceId = MDC.get(TraceIdFilter.MDC_TRACE_ID);
        objectMapper.writeValue(response.getOutputStream(), new ErrorResponse(
                ex.errorCode().code(), ex.errorCode().message(),
                traceId == null ? "" : traceId, ex.details()));
    }
}
