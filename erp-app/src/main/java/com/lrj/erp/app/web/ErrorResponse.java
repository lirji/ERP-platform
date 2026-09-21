package com.lrj.erp.app.web;

import java.util.Map;

/**
 * 统一错误响应体。结构见 contracts/CONTRACTS.md §2.1。
 *
 * @param details 结构化补充；<b>不得</b>包含 SQL、堆栈、内部类名或其他租户的数据
 */
public record ErrorResponse(String code, String message, String traceId, Map<String, Object> details) { }
