package com.lrj.erp.procurement.domain;

import com.lrj.erp.kernel.error.ErrorCode;

/** 采购错误码（ERP-PUR-*）。 */
public enum ProcurementErrorCode implements ErrorCode {

    ORDER_NOT_FOUND ("ERP-PUR-3001", "采购订单不存在",                     422),
    LINE_NOT_FOUND  ("ERP-PUR-3002", "采购订单行不存在",                   422),
    /**
     * 不允许超收（2026-09-21 用户决策）。
     * 不设比例配置项——为一条"不允许"的规则建配置开关是多余的表面。
     */
    OVER_RECEIPT    ("ERP-PUR-3003", "收货数量超过订购数量，不允许超收",     422),
    LINE_CLOSED     ("ERP-PUR-3004", "该订单行已关闭，不能继续收货",         422),
    CANCEL_AFTER_RECEIPT("ERP-PUR-3005", "订单已发生收货，不能取消；如需终止请关闭剩余", 422),
    NOTHING_TO_RECEIVE("ERP-PUR-3006", "收货单没有有效行",                  400);

    private final String code;
    private final String message;
    private final int httpStatus;

    ProcurementErrorCode(String c, String m, int h) { code = c; message = m; httpStatus = h; }

    @Override public String code() { return code; }
    @Override public String message() { return message; }
    @Override public int httpStatus() { return httpStatus; }
}
