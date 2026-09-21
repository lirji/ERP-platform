package com.lrj.erp.inventory.domain;

import com.lrj.erp.kernel.error.ErrorCode;

/** 库存错误码（ERP-INV-*）。与 contracts/API_P3_INVENTORY.md §7 一一对应。 */
public enum InventoryErrorCode implements ErrorCode {

    INVALID_POSTING     ("ERP-INV-1001", "过账参数非法",             400),
    INSUFFICIENT_STOCK  ("ERP-INV-3001", "可用库存不足",             422),
    RELEASE_EXCEEDS     ("ERP-INV-3002", "释放量超过未消耗的预占量", 422),
    RESERVATION_NOT_FOUND("ERP-INV-3003", "预占记录不存在",          422),
    BATCH_REQUIRED      ("ERP-INV-3004", "该商品启用批次管理，必须指定批次", 422);

    private final String code;
    private final String message;
    private final int httpStatus;

    InventoryErrorCode(String code, String message, int httpStatus) {
        this.code = code; this.message = message; this.httpStatus = httpStatus;
    }

    @Override public String code() { return code; }
    @Override public String message() { return message; }
    @Override public int httpStatus() { return httpStatus; }
}
