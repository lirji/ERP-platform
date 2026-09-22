package com.lrj.erp.kernel.events;
import java.math.BigDecimal;
/** 退货财务事实：不引用来源模块类型，金额为冲红绝对值，币种沿用原来源单。 */
public record ReturnPosted(String sourceType,String sourceId,String returnType,String returnId,String returnNo,
        long companyId,long partnerId,BigDecimal amount,String currency,long operatorId) {
    public static final String PURCHASE="PurchaseReturnPosted.v1";
    public static final String SALES="SalesReturnPosted.v1";
    public ReturnPosted {
        if(sourceType==null || sourceId==null || sourceId.isBlank() || returnType==null
                || returnId==null || returnId.isBlank() || returnNo==null || returnNo.isBlank()
                || companyId<=0 || partnerId<=0 || operatorId<=0 || amount==null || amount.signum()<0
                || amount.stripTrailingZeros().scale()>4 || currency==null || !currency.matches("[A-Z]{3}"))
            throw new IllegalArgumentException("退货事件字段非法");
    }
}
