package com.lrj.erp.finance.application;
import com.lrj.erp.finance.domain.*;
import com.lrj.erp.finance.domain.CreditRepository.*;
import com.lrj.erp.kernel.events.ReturnPosted;
import com.lrj.erp.kernel.error.DomainException;
import com.lrj.erp.kernel.outbox.OutboxRecorder;
import com.lrj.erp.numbering.service.NumberGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import static com.lrj.erp.finance.domain.FinanceErrorCode.*;

/** 独立红字、退款义务和退款事实；原往来金额、现金及核销流水保持不变。 */
@Service
public class CreditAdjustmentService {
    private final FinanceRepository bills;
    private final CreditRepository credits;
    private final NumberGenerator numbers;
    private final OutboxRecorder outbox;
    public CreditAdjustmentService(FinanceRepository bills,CreditRepository credits,NumberGenerator numbers,OutboxRecorder outbox) {
        this.bills=bills;this.credits=credits;this.numbers=numbers;this.outbox=outbox;
    }
    /** 与收付款共用原往来单行锁；来源未到达时抛错交给 Outbox 重试。 */
    @Transactional
    public long accept(long tenantId,BillType type,ReturnPosted event) {
        if(type==null || event==null) throw error(INVALID);
        if(!(type==BillType.AR?"SALES_SHIPMENT":"PURCHASE_RECEIPT").equals(event.sourceType())
                || !(type==BillType.AR?"SALES_RETURN":"PURCHASE_RETURN").equals(event.returnType())) throw error(MISMATCH);
        var source=bills.findSource(tenantId,type,event.sourceType(),event.sourceId());
        if(source==null) throw error(NOT_FOUND);
        var bill=bills.findBill(tenantId,type,source.id(),true);
        if(bill.companyId()!=event.companyId() || bill.partnerId()!=event.partnerId() || !bill.currency().equals(event.currency())) throw error(MISMATCH);
        Credit existing=credits.findSource(tenantId,event.returnType(),event.returnId());
        if(existing!=null) {
            if(existing.billId()!=bill.id() || !existing.billType().equals(type.code())
                    || existing.amount().negate().compareTo(event.amount())!=0 || !existing.returnNo().equals(event.returnNo())) throw error(MISMATCH);
            return existing.id();
        }
        BigDecimal beforeDue=bill.paidAmount().add(bill.creditedAmount()).subtract(bill.amount()).max(BigDecimal.ZERO);
        BigDecimal afterDue=bill.paidAmount().add(bill.creditedAmount()).add(event.amount()).subtract(bill.amount()).max(BigDecimal.ZERO);
        if(!credits.addCredit(tenantId,type,bill.id(),event.amount())) throw error(EXCEEDS);
        String no=numbers.next(tenantId,"CREDIT",LocalDate.now());
        long id=credits.insert(new Credit(0,tenantId,type.code(),bill.id(),no,event.amount().negate(),event.currency(),
                event.returnType(),event.returnId(),event.returnNo(),afterDue.subtract(beforeDue),BigDecimal.ZERO),event.operatorId());
        outbox.record(tenantId,"CreditAdjustment",""+id,"CreditAdjustmentCreated.v1",
                Map.of("parentType",event.returnType(),"parentId",event.returnId(),"parentNo",event.returnNo(),
                        "childType","CREDIT_ADJUSTMENT","childId",""+id,"childNo",no));
        return id;
    }

    /** 人工确认退款已发生；不调用银行，不修改原现金单。幂等键内容不同必须拒绝。 */
    @Transactional
    public long recordRefund(long tenantId,long creditId,BigDecimal amount,String currency,String commandId,long operator) {
        if(amount==null || amount.signum()<=0 || amount.stripTrailingZeros().scale()>4
                || commandId==null || commandId.isBlank() || commandId.length()>128 || operator<=0) throw error(INVALID);
        Credit c=require(tenantId,creditId);
        // 锁顺序与冲红/收付款一致；同一义务并发确认不允许超退。
        if(bills.findBill(tenantId,BillType.valueOf(c.billType()),c.billId(),true)==null) throw error(NOT_FOUND);
        c=require(tenantId,creditId);
        if(!c.currency().equals(currency)) throw error(MISMATCH);
        Refund existing=credits.findCommand(tenantId,commandId);
        if(existing!=null) {
            if(existing.adjustmentId()!=creditId || existing.amount().compareTo(amount)!=0 || !existing.currency().equals(currency)) throw error(MISMATCH);
            return existing.id();
        }
        if(!credits.addRefund(tenantId,creditId,amount)) throw error(EXCEEDS);
        long id=credits.insertRefund(tenantId,creditId,amount,currency,commandId,operator);
        outbox.record(tenantId,"Refund",""+id,"RefundRecorded.v1",
                Map.of("creditId",creditId,"billType",c.billType(),"amount",amount,"currency",currency,"operatorId",operator));
        return id;
    }
    /** 查询红字单及待退/已退金额；查询必须限定租户。 */
    public Credit get(long tenantId,long id) { return require(tenantId,id); }
    private Credit require(long tenantId,long id) {
        Credit c=credits.find(tenantId,id);if(c==null) throw error(NOT_FOUND);return c;
    }
    private DomainException error(FinanceErrorCode code) { return new DomainException(code); }
}
