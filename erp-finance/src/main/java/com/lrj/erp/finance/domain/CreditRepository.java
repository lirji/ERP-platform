package com.lrj.erp.finance.domain;
import java.math.BigDecimal;
/** 红字与退款端口，只访问财务自己的单据。 */
public interface CreditRepository {
    record Credit(long id,long tenantId,String billType,long billId,String documentNo,BigDecimal amount,
            String currency,String returnType,String returnId,String returnNo,BigDecimal refundDue,BigDecimal refundedAmount) {}
    record Refund(long id,long adjustmentId,BigDecimal amount,String currency,String commandId) {}
    /** 调用方先锁原往来单。 */
    Credit findSource(long tenantId,String returnType,String returnId);
    Credit find(long tenantId,long id);
    boolean addCredit(long tenantId,BillType type,long billId,BigDecimal amount);
    long insert(Credit credit,long operator);
    Refund findCommand(long tenantId,String commandId);
    boolean addRefund(long tenantId,long id,BigDecimal amount);
    long insertRefund(long tenantId,long id,BigDecimal amount,String currency,String commandId,long operator);
}
