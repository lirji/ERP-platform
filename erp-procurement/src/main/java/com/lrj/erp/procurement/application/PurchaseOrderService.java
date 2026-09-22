package com.lrj.erp.procurement.application;

import com.lrj.erp.approval.service.ApprovalPort;
import com.lrj.erp.inventory.application.StockPostingService;
import com.lrj.erp.inventory.domain.*;
import com.lrj.erp.kernel.error.DomainException;
import com.lrj.erp.kernel.masterdata.MasterDataRef;
import com.lrj.erp.kernel.outbox.OutboxRecorder;
import com.lrj.erp.kernel.statemachine.*;
import com.lrj.erp.numbering.service.NumberGenerator;
import com.lrj.erp.procurement.domain.ProcurementErrorCode;
import com.lrj.erp.procurement.domain.ProcurementRepository;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import com.lrj.erp.kernel.events.PostedDocument;
import com.lrj.erp.masterdata.service.CurrencyService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 采购订单用例：下单 → 审批 → 分批收货入库 → 关闭 / 取消。
 *
 * <p>本服务是 P2P 链路的编排者，业务规则分布在各自的归属处：
 * 状态合法性在状态机、不超收在订单行的上界条件与 CHECK 约束、
 * 库存幂等在流水唯一索引。编排层负责把它们按正确顺序串起来，
 * 而不是把规则复制一份到这里。
 */
@Service
public class PurchaseOrderService {

    public static final String DOC_TYPE_ORDER = "PURCHASE_ORDER";
    public static final String DOC_TYPE_RECEIPT = "PURCHASE_RECEIPT";

    private final ProcurementRepository repository;
    private final NumberGenerator numberGenerator;
    private final ApprovalPort approvalPort;
    private final DocumentStateService documentState;
    /** 关系图通过事件构建：采购不得依赖 erp-document（依赖矩阵由 ModuleDependencyMatrixTest 强制）。 */
    private final OutboxRecorder outbox;
    private final CurrencyService currencies;
    private final StockPostingService posting;

    private final StateMachine<ProcurementRepository.OrderHeader> machine =
            StandardDocumentStateMachine.<ProcurementRepository.OrderHeader>builder().build();

    public PurchaseOrderService(ProcurementRepository repository,
                                NumberGenerator numberGenerator,
                                ApprovalPort approvalPort,
                                DocumentStateService documentState,
                                OutboxRecorder outbox,
                                StockPostingService posting, CurrencyService currencies) {
        this.currencies = currencies;
        this.repository = repository;
        this.numberGenerator = numberGenerator;
        this.approvalPort = approvalPort;
        this.documentState = documentState;
        this.outbox = outbox;
        this.posting = posting;
    }

    // ------------------------------------------------------------- 下单

    /** 新建采购订单（DRAFT）。 */
    @Transactional
    public long createOrder(long tenantId, long companyId, long supplierId, long warehouseId,
                            MasterDataRef supplierRef, List<NewLine> lines,
                            String orgPath, long operatorId) {
        String orderNo = numberGenerator.next(tenantId, "PO", LocalDate.now());
        long orderId = repository.insertOrder(tenantId, companyId, orderNo, supplierId,
                toJson(supplierRef), warehouseId, orgPath, operatorId);

        int lineNo = 1;
        for (NewLine l : lines) {
            repository.insertOrderLine(tenantId, orderId, lineNo++, l.skuId(),
                    toJson(l.skuRef()), l.quantity(), l.unitPrice());
        }
        return orderId;
    }

    // ------------------------------------------------------------- 审批

    /** 提交审批：DRAFT → SUBMITTED → APPROVING，并在审批中心建实例。 */
    @Transactional
    public long submitForApproval(long tenantId, long orderId, long operatorId) {
        ProcurementRepository.OrderHeader order = requireOrder(tenantId, orderId);
        long v = order.version();
        v = transition(order, DocumentEvent.SUBMIT, v, operatorId);
        transition(requireOrder(tenantId, orderId), DocumentEvent.START_APPROVAL, v, operatorId);
        return approvalPort.submit(tenantId, DOC_TYPE_ORDER, String.valueOf(orderId),
                order.orderNo(), operatorId);
    }

    /** 审批通过：APPROVING → APPROVED。 */
    @Transactional
    public void approve(long tenantId, long orderId, long instanceId, long approverId) {
        approvalPort.approve(tenantId, instanceId, approverId);
        ProcurementRepository.OrderHeader order = requireOrder(tenantId, orderId);
        transition(order, DocumentEvent.APPROVE, order.version(), approverId);
    }

    // ------------------------------------------------------------- 收货

    /**
     * 收货并入库。一次收货 = 一张收货单 + 若干库存入库过账。
     *
     * <p>顺序：校验不超收 → 建收货单 → 库存过账 → 累加已收量 → 推进订单状态 → 登记单据关系。
     * 全程同一事务：任何一步失败，收货单、库存、已收量一起回滚，不会出现
     * "收货单建了但库存没加"这类需要人工对账的中间态。
     */
    @Transactional
    public long receive(long tenantId, long orderId, List<ReceiptLine> lines, long operatorId) {
        if (lines == null || lines.isEmpty()) {
            throw new DomainException(ProcurementErrorCode.NOTHING_TO_RECEIVE, Map.of());
        }
        ProcurementRepository.OrderHeader order = requireOrder(tenantId, orderId);

        String receiptNo = numberGenerator.next(tenantId, "IN", LocalDate.now());
        long receiptId = repository.insertReceipt(tenantId, order.companyId(), receiptNo,
                orderId, order.warehouseId(), order.orgPath(), operatorId);

        BigDecimal postedAmount = BigDecimal.ZERO;
        for (ReceiptLine l : lines) {
            ProcurementRepository.OrderLine ol = repository.findOrderLine(tenantId, l.orderLineId());
            if (ol == null || ol.orderId() != orderId) {
                throw new DomainException(ProcurementErrorCode.LINE_NOT_FOUND,
                        Map.of("orderLineId", l.orderLineId()));
            }
            if (ol.closed()) {
                throw new DomainException(ProcurementErrorCode.LINE_CLOSED,
                        Map.of("orderLineId", l.orderLineId()));
            }

            // 不允许超收（用户决策）。上界条件与 CHECK 约束双保险；
            // 这里的 false 分支负责把它翻译成可读的业务错误。
            if (!repository.increaseReceivedIfWithinOrdered(tenantId, l.orderLineId(), l.quantity())) {
                throw new DomainException(ProcurementErrorCode.OVER_RECEIPT,
                        Map.of("orderLineId", l.orderLineId(),
                               "ordered", ol.orderedQty(),
                               "alreadyReceived", ol.receivedQty(),
                               "attempted", l.quantity()));
            }

            postedAmount = postedAmount.add(ol.receivedQty().add(l.quantity()).multiply(ol.unitPrice()).setScale(4, RoundingMode.HALF_UP)
                    .subtract(ol.receivedQty().multiply(ol.unitPrice()).setScale(4, RoundingMode.HALF_UP)));

            long receiptLineId = repository.insertReceiptLine(tenantId, receiptId,
                    l.orderLineId(), ol.skuId(), l.batchNo(), l.quantity());

            // 库存入库。source 三元组以**收货单行**为准：
            // 重复提交同一张收货单会撞 INV-04 唯一索引而不产生二次入库。
            InventoryBucket bucket = InventoryBucket.ofBatch(tenantId, order.companyId(),
                    order.warehouseId(), ol.skuId(), l.batchNo());
            posting.post(new PostingRequest(bucket, PostingDirection.IN, l.quantity(),
                    "PURCHASE_IN", DOC_TYPE_RECEIPT, String.valueOf(receiptId),
                    String.valueOf(receiptLineId), operatorId,
                    l.quantity().multiply(ol.unitPrice()).setScale(6, RoundingMode.HALF_UP)));
        }

        advanceAfterReceipt(tenantId, orderId, operatorId);

        // 发布收货事件。它有两个消费方：
        //   · erp-document 据此构建单据关系图（P4 出口条件 ⑤ 双向可追溯）
        //   · erp-finance 将在 P6 据此生成应付
        // 走 Outbox 而不是直接调用：采购不得依赖 erp-document / erp-finance（依赖矩阵）。
        outbox.record(tenantId, "PurchaseReceipt", String.valueOf(receiptId), PostedDocument.PURCHASE,
                new PostedDocument(DOC_TYPE_ORDER, String.valueOf(orderId), order.orderNo(),
                        DOC_TYPE_RECEIPT, String.valueOf(receiptId), receiptNo,
                        order.companyId(), order.supplierId(), postedAmount, currencies.requireBase(tenantId),
                        order.orgPath(), operatorId));
        return receiptId;
    }

    /** 收货后推进订单状态：全部收齐 → FINISHED，否则 → PROCESSING / PARTIAL_FINISHED。 */
    private void advanceAfterReceipt(long tenantId, long orderId, long operatorId) {
        ProcurementRepository.OrderHeader order = requireOrder(tenantId, orderId);
        List<ProcurementRepository.OrderLine> lines = repository.findOrderLines(tenantId, orderId);

        boolean allSettled = lines.stream()
                .allMatch(l -> l.closed() || l.outstanding().signum() == 0);

        DocumentState current = DocumentState.of(order.state());
        long v = order.version();

        // APPROVED 收到第一批货时先进入 PROCESSING
        if (current == DocumentState.APPROVED) {
            v = transition(order, DocumentEvent.PROCESS, v, operatorId);
            current = DocumentState.PROCESSING;
            order = requireOrder(tenantId, orderId);
        }
        if (allSettled) {
            transition(order, DocumentEvent.FINISH, v, operatorId);
        } else if (current == DocumentState.PROCESSING || current == DocumentState.PARTIAL_FINISHED) {
            transition(order, DocumentEvent.PARTIAL_FINISH, v, operatorId);
        }
    }

    // --------------------------------------------------------- 关闭 / 取消

    /** 少收后关闭剩余：已收部分保留，剩余不再收。 */
    @Transactional
    public void closeRemaining(long tenantId, long orderId, long operatorId) {
        ProcurementRepository.OrderHeader order = requireOrder(tenantId, orderId);
        repository.findOrderLines(tenantId, orderId).stream()
                .filter(l -> !l.closed() && l.outstanding().signum() > 0)
                .forEach(l -> repository.closeOrderLine(tenantId, l.id()));
        transition(order, DocumentEvent.CLOSE, order.version(), operatorId);
    }

    /**
     * 取消订单。
     *
     * <p><b>已发生收货则拒绝</b>：收货是既成事实，取消无法把货退回供应商。
     * 需要终止时应当关闭剩余，保留已收部分。状态机本身也不允许
     * PROCESSING 之后 CANCEL，这里的显式检查是为了给出更有指向性的错误。
     */
    @Transactional
    public void cancel(long tenantId, long orderId, long operatorId) {
        if (repository.hasAnyReceipt(tenantId, orderId)) {
            throw new DomainException(ProcurementErrorCode.CANCEL_AFTER_RECEIPT,
                    Map.of("orderId", orderId, "suggestion", "closeRemaining"));
        }
        ProcurementRepository.OrderHeader order = requireOrder(tenantId, orderId);
        transition(order, DocumentEvent.CANCEL, order.version(), operatorId);
    }

    // ------------------------------------------------------------- 辅助

    private long transition(ProcurementRepository.OrderHeader order, DocumentEvent event,
                            long fromVersion, long operatorId) {
        DocumentState next = documentState.transition(machine, order,
                DocumentState.of(order.state()), event,
                order.tenantId(), DOC_TYPE_ORDER, String.valueOf(order.id()), order.orderNo(),
                fromVersion, operatorId, MDC.get("traceId"));
        repository.updateOrderState(order.tenantId(), order.id(), next.code(), fromVersion + 1);
        return fromVersion + 1;
    }

    private ProcurementRepository.OrderHeader requireOrder(long tenantId, long orderId) {
        ProcurementRepository.OrderHeader o = repository.lockOrder(tenantId, orderId);
        if (o == null) {
            throw new DomainException(ProcurementErrorCode.ORDER_NOT_FOUND, Map.of("orderId", orderId));
        }
        return o;
    }

    /** 快照以 JSON 存列；这里手写以避免为一个两字段对象引入序列化依赖。 */
    private static String toJson(MasterDataRef ref) {
        if (ref == null) return null;
        return "{\"id\":%d,\"code\":\"%s\",\"name\":\"%s\",\"unitName\":%s}".formatted(
                ref.id(), escape(ref.code()), escape(ref.name()),
                ref.unitName() == null ? "null" : "\"" + escape(ref.unitName()) + "\"");
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public List<ProcurementRepository.OrderLine> lines(long tenantId, long orderId) {
        return new ArrayList<>(repository.findOrderLines(tenantId, orderId));
    }

    public ProcurementRepository.OrderHeader order(long tenantId, long orderId) {
        return repository.findOrder(tenantId, orderId);
    }

    public record NewLine(long skuId, MasterDataRef skuRef, BigDecimal quantity, BigDecimal unitPrice) { }

    public record ReceiptLine(long orderLineId, String batchNo, BigDecimal quantity) { }
}
