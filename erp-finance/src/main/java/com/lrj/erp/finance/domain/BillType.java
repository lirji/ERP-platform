package com.lrj.erp.finance.domain;

/** 封闭的往来类型；SQL 只按这两个类型选择固定表名，不接受外部表名。 */
public enum BillType {
    AR("AR", "RECEIVABLE", "RECEIPT"), AP("AP", "PAYABLE", "PAYMENT");
    private final String code, billDocumentType, cashDocumentType;
    BillType(String code, String billDocumentType, String cashDocumentType) {
        this.code=code; this.billDocumentType=billDocumentType; this.cashDocumentType=cashDocumentType;
    }
    public String code() { return code; }
    public String billDocumentType() { return billDocumentType; }
    public String cashDocumentType() { return cashDocumentType; }
}
