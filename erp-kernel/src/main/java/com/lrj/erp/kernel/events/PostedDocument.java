package com.lrj.erp.kernel.events;
import java.math.BigDecimal;

/** v2 出入库财务事实；保留关系字段使原关系投影消费者继续兼容。 */
public record PostedDocument(String parentType,String parentId,String parentNo,
                             String childType,String childId,String childNo,
                             long companyId,long partnerId,BigDecimal amount,String currency,
                             String orgPath,long operatorId) {
    public static final String PURCHASE = "PurchaseReceiptPosted.v2";
    public static final String SALES = "ShipmentPosted.v2";
    public PostedDocument {
        if (companyId<=0 || partnerId<=0 || amount==null || amount.signum()<0
                || amount.stripTrailingZeros().scale()>4 || currency==null || !currency.matches("[A-Z]{3}")
                || parentType==null || parentId==null || parentNo==null
                || childType==null || childId==null || childNo==null || orgPath==null || operatorId<=0) {
            throw new IllegalArgumentException("财务事件字段缺失或非法");
        }
    }
}
