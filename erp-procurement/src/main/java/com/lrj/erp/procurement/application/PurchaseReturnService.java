package com.lrj.erp.procurement.application;
import com.lrj.erp.procurement.domain.*;
import com.lrj.erp.procurement.domain.PurchaseReturnRepository.*;
import com.lrj.erp.inventory.application.StockPostingService;
import com.lrj.erp.inventory.domain.*;
import com.lrj.erp.approval.service.ApprovalPort;
import com.lrj.erp.kernel.error.DomainException;
import com.lrj.erp.kernel.events.ReturnPosted;
import com.lrj.erp.kernel.outbox.OutboxRecorder;
import com.lrj.erp.kernel.statemachine.*;
import com.lrj.erp.numbering.service.NumberGenerator;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Map;

/** 独立退货单：原收发货行锁保护上界，库存与事件同事务；财务独立消费红字事实。 */
@Service
public class PurchaseReturnService {
    public static final String DOC_TYPE="PURCHASE_RETURN";
    private static final String PURCHASE_RECEIPT_TYPE="PURCHASE_RECEIPT";
    private final PurchaseReturnRepository repository;
    private final StockPostingService posting;
    private final ApprovalPort approvals;
    private final DocumentStateService states;
    private final NumberGenerator numbers;
    private final OutboxRecorder outbox;
    private final StateMachine<Return> machine=StandardDocumentStateMachine.<Return>builder().build();
    public PurchaseReturnService(PurchaseReturnRepository repository,StockPostingService posting,ApprovalPort approvals,
            DocumentStateService states,NumberGenerator numbers,OutboxRecorder outbox) {
        this.repository=repository;this.posting=posting;this.approvals=approvals;
        this.states=states;this.numbers=numbers;this.outbox=outbox;
    }
    /** 创建不可变来源行退货草稿；批次须显式给出，幂等键不可用于不同业务内容。 */
    @Transactional
    public long create(long tenantId,long sourceLineId,BigDecimal quantity,String batchNo,String commandId,String reason,long operator) {
        require(quantity!=null && quantity.signum()>0 && quantity.stripTrailingZeros().scale()<=6
                && batchNo!=null && !batchNo.isBlank() && batchNo.length()<=64
                && commandId!=null && !commandId.isBlank() && commandId.length()<=128
                && reason!=null && !reason.isBlank() && reason.length()<=256 && operator>0,"退货参数非法或批次未显式指定");
        Source source=source(tenantId,sourceLineId);
        Return existing=repository.command(tenantId,commandId);
        if(existing!=null) {
            require(existing.sourceLineId()==sourceLineId && existing.quantity().compareTo(quantity)==0
                    && existing.batchNo().equals(batchNo) && existing.reason().equals(reason),"幂等键内容不匹配");
            return existing.id();
        }
        require(quantity.compareTo(source.quantity().subtract(source.returnedQty()))<=0,"退货数量超过实际收发货未退量");
        require(source.batchNo().equals(batchNo),"采购退货必须对应原入库批次");
        return repository.insert(new Return(0,tenantId,numbers.next(tenantId,"PR",LocalDate.now()),sourceLineId,
                quantity,batchNo,commandId,reason,null,DocumentState.DRAFT.code(),0,operator));
    }
    /** 提交真实内置审批；草稿内容不可修改，防止审批快照与执行对象漂移。 */
    @Transactional
    public long submit(long tenantId,long id,long operator) {
        Return r=required(tenantId,id);
        if(!r.state().equals(DocumentState.APPROVING.code())) {
            move(r,DocumentEvent.SUBMIT,operator);
            move(required(tenantId,id),DocumentEvent.START_APPROVAL,operator);
        }
        return approvals.submit(tenantId,DOC_TYPE,""+id,r.documentNo(),operator);
    }
    /** 审批实例必须对应本租户本退货单。 */
    @Transactional
    public void approve(long tenantId,long id,long approvalId,long operator) {
        Return r=required(tenantId,id);
        if(r.state().equals(DocumentState.APPROVED.code())) {
            require(approvals.matches(tenantId,approvalId,DOC_TYPE,""+id,"APPROVED"),"审批归属不匹配");return;
        }
        require(approvals.matches(tenantId,approvalId,DOC_TYPE,""+id,"PENDING"),"审批归属不匹配");
        approvals.approve(tenantId,approvalId,operator);
        move(r,DocumentEvent.APPROVE,operator);
    }
    /** 锁原来源行后再计算累计金额差，重复执行只返回原结果。 */
    @Transactional
    public void execute(long tenantId,long id,long operator) {
        Return r=required(tenantId,id);
        if(r.state().equals(DocumentState.FINISHED.code())) return;
        move(r,DocumentEvent.PROCESS,operator);
        Source s=source(tenantId,r.sourceLineId());
        require(repository.addReturned(tenantId,r.sourceLineId(),r.quantity()),"累计退货超过来源行数量");
        BigDecimal amount=portion(s.postedAmount(),s.returnedQty().add(r.quantity()),s.quantity(),4)
                .subtract(portion(s.postedAmount(),s.returnedQty(),s.quantity(),4));
        BigDecimal incomingValue=null;
        // 采购退货按当前库存均价出库，冲红仍按原采购金额，两者差额保留在事实中。
        InventoryBucket bucket=InventoryBucket.ofBatch(tenantId,s.companyId(),s.warehouseId(),s.skuId(),r.batchNo());
        posting.post(new PostingRequest(bucket,PostingDirection.OUT,r.quantity(),DOC_TYPE,DOC_TYPE,""+id,"1",operator,incomingValue));
        repository.amount(r,amount);
        move(required(tenantId,id),DocumentEvent.FINISH,operator);
        outbox.record(tenantId,DOC_TYPE,""+id,ReturnPosted.PURCHASE,
                new ReturnPosted(PURCHASE_RECEIPT_TYPE,""+s.sourceId(),DOC_TYPE,""+id,r.documentNo(),s.companyId(),s.partnerId(),amount,s.currency(),operator));
        outbox.record(tenantId,DOC_TYPE,""+id,"PurchaseReturnLinked.v1",Map.of(
                "parentType",PURCHASE_RECEIPT_TYPE,"parentId",""+s.sourceId(),"parentNo",s.sourceNo(),
                "childType",DOC_TYPE,"childId",""+id,"childNo",r.documentNo()));
    }
    /** 已过账不允许取消；只能另建正向业务单，不能删除既成库存和财务事实。 */
    @Transactional
    public void cancel(long tenantId,long id,long operator) {
        Return r=required(tenantId,id);if(r.state().equals(DocumentState.CANCELLED.code())) return;
        move(r,DocumentEvent.CANCEL,operator);
    }
    /** 租户内读取，返回原退货金额及状态。 */
    @Transactional
    public Return get(long tenantId,long id) { return required(tenantId,id); }
    private Source source(long tenantId,long id) {
        Source s=repository.lockSource(tenantId,id);
        require(s!=null,"来源收发货行不存在");
        require(s.postedAmount()!=null && s.currency()!=null,"历史来源缺少金额快照，需先独立对账，禁止按现价猜算");
        return s;
    }
    private Return required(long tenantId,long id) {
        Return r=repository.lock(tenantId,id);require(r!=null,"退货单不存在");return r;
    }
    private void move(Return r,DocumentEvent event,long operator) {
        require(operator>0,"操作人必须有效");
        var next=states.transition(machine,r,DocumentState.of(r.state()),event,r.tenantId(),DOC_TYPE,""+r.id(),r.documentNo(),r.version(),operator,MDC.get("traceId"));
        require(repository.transition(r,next.code()),"退货单并发修改");
    }
    private BigDecimal portion(BigDecimal amount,BigDecimal quantity,BigDecimal total,int scale) {
        return amount.multiply(quantity).divide(total,scale,RoundingMode.HALF_UP);
    }
    private void require(boolean condition,String reason) {
        if(!condition) throw new DomainException(ProcurementErrorCode.INVALID_RETURN,Map.of("reason",reason));
    }
}
