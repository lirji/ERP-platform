package com.lrj.erp.app.web;

import com.lrj.erp.app.observability.TraceIdFilter;
import com.lrj.erp.kernel.error.DomainException;
import com.lrj.erp.kernel.error.SystemErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 异常到 HTTP 的<b>唯一</b>转换点（工程约定 §6）。
 *
 * <p>领域异常按错误码映射为 4xx 并原样返回业务语义；系统异常一律 5xx 且
 * <b>只返回通用文案</b>——堆栈、SQL、内部类名不得泄漏给调用方
 * （开发规范 §设计 10）。traceId 一并返回，用户报障时凭它就能定位到具体请求。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ErrorResponse> handleDomain(DomainException ex) {
        // 领域异常是预期内的业务拒绝，不打 error 级别——否则告警会被业务噪音淹没
        log.info("业务拒绝 code={} details={}", ex.errorCode().code(), ex.details());
        return ResponseEntity.status(ex.errorCode().httpStatus())
                .body(new ErrorResponse(ex.errorCode().code(), ex.errorCode().message(),
                        traceId(), ex.details()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, Object> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> fields.put(e.getField(), e.getDefaultMessage()));
        // 400：不看业务数据就能判定不合法。与 422（业务规则拒绝）严格区分，
        // 前者前端应高亮字段，后者应给业务提示。
        return ResponseEntity.badRequest().body(new ErrorResponse(
                "ERP-SYS-0007", "请求参数校验失败", traceId(), Map.of("fields", fields)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        // 未预期异常必须留完整日志（服务端），但响应体只给通用文案
        log.error("未预期的系统异常", ex);
        return ResponseEntity.status(SystemErrorCode.UNEXPECTED.httpStatus())
                .body(new ErrorResponse(SystemErrorCode.UNEXPECTED.code(),
                        SystemErrorCode.UNEXPECTED.message(), traceId(), Map.of()));
    }

    private static String traceId() {
        String id = MDC.get(TraceIdFilter.MDC_TRACE_ID);
        return id == null ? "" : id;
    }
}
