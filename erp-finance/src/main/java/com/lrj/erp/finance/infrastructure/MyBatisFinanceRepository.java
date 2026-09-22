package com.lrj.erp.finance.infrastructure;
import com.lrj.erp.finance.domain.*;
import java.math.BigDecimal;
import org.springframework.stereotype.Repository;

/** 将 SQL 影响行数映射为业务结果；事务边界由应用用例持有。 */
@Repository
public class MyBatisFinanceRepository implements FinanceRepository {
    @Override public BigDecimal releasedCredit(long tenantId,long customerId){return mapper.releasedCredit(tenantId,customerId);}
    private final FinanceMapper mapper;
    public MyBatisFinanceRepository(FinanceMapper mapper) { this.mapper=mapper; }
    @Override public Long insertBill(BillType type, Bill bill) {
        return mapper.insertBill(type, bill);
    }
    @Override public Bill findSource(long tenantId, BillType type, String sourceType, String sourceId) {
        return mapper.findSource(tenantId, type, sourceType, sourceId);
    }
    @Override public Bill findBill(long tenantId, BillType type, long billId, boolean lock) {
        return mapper.findBill(tenantId, type, billId, lock);
    }
    @Override public Cash findCash(long tenantId, BillType type, long cashId, boolean lock) {
        return mapper.findCash(tenantId, type, cashId, lock);
    }
    @Override public Cash findCommand(long tenantId, BillType type, String commandId) {
        return mapper.findCommand(tenantId, type, commandId);
    }
    @Override public long insertCash(long tenantId, BillType type, Bill bill, String documentNo, BigDecimal amount, String commandId, long operatorId) {
        return mapper.insertCash(tenantId, type, bill, documentNo, amount, commandId, operatorId);
    }
    @Override public boolean addPaid(long tenantId, BillType type, long billId, BigDecimal amount) {
        return mapper.addPaid(tenantId, type, billId, amount) == 1;
    }
    @Override public long insertSettlement(long tenantId, BillType type, long billId, long cashId, BigDecimal amount, String currency, Long reversalOf, long operatorId, String reason) {
        return mapper.insertSettlement(tenantId, type, billId, cashId, amount, currency, reversalOf, operatorId, reason);
    }
    @Override public Settlement findSettlement(long tenantId, long settlementId) {
        return mapper.findSettlement(tenantId, settlementId);
    }
    @Override public boolean hasActivePair(long tenantId, BillType type, long billId, long cashId) {
        return mapper.hasActivePair(tenantId, type, billId, cashId);
    }
    @Override public boolean markReversed(long tenantId, long settlementId) {
        return mapper.markReversed(tenantId, settlementId) == 1;
    }
    @Override public boolean addWrittenOff(long tenantId,BillType type,long billId,long cashId,BigDecimal amount) {
        // 任一端失败由应用层抛出并回滚整笔，不能提交半边核销。
        return mapper.addBillWrittenOff(tenantId,type,billId,amount)==1
                && mapper.addCashWrittenOff(tenantId,type,cashId,amount)==1;
    }
}
