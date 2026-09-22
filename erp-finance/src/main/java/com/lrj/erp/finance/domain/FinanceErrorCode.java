package com.lrj.erp.finance.domain;
import com.lrj.erp.kernel.error.ErrorCode;

/** 财务拒绝必须与系统异常区分，且不得暴露 SQL。 */
public enum FinanceErrorCode implements ErrorCode {
    NOT_FOUND("ERP-FIN-3001","往来单或收付款单不存在",404),
    EXCEEDS("ERP-FIN-3002","金额超过剩余额度",422),
    DUPLICATE("ERP-FIN-3003","该笔核销已存在或已经反核销",409),
    MISMATCH("ERP-FIN-3004","往来单、币种或业务内容不匹配",422),
    INVALID("ERP-FIN-3005","金额或业务参数非法",400);
    private final String code,message; private final int status;
    FinanceErrorCode(String code,String message,int status){this.code=code;this.message=message;this.status=status;}
    public String code(){return code;} public String message(){return message;} public int httpStatus(){return status;}
}
