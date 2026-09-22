package com.lrj.erp.finance.domain;
import java.math.BigDecimal;

/** 财务自己的持久化端口；不访问采购、销售表。 */
public interface FinanceRepository {
    /** 权威核销净额，供信用硬校验读取。 */
    BigDecimal releasedCredit(long tenantId,long customerId);
    record Bill(long id,long tenantId,long companyId,long partnerId,String documentNo,String currency,
                BigDecimal amount,BigDecimal writtenOffAmount,BigDecimal paidAmount,long version,
                String sourceDocType,String sourceDocId,String sourceDocNo,String orderId,String orgPath,long createdBy,BigDecimal creditedAmount) {}
    record Cash(long id,long billId,String documentNo,String currency,BigDecimal amount,BigDecimal writtenOffAmount,String commandId) {}
    record Settlement(long id,long tenantId,String billType,long billId,long cashId,BigDecimal amount,
                      String currency,Long reversalOf,boolean reversed) {}
    /** 来源唯一约束保证至少一次投递不产生第二张往来单；重复返回 null。 */
    Long insertBill(BillType type,Bill bill);
    Bill findSource(long tenantId,BillType type,String sourceType,String sourceId);
    Bill findBill(long tenantId,BillType type,long billId,boolean lock);
    Cash findCash(long tenantId,BillType type,long cashId,boolean lock);
    Cash findCommand(long tenantId,BillType type,String commandId);
    long insertCash(long tenantId,BillType type,Bill bill,String documentNo,BigDecimal amount,String commandId,long operatorId);
    boolean addPaid(long tenantId,BillType type,long billId,BigDecimal amount);
    boolean addWrittenOff(long tenantId,BillType type,long billId,long cashId,BigDecimal amount);
    long insertSettlement(long tenantId,BillType type,long billId,long cashId,BigDecimal amount,String currency,
                          Long reversalOf,long operatorId,String reason);
    Settlement findSettlement(long tenantId,long settlementId);
    boolean hasActivePair(long tenantId,BillType type,long billId,long cashId);
    boolean markReversed(long tenantId,long settlementId);
}
