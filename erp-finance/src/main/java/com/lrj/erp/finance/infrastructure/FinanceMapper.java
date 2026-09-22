package com.lrj.erp.finance.infrastructure;
import com.lrj.erp.finance.domain.*;
import com.lrj.erp.finance.domain.FinanceRepository.*;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.math.BigDecimal;

/** SQL 在 XML；动态表只通过封闭 BillType 的固定分支选择。 */
@Mapper
public interface FinanceMapper {
    BigDecimal releasedCredit(@Param("tenantId") long tenantId,@Param("customerId") long customerId);
    @InterceptorIgnore(tenantLine = "true", dataPermission = "true")
    Long insertBill(@Param("type") BillType type, @Param("bill") Bill bill);
    Bill findSource(@Param("tenantId") long tenantId, @Param("type") BillType type, @Param("sourceType") String sourceType, @Param("sourceId") String sourceId);
    Bill findBill(@Param("tenantId") long tenantId, @Param("type") BillType type, @Param("billId") long billId, @Param("lock") boolean lock);
    Cash findCash(@Param("tenantId") long tenantId, @Param("type") BillType type, @Param("cashId") long cashId, @Param("lock") boolean lock);
    Cash findCommand(@Param("tenantId") long tenantId, @Param("type") BillType type, @Param("commandId") String commandId);
    @InterceptorIgnore(tenantLine = "true", dataPermission = "true")
    long insertCash(@Param("tenantId") long tenantId, @Param("type") BillType type, @Param("bill") Bill bill, @Param("documentNo") String documentNo, @Param("amount") BigDecimal amount, @Param("commandId") String commandId, @Param("operatorId") long operatorId);
    int addPaid(@Param("tenantId") long tenantId, @Param("type") BillType type, @Param("billId") long billId, @Param("amount") BigDecimal amount);
    int addBillWrittenOff(@Param("tenantId") long tenantId, @Param("type") BillType type, @Param("billId") long billId, @Param("amount") BigDecimal amount);
    int addCashWrittenOff(@Param("tenantId") long tenantId, @Param("type") BillType type, @Param("cashId") long cashId, @Param("amount") BigDecimal amount);
    @InterceptorIgnore(tenantLine = "true", dataPermission = "true")
    long insertSettlement(@Param("tenantId") long tenantId, @Param("type") BillType type, @Param("billId") long billId, @Param("cashId") long cashId, @Param("amount") BigDecimal amount, @Param("currency") String currency, @Param("reversalOf") Long reversalOf, @Param("operatorId") long operatorId, @Param("reason") String reason);
    Settlement findSettlement(@Param("tenantId") long tenantId, @Param("settlementId") long settlementId);
    boolean hasActivePair(@Param("tenantId") long tenantId, @Param("type") BillType type, @Param("billId") long billId, @Param("cashId") long cashId);
    int markReversed(@Param("tenantId") long tenantId, @Param("settlementId") long settlementId);
}
