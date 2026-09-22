package com.lrj.erp.inventory.application;

import com.lrj.erp.inventory.domain.*;
import com.lrj.erp.approval.service.ApprovalPort;
import com.lrj.erp.kernel.error.DomainException;
import com.lrj.erp.kernel.statemachine.*;
import com.lrj.erp.numbering.service.NumberGenerator;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Map;
import static com.lrj.erp.inventory.domain.StockOperation.DOC_TYPE;

/** 单桶库存作业。单据锁序列化状态，桶锁保证冻结、数量与计价处在同一事务。 */
@Service
public class StockOperationService {
    private final StockOperationRepository repository;
    private final InventoryRepository inventory;
    private final StockPostingService posting;
    private final ApprovalPort approvals;
    private final DocumentStateService states;
    private final NumberGenerator numbers;
    private final StateMachine<StockOperation> machine=StandardDocumentStateMachine.<StockOperation>builder().build();

    public StockOperationService(StockOperationRepository repository, InventoryRepository inventory,
            StockPostingService posting, ApprovalPort approvals, DocumentStateService states, NumberGenerator numbers) {
        this.repository=repository; this.inventory=inventory; this.posting=posting;
        this.approvals=approvals; this.states=states; this.numbers=numbers;
    }

    /** 创建调拨草稿；同公司同 SKU，批次沿用原批次以保留追溯。整单调出、整单接收。 */
    @Transactional
    public long createTransfer(InventoryBucket source, InventoryBucket target, BigDecimal quantity, String reason, long operator) {
        require(source!=null && target!=null && source.tenantId()==target.tenantId()
                && source.companyId()==target.companyId() && source.skuId()==target.skuId()
                && source.batchNo().equals(target.batchNo()) && !source.equals(target), "调拨须同公司同 SKU 同批次且目标不同");
        validateQuantity(quantity, false);
        require(quantity.signum()>0,"调拨数量须为正");
        return create(StockOperationKind.TRANSFER, source,target,quantity,null,StockOperationKind.TRANSFER.code(),reason,operator);
    }

    /** 开始盘点即冻结桶；实盘数量稍后录入，避免把盘点期间收发货覆盖掉。 */
    @Transactional
    public long beginCount(InventoryBucket source, String reason, long operator) {
        long id=create(StockOperationKind.COUNT,source,null,null,null,StockOperationKind.COUNT.code(),reason,operator);
        require(repository.freeze(source,id),"同一桶已有盘点");
        return id;
    }

    /** 创建调整草稿；正数增加，负数减少。入库成本未知时显式传空。 */
    @Transactional
    public long createAdjustment(InventoryBucket source, BigDecimal difference, BigDecimal inboundValue, AdjustmentReason reasonCode, String reason, long operator) {
        require(reasonCode!=null,"调整原因码不能为空");
        validateQuantity(difference,false);
        require(difference.signum()!=0,"调整数量不可为零");
        require(inboundValue==null || difference.signum()>0 && inboundValue.signum()>=0 && inboundValue.scale()<=6,
                "仅正向调整可指定非负入库成本");
        return create(StockOperationKind.ADJUST,source,null,difference,inboundValue,reasonCode.code(),reason,operator);
    }

    private long create(StockOperationKind kind, InventoryBucket b, InventoryBucket target,
                        BigDecimal quantity, BigDecimal value, String reasonCode, String reason, long operator) {
        require(operator>0 && b!=null && reason!=null && !reason.isBlank() && reason.length()<=256,"库存作业必须说明原因");
        inventory.ensureBucket(b);
        CostSnapshot snapshot=inventory.lockCost(b);
        require(snapshot.countDocumentId()==null,"库存桶已冻结");
        return repository.insert(new StockOperation(0,b.tenantId(),b.companyId(),
                numbers.next(b.tenantId(),"STOCK",LocalDate.now()),kind.code(),b.warehouseId(),b.locationId(),b.skuId(),b.batchNo(),
                target==null?null:target.warehouseId(),target==null?null:target.locationId(),target==null?null:target.batchNo(),
                quantity,snapshot.quantity(),kind==StockOperationKind.COUNT?snapshot.inventoryValue():value,
                reason,DocumentState.DRAFT.code(),0,reasonCode,operator));
    }

    /** 只允许草稿盘点录入；差异相对冻结快照计算，不直接修改余额。 */
    @Transactional
    public void recordCount(long tenantId,long id,BigDecimal counted) {
        validateQuantity(counted,true);
        StockOperation o=required(tenantId,id);
        require(o.kind().equals(StockOperationKind.COUNT.code()) && o.state().equals(DocumentState.DRAFT.code()),"仅草稿盘点可录入");
        repository.recordCount(o,counted.subtract(o.snapshotQuantity()));
    }

    /** 提交前保证实盘已录入；重复提交返回同一在审实例。 */
    @Transactional
    public long submit(long tenantId,long id,long operator) {
        StockOperation o=required(tenantId,id);
        if(o.state().equals(DocumentState.APPROVING.code()))
            return approvals.submit(tenantId,DOC_TYPE,""+id,o.documentNo(),operator);
        require(o.quantity()!=null,"请先录入实盘数量");
        move(o,DocumentEvent.SUBMIT,operator);
        move(required(tenantId,id),DocumentEvent.START_APPROVAL,operator);
        return approvals.submit(tenantId,DOC_TYPE,""+id,o.documentNo(),operator);
    }

    /** 审批实例必须属于本单；库存副作用仍需独立执行操作。 */
    @Transactional
    public void approve(long tenantId,long id,long approvalId,long operator) {
        StockOperation o=required(tenantId,id);
        if(o.state().equals(DocumentState.APPROVED.code())) {
            require(approvals.matches(tenantId,approvalId,DOC_TYPE,""+id,"APPROVED"),"审批归属不匹配");
            return;
        }
        require(approvals.matches(tenantId,approvalId,DOC_TYPE,""+id,"PENDING"),"审批归属不匹配");
        approvals.approve(tenantId,approvalId,operator);
        move(o,DocumentEvent.APPROVE,operator);
    }

    /** 调拨执行只调出到在途；盘点/调整执行后直接完成。重复执行不产生第二份流水。 */
    @Transactional
    public void execute(long tenantId,long id,long operator) {
        StockOperation o=required(tenantId,id);
        if(o.state().equals(DocumentState.FINISHED.code()) || o.state().equals(DocumentState.PROCESSING.code())) return;
        move(o,DocumentEvent.PROCESS,operator);
        boolean transfer=o.kind().equals(StockOperationKind.TRANSFER.code());
        BigDecimal delta=transfer?o.quantity().negate():o.quantity();
        inventory.ensureBucket(o.source());
        CostSnapshot before=inventory.lockCost(o.source());
        // 先在持有桶锁的本事务解冻；失败会回滚冻结，其他事务无法插入盘点过账窗口。
        if(o.kind().equals(StockOperationKind.COUNT.code())) require(repository.unfreeze(o.source(),id),"盘点冻结归属不匹配");
        if(delta.signum()!=0) {
            BigDecimal value=o.inventoryValue();
            if(o.kind().equals(StockOperationKind.COUNT.code()) && delta.signum()>0) {
                // 零库存没有均价依据；盘盈成本不得凭空推定为零。
                value=before.inventoryValue()==null || before.quantity().signum()==0?null
                        :before.inventoryValue().multiply(delta).divide(before.quantity(),6,RoundingMode.HALF_UP);
            }
            posting.post(new PostingRequest(o.source(),delta.signum()>0?PostingDirection.IN:PostingDirection.OUT,
                    delta.abs(),o.kind(),DOC_TYPE,""+id,"SOURCE",operator,delta.signum()>0?value:null));
        }
        if(transfer) {
            repository.recordValue(o,before.outgoingValue(o.quantity()));
            require(repository.transit(o.source(),o.quantity()),"在途写入失败");
        } else {
            move(required(tenantId,id),DocumentEvent.FINISH,operator);
        }
    }

    /** 整单接收以调出时成本入库，源仓在途减少；两个变化同事务，失败保持在途。 */
    @Transactional
    public void receiveTransfer(long tenantId,long id,long operator) {
        StockOperation o=required(tenantId,id);
        require(o.kind().equals(StockOperationKind.TRANSFER.code()),"仅调拨可接收");
        if(o.state().equals(DocumentState.FINISHED.code())) return;
        require(o.state().equals(DocumentState.PROCESSING.code()),"调拨尚未调出");
        require(repository.transit(o.source(),o.quantity().negate()),"在途数量不足");
        posting.post(new PostingRequest(o.target(),PostingDirection.IN,o.quantity(),o.kind(),DOC_TYPE,""+id,"TARGET",operator,o.inventoryValue()));
        move(o,DocumentEvent.FINISH,operator);
    }

    /** 执行前可取消；盘点释放冻结，已调出作业不能用取消抹去事实。 */
    @Transactional
    public void cancel(long tenantId,long id,long operator) {
        StockOperation o=required(tenantId,id);
        if(o.state().equals(DocumentState.CANCELLED.code())) return;
        move(o,DocumentEvent.CANCEL,operator);
        if(o.kind().equals(StockOperationKind.COUNT.code())) require(repository.unfreeze(o.source(),id),"盘点冻结归属不匹配");
    }

    /** 租户内查询，锁在本事务结束时释放。 */
    @Transactional
    public StockOperation get(long tenantId,long id) { return required(tenantId,id); }

    private StockOperation required(long tenantId,long id) {
        StockOperation o=repository.lock(tenantId,id);
        require(o!=null,"库存作业不存在");
        return o;
    }
    private void move(StockOperation o,DocumentEvent event,long operator) {
        DocumentState next=states.transition(machine,o,DocumentState.of(o.state()),event,o.tenantId(),DOC_TYPE,
                ""+o.id(),o.documentNo(),o.version(),operator,MDC.get("traceId"));
        require(repository.transition(o,next.code()),"库存作业并发变更");
    }
    private void validateQuantity(BigDecimal quantity, boolean nonnegative) {
        require(quantity!=null && quantity.scale()<=6 && (!nonnegative || quantity.signum()>=0),"数量精度须不超过六位且实盘不可为负");
    }
    private void require(boolean condition,String reason) {
        if(!condition) throw new DomainException(InventoryErrorCode.INVALID_OPERATION,Map.of("reason",reason));
    }
}
