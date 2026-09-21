package com.lrj.erp.app.observability;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TraceIdFilter 的行为约束。
 *
 * <p>把 P0 出口条件 ④「日志中可见 traceId」固化成测试，而不是只靠一次人工 curl 验证——
 * 人工验证证明的是"当时能用"，测试证明的是"以后改坏会被发现"。
 */
@DisplayName("请求追踪标识")
class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter();

    @Test
    @DisplayName("未携带追踪头时生成 traceId，并在处理期间写入 MDC")
    void 无入站追踪头时生成() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/any");
        MockHttpServletResponse res = new MockHttpServletResponse();
        AtomicReference<String> seenInsideChain = new AtomicReference<>();

        FilterChain chain = (a, b) -> seenInsideChain.set(MDC.get(TraceIdFilter.MDC_TRACE_ID));
        filter.doFilter(req, res, chain);

        assertNotNull(seenInsideChain.get(), "处理链执行期间 MDC 必须已有 traceId，否则日志打不出来");
        assertFalse(seenInsideChain.get().isBlank());
        assertEquals(seenInsideChain.get(), res.getHeader("X-Trace-Id"),
                "响应头应回写同一个 traceId，调用方才能据此提工单");
    }

    @Test
    @DisplayName("携带追踪头时必须复用，不得另生成一个")
    void 复用入站追踪头() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/any");
        req.addHeader("X-Trace-Id", "upstream-trace-id");
        MockHttpServletResponse res = new MockHttpServletResponse();
        AtomicReference<String> seen = new AtomicReference<>();

        filter.doFilter(req, res, (a, b) -> seen.set(MDC.get(TraceIdFilter.MDC_TRACE_ID)));

        assertEquals("upstream-trace-id", seen.get(),
                "另生成 traceId 会让一次调用在上下游日志中出现两个 id，追踪链断裂");
    }

    @Test
    @DisplayName("请求结束必须清理 MDC，避免线程池把 traceId 泄漏给下一个请求")
    void 结束后清理MDC() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/any");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, (a, b) -> { });

        assertNull(MDC.get(TraceIdFilter.MDC_TRACE_ID),
                "未清理会导致下一个请求继承上一个的 traceId —— 这类污染极难排查");
    }

    @Test
    @DisplayName("处理链抛异常时同样要清理 MDC")
    void 异常路径也清理MDC() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/any");
        MockHttpServletResponse res = new MockHttpServletResponse();

        assertThrows(RuntimeException.class, () -> filter.doFilter(req, res, (a, b) -> {
            throw new RuntimeException("boom");
        }));
        assertNull(MDC.get(TraceIdFilter.MDC_TRACE_ID),
                "异常路径不清理，正是线程复用污染最常见的来源");
    }
}
