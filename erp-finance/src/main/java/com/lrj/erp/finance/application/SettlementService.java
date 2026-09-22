package com.lrj.erp.finance.application;

import com.lrj.erp.finance.domain.*;
import com.lrj.erp.finance.domain.FinanceRepository.*;
import com.lrj.erp.kernel.error.DomainException;
import com.lrj.erp.kernel.events.PostedDocument;
import com.lrj.erp.kernel.outbox.OutboxRecorder;
import com.lrj.erp.numbering.service.NumberGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import static com.lrj.erp.finance.domain.FinanceErrorCode.*;

/** AR/AP 共用核销算法；往来单、现金单、核销流水与事件必须同事务提交。 */
@Service
public class SettlementService {
    private final FinanceRepository repository;
    private final OutboxRecorder outbox;
    private final NumberGenerator numbering;
    public SettlementService(FinanceRepository repository,OutboxRecorder outbox,NumberGenerator numbering){
        this.repository=repository;this.outbox=outbox;this.numbering=numbering;
    }

    /** 来源事件至少一次投递；先查后插只减少工作量，真正幂等由数据库唯一键保证。 */
    @Transactional(timeout=30)
    public long acceptPosted(long tenantId,BillType type,PostedDocument source){
        requireType(type);
        String sourceType=type==BillType.AR?"SALES_SHIPMENT":"PURCHASE_RECEIPT";
        String parentType=type==BillType.AR?"SALES_ORDER":"PURCHASE_ORDER";
        if(!sourceType.equals(source.childType()) || !parentType.equals(source.parentType())) throw error(MISMATCH);
        Bill existing=repository.findSource(tenantId,type,source.childType(),source.childId());
        if(existing!=null){verifySameSource(existing,source);return existing.id();}
        String no=next(tenantId,type.code());
        Bill candidate=new Bill(0,tenantId,source.companyId(),source.partnerId(),no,source.currency(),
                source.amount(),BigDecimal.ZERO,BigDecimal.ZERO,0,source.childType(),source.childId(),source.childNo(),
                source.parentId(),source.orgPath(),source.operatorId(),BigDecimal.ZERO);
        Long id=repository.insertBill(type,candidate);
        if(id==null){
            Bill winner=repository.findSource(tenantId,type,source.childType(),source.childId());
            verifySameSource(winner,source);return winner.id();
        }
        outbox.record(tenantId,type.billDocumentType(),""+id,type==BillType.AR?"ReceivableCreated.v1":"PayableCreated.v1",
                relation(source.childType(),source.childId(),source.childNo(),type.billDocumentType(),""+id,no));
        return id;
    }

    /** 同一来源不能以不同金额/币种重放，冲突必须隔离而不能悄悄覆盖财务事实。 */
    private void verifySameSource(Bill bill,PostedDocument source){
        if(bill==null || bill.amount().compareTo(source.amount())!=0 || !bill.currency().equals(source.currency())
                || bill.companyId()!=source.companyId() || bill.partnerId()!=source.partnerId()
                || !bill.orderId().equals(source.parentId()))throw error(MISMATCH);
    }

    /** 记录真实收付款凭证；幂等键同内容重试返回原单，不能借重试扩大金额。 */
    @Transactional(timeout=30)
    public long recordCash(long tenantId,BillType type,long billId,BigDecimal amount,String currency,
                           String commandId,long operatorId){
        requireType(type);positive(amount);
        if(commandId==null || commandId.isBlank() || commandId.length()>64 || operatorId<=0)throw error(INVALID);
        Bill bill=requireBill(tenantId,type,billId,true);
        if(!bill.currency().equals(currency))throw error(MISMATCH);
        Cash existing=repository.findCommand(tenantId,type,commandId);
        if(existing!=null){
            if(existing.billId()!=billId || existing.amount().compareTo(amount)!=0 || !existing.currency().equals(currency))throw error(MISMATCH);
            return existing.id();
        }
        if(!repository.addPaid(tenantId,type,billId,amount))throw error(EXCEEDS);
        String no=next(tenantId,type==BillType.AR?"RCV":"PAY");
        long id=repository.insertCash(tenantId,type,bill,no,amount,commandId,operatorId);
        outbox.record(tenantId,type.cashDocumentType(),""+id,type==BillType.AR?"ReceiptCompleted.v1":"PaymentCompleted.v1",
                relation(type.billDocumentType(),""+billId,bill.documentNo(),type.cashDocumentType(),""+id,no));
        return id;
    }

    /** 固定锁顺序，检查两端额度后同时核销；同一有效单据对不允许再次核销。 */
    @Transactional(timeout=30)
    public long apply(long tenantId,BillType type,long billId,long cashId,BigDecimal amount,long operatorId){
        requireType(type);positive(amount);if(operatorId<=0)throw error(INVALID);
        Bill bill=requireBill(tenantId,type,billId,true);
        Cash cash=repository.findCash(tenantId,type,cashId,true);
        if(cash==null)throw error(NOT_FOUND);
        if(cash.billId()!=billId || !cash.currency().equals(bill.currency()))throw error(MISMATCH);
        if(repository.hasActivePair(tenantId,type,billId,cashId))throw error(DUPLICATE);
        if(!repository.addWrittenOff(tenantId,type,billId,cashId,amount))throw error(EXCEEDS);
        long id=repository.insertSettlement(tenantId,type,billId,cashId,amount,bill.currency(),null,operatorId,null);
        settlementEvent(tenantId,type,bill,cashId,id,amount,false);
        return id;
    }

    /** 反核销是补偿：新增负金额流水，不删除原记录，也不撤销实际付款。 */
    @Transactional(timeout=30)
    public long reverse(long tenantId,long settlementId,long operatorId,String reason){
        if(reason==null || reason.isBlank() || reason.length()>512 || operatorId<=0)throw error(INVALID);
        Settlement original=repository.findSettlement(tenantId,settlementId);
        if(original==null)throw error(NOT_FOUND);
        BillType type=BillType.valueOf(original.billType());
        Bill bill=requireBill(tenantId,type,original.billId(),true);
        repository.findCash(tenantId,type,original.cashId(),true);
        // 先取得两端锁，再条件标记原记录；并发反核销只有一次能成功。
        if(!repository.markReversed(tenantId,settlementId))throw error(DUPLICATE);
        BigDecimal delta=original.amount().negate();
        if(!repository.addWrittenOff(tenantId,type,bill.id(),original.cashId(),delta))throw error(EXCEEDS);
        long id=repository.insertSettlement(tenantId,type,bill.id(),original.cashId(),delta,bill.currency(),settlementId,operatorId,reason);
        settlementEvent(tenantId,type,bill,original.cashId(),id,delta,true);
        return id;
    }

    private void settlementEvent(long tenantId,BillType type,Bill bill,long cashId,long id,BigDecimal amount,boolean reversed){
        outbox.record(tenantId,"Settlement",""+id,reversed?"SettlementReversed.v1":"SettlementApplied.v1",
                Map.of("billType",type.code(),"billId",bill.id(),"cashId",cashId,"amount",amount,
                        "currency",bill.currency(),"partnerId",bill.partnerId(),"orderId",bill.orderId(),"settlementId",id));
    }
    private Map<String,String> relation(String parentType,String parentId,String parentNo,String childType,String childId,String childNo){
        return Map.of("parentType",parentType,"parentId",parentId,"parentNo",parentNo,
                "childType",childType,"childId",childId,"childNo",childNo);
    }
    private String next(long tenantId,String type){return numbering.next(tenantId,type,LocalDate.now(ZoneId.of("Asia/Shanghai")));}
    private void requireType(BillType type){if(type==null)throw error(INVALID);}
    private void positive(BigDecimal amount){
        if(amount==null || amount.signum()<=0 || amount.stripTrailingZeros().scale()>4 || amount.precision()-amount.scale()>14)throw error(INVALID);
    }
    private Bill requireBill(long tenantId,BillType type,long id,boolean lock){
        Bill bill=repository.findBill(tenantId,type,id,lock);if(bill==null)throw error(NOT_FOUND);return bill;
    }
    private DomainException error(FinanceErrorCode code){return new DomainException(code,Map.of());}
    /** 按来源定位财务单，供业务查询与对账，不跨表读取来源模块。 */
    public Bill source(long tenantId,BillType type,String sourceType,String sourceId){requireType(type);return repository.findSource(tenantId,type,sourceType,sourceId);}
    /** 查询往来余额。 */
    public Bill bill(long tenantId,BillType type,long billId){requireType(type);return requireBill(tenantId,type,billId,false);}
    /** 查询现金单据的可核销金额。 */
    public Cash cash(long tenantId,BillType type,long cashId){requireType(type);return repository.findCash(tenantId,type,cashId,false);}
    /** 保留原核销与反向记录，支持审计追溯。 */
    public Settlement settlement(long tenantId,long id){return repository.findSettlement(tenantId,id);}
}
