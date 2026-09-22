package com.lrj.erp.sales.domain;

import com.lrj.erp.kernel.error.ErrorCode;

/** 销售错误码（ERP-SAL-*）。 */
public enum SalesErrorCode implements ErrorCode {

    ORDER_NOT_FOUND  ("ERP-SAL-3001", "销售订单不存在",                 422),
    LINE_NOT_FOUND   ("ERP-SAL-3002", "销售订单行不存在",               422),
    CREDIT_EXCEEDED  ("ERP-SAL-3003", "客户信用额度不足",               422),
    OVER_SHIP        ("ERP-SAL-3004", "出库数量超过订购数量",           422),
    NOT_RESERVED     ("ERP-SAL-3005", "订单尚未完成库存预占，不能出库", 422),
    ALREADY_RESERVED ("ERP-SAL-3006", "订单已完成预占，不能重复预占",   409),
    CANCEL_AFTER_SHIP("ERP-SAL-3007", "订单已发生出库，不能取消；如需终止请关闭剩余", 422),
    NOTHING_TO_SHIP  ("ERP-SAL-3008", "发货单没有有效行",               400);

    private final String code;
    private final String message;
    private final int httpStatus;

    SalesErrorCode(String c, String m, int h) { code = c; message = m; httpStatus = h; }

    @Override public String code() { return code; }
    @Override public String message() { return message; }
    @Override public int httpStatus() { return httpStatus; }
}
