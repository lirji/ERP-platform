package com.lrj.erp.sales.application;

import com.lrj.erp.approval.service.ApprovalPort;
import com.lrj.erp.inventory.application.ReservationService;
import com.lrj.erp.inventory.application.StockPostingService;
import com.lrj.erp.inventory.domain.*;
import com.lrj.erp.kernel.error.DomainException;
import com.lrj.erp.kernel.masterdata.MasterDataRef;
import com.lrj.erp.kernel.outbox.OutboxRecorder;
import com.lrj.erp.kernel.statemachine.*;
import com.lrj.erp.numbering.service.NumberGenerator;
import com.lrj.erp.sales.domain.SalesErrorCode;
import com.lrj.erp.sales.domain.SalesRepository;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import com.lrj.erp.kernel.events.PostedDocument;
import com.lrj.erp.masterdata.service.CurrencyService;
import java.time.LocalDate;
import java.time.ZoneId;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lrj.erp.kernel.error.SystemErrorCode;
import java.util.List;
import java.util.Map;

/**
 * 销售订单用例：下单 → 审批 → 预占 → 分批出库 → 发货 → 签收 / 取消。
 *
 * <p>与采购侧对称：编排层只负责把规则按正确顺序串起来，规则本身留在各自归属处——
 * 状态合法性在状态机、不超发在订单行上界与 CHECK 约束、
 * 库存可用量与预占上界在库存内核、信用上限在信用账户的条件更新。
 */
@Service
public class SalesOrderService {

    public static final String DOC_TYPE_ORDER = "SALES_ORDER";
    public static final String DOC_TYPE_SHIPMENT = "SALES_SHIPMENT";

    private final com.lrj.erp.kernel.finance.SettledCreditQuery settledCredit;
    private final ObjectMapper json;
    private final SalesRepository repository;
    private final NumberGenerator numberGenerator;
    private final ApprovalPort approvalPort;
    private final DocumentStateService documentState;
    private final StockPostingService posting;
    private final ReservationService reservations;
    /** 关系图通过事件构建：销售不得依赖 erp-document（依赖矩阵）。 */
    private final OutboxRecorder outbox;
    private final CurrencyService currencies;

    private final StateMachine<SalesRepository.OrderHeader> machine =
            StandardDocumentStateMachine.<SalesRepository.OrderHeader>builder().build();

    public SalesOrderService(SalesRepository repository, NumberGenerator numberGenerator,
                             ApprovalPort approvalPort, DocumentStateService documentState,
                             StockPostingService posting, ReservationService reservations,
                             OutboxRecorder outbox, ObjectMapper json, CurrencyService currencies, com.lrj.erp.kernel.finance.SettledCreditQuery settledCredit) {
        this.settledCredit = settledCredit;
        this.currencies = currencies;
        this.json = json;
        this.repository = repository;
        this.numberGenerator = numberGenerator;
        this.approvalPort = approvalPort;
        this.documentState = documentState;
        this.posting = posting;
        this.reservations = reservations;
        this.outbox = outbox;
    }

    // ------------------------------------------------------------- 下单

    /**
     * 新建销售订单并占用信用额度。
     *
     * @param creditLimit          客户授信上限，由调用方从主数据读入。
     *                             不在销售侧冗余一份——两处存同一个上限必然出现不一致。
     * @param creditApprovalId 已通过的信用审批实例；必须绑定本次订单内容且只能使用一次
     * @throws DomainException {@code ERP-SAL-3003} 信用额度不足
     */
    @Transactional(timeout = 30)
    public long createOrder(long tenantId, long companyId, long customerId, long warehouseId,
                            MasterDataRef customerRef, BigDecimal creditLimit,
                            Long creditApprovalId, List<NewLine> lines,
                            String orgPath, long operatorId) {

        validateLines(lines, creditLimit);
        // 与分批出库事件统一按行舍入，再汇总，避免财务核销金额超过信用占用本金。
        BigDecimal total = lines.stream()
                .map(l -> l.unitPrice().multiply(l.quantity()).setScale(4, RoundingMode.HALF_UP))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(4, java.math.RoundingMode.HALF_UP);

        repository.ensureCreditAccount(tenantId, customerId);

        if (creditApprovalId != null) {
            if (!approvalPort.matches(tenantId, creditApprovalId, "SALES_CREDIT",
                    creditSubject(companyId, customerId, warehouseId, creditLimit, lines, orgPath), "APPROVED")) {
                throw new DomainException(SystemErrorCode.MALFORMED_BODY, Map.of("reason", "信用审批未通过或与订单内容不匹配"));
            }
            // 已获审批放行：无条件占用。放行是业务决定，不是把上限调高——
            // 调高上限会影响之后所有订单，放行只影响这一单。
            repository.occupyCreditForced(tenantId, customerId, total);
        } else if (!repository.occupyCreditIfWithinLimit(tenantId, customerId, total, creditLimit.add(settledCredit.releasedCredit(tenantId, customerId)))) {
            throw new DomainException(SalesErrorCode.CREDIT_EXCEEDED,
                    Map.of("customerId", customerId, "creditLimit", creditLimit,
                           "used", repository.usedCredit(tenantId, customerId),
                           "orderAmount", total));
        }

        String orderNo = numberGenerator.next(tenantId, "SO", LocalDate.now(ZoneId.of("Asia/Shanghai")));
        long orderId = repository.insertOrder(tenantId, companyId, orderNo, customerId,
                toJson(customerRef), warehouseId, total, orgPath, operatorId);

        if (creditApprovalId != null) repository.bindCreditApproval(tenantId, orderId, creditApprovalId);
        int lineNo = 1;
        for (NewLine l : lines) {
            repository.insertOrderLine(tenantId, orderId, lineNo++, l.skuId(), toJson(l.skuRef()),
                    l.batchNo(), l.quantity(), l.unitPrice());
        }
        return orderId;
    }

    /** 提交与订单内容绑定的信用超限审批；不能凭调用方布尔值放行。 */
    @Transactional(timeout = 30)
    public long requestCreditApproval(long tenantId, long companyId, long customerId, long warehouseId,
                                      BigDecimal creditLimit, List<NewLine> lines, String orgPath, long operatorId) {
        validateLines(lines, creditLimit);
        String subject = creditSubject(companyId, customerId, warehouseId, creditLimit, lines, orgPath);
        return approvalPort.submit(tenantId, "SALES_CREDIT", subject, "CREDIT-" + customerId, operatorId);
    }

    /** 摘要覆盖客户、主体、仓库、金额来源和组织，避免审批后替换订单内容。 */
    private String creditSubject(long companyId, long customerId, long warehouseId, BigDecimal creditLimit,
                                 List<NewLine> lines, String orgPath) {
        try {
            byte[] content = json.writeValueAsBytes(List.of(companyId, customerId, warehouseId,
                    creditLimit.stripTrailingZeros().toPlainString(), lines, orgPath));
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(content));
        } catch (Exception e) {
            throw new IllegalStateException("无法生成信用审批内容摘要", e);
        }
    }

    /** 在写库前拒绝空订单、负金额和超精度数量，避免数据库舍入改变业务效果。 */
    private void validateLines(List<NewLine> lines, BigDecimal creditLimit) {
        if (lines == null || lines.isEmpty() || lines.size() > 200 || creditLimit == null || creditLimit.signum() < 0) {
            throw new DomainException(SystemErrorCode.MALFORMED_BODY, Map.of("reason", "订单行或信用上限非法"));
        }
        for (NewLine line : lines) {
            if (line == null || line.quantity() == null || line.quantity().signum() <= 0
                    || line.quantity().stripTrailingZeros().scale() > 6
                    || line.unitPrice() == null || line.unitPrice().signum() < 0
                    || line.unitPrice().stripTrailingZeros().scale() > 6 || line.batchNo() == null) {
                throw new DomainException(SystemErrorCode.MALFORMED_BODY, Map.of("reason", "数量、单价或批次非法"));
            }
        }
    }

    // ------------------------------------------------------------- 审批

    /** 提交审批与状态迁移在同一事务内，失败不留下半张待审单。 */
    @Transactional(timeout = 30)
    public long submitForApproval(long tenantId, long orderId, long operatorId) {
        SalesRepository.OrderHeader order = requireOrder(tenantId, orderId);
        long v = transition(order, DocumentEvent.SUBMIT, order.version(), operatorId);
        transition(requireOrder(tenantId, orderId), DocumentEvent.START_APPROVAL, v, operatorId);
        return approvalPort.submit(tenantId, DOC_TYPE_ORDER, String.valueOf(orderId),
                order.orderNo(), operatorId);
    }

    /** 先锁业务单，再校验审批实例归属，防止串单审批。 */
    @Transactional(timeout = 30)
    public void approve(long tenantId, long orderId, long instanceId, long approverId) {
        SalesRepository.OrderHeader order = requireOrder(tenantId, orderId);
        if (!approvalPort.matches(tenantId, instanceId, DOC_TYPE_ORDER, String.valueOf(orderId), "PENDING")) {
            throw new DomainException(SystemErrorCode.MALFORMED_BODY, Map.of("reason", "审批实例与订单不匹配"));
        }
        approvalPort.approve(tenantId, instanceId, approverId);
        transition(order, DocumentEvent.APPROVE, order.version(), approverId);
    }

    // ------------------------------------------------------------- 预占

    /**
     * 库存预占。可用量不足时抛异常，<b>订单保持在 APPROVED</b>（出口条件 ②）——
     * 预占失败不是订单失败，补货后可以重试；把订单打回或作废会让业务无法继续。
     */
    @Transactional(timeout = 30)
    public void reserveStock(long tenantId, long orderId) {
        SalesRepository.OrderHeader order = requireOrder(tenantId, orderId);
        if (!DocumentState.APPROVED.code().equals(order.state())) {
            throw new DomainException(SystemErrorCode.OPTIMISTIC_LOCK, Map.of("state", order.state()));
        }
        if (order.reserved()) {
            throw new DomainException(SalesErrorCode.ALREADY_RESERVED, Map.of("orderId", orderId));
        }
        for (SalesRepository.OrderLine line : repository.findOrderLines(tenantId, orderId)) {
            reservations.reserve(bucket(order, line), DOC_TYPE_ORDER,
                    String.valueOf(orderId), String.valueOf(line.id()), line.orderedQty());
        }
        repository.markReserved(tenantId, orderId, true);
    }

    // ------------------------------------------------------------- 出库

    /**
     * 拣货出库并发货。一次出库 = 一张发货单 + 若干库存出库过账 + 预占消耗。
     *
     * <p>顺序：校验已预占 → 建发货单 → 累加已出库量 → 消耗预占 → 库存出库过账。
     * 全程同一事务：任何一步失败一起回滚，不会出现"发货单建了但库存没扣"的中间态。
     */
    @Transactional(timeout = 30)
    public long ship(long tenantId, long orderId, List<ShipLine> lines, long operatorId) {
        if (lines == null || lines.isEmpty() || lines.size() > 200) {
            throw new DomainException(SalesErrorCode.NOTHING_TO_SHIP, Map.of());
        }
        SalesRepository.OrderHeader order = requireOrder(tenantId, orderId);
        if (!List.of("APPROVED", "PARTIAL_FINISHED").contains(order.state())) {
            throw new DomainException(SystemErrorCode.OPTIMISTIC_LOCK, Map.of("state", order.state()));
        }
        if (!order.reserved()) {
            throw new DomainException(SalesErrorCode.NOT_RESERVED, Map.of("orderId", orderId));
        }

        String shipmentNo = numberGenerator.next(tenantId, "OUT", LocalDate.now(ZoneId.of("Asia/Shanghai")));
        long shipmentId = repository.insertShipment(tenantId, order.companyId(), shipmentNo,
                orderId, order.warehouseId(), order.orgPath(), operatorId);

        String currency = currencies.requireBase(tenantId);
        BigDecimal postedAmount = BigDecimal.ZERO;
        for (ShipLine l : lines) {
            if (l == null || l.quantity() == null || l.quantity().signum() <= 0
                    || l.quantity().stripTrailingZeros().scale() > 6) {
                throw new DomainException(SystemErrorCode.MALFORMED_BODY, Map.of("reason", "出库数量必须为正且最多六位小数"));
            }
            SalesRepository.OrderLine ol = repository.findOrderLine(tenantId, l.orderLineId());
            if (ol == null || ol.orderId() != orderId) {
                throw new DomainException(SalesErrorCode.LINE_NOT_FOUND,
                        Map.of("orderLineId", l.orderLineId()));
            }
            if (!repository.increaseShippedIfWithinOrdered(tenantId, l.orderLineId(), l.quantity())) {
                throw new DomainException(SalesErrorCode.OVER_SHIP,
                        Map.of("orderLineId", l.orderLineId(), "ordered", ol.orderedQty(),
                               "alreadyShipped", ol.shippedQty(), "attempted", l.quantity()));
            }

            BigDecimal lineAmount = ol.shippedQty().add(l.quantity()).multiply(ol.unitPrice()).setScale(4, RoundingMode.HALF_UP)
                    .subtract(ol.shippedQty().multiply(ol.unitPrice()).setScale(4, RoundingMode.HALF_UP));
            postedAmount = postedAmount.add(lineAmount);

            InventoryBucket bucket = bucket(order, ol);
            // 先消耗预占再扣在库：两者都在同一事务，顺序不影响结果，
            // 但先消耗预占能让"预占不足"这类错误更早暴露
            reservations.consume(bucket, DOC_TYPE_ORDER, String.valueOf(orderId),
                    String.valueOf(ol.id()), l.quantity());

            long shipmentLineId = repository.insertShipmentLine(tenantId, shipmentId,
                    ol.id(), ol.skuId(), ol.batchNo(), l.quantity());

            repository.recordLineAmount(tenantId,shipmentLineId,lineAmount,currency);
            posting.post(new PostingRequest(bucket, PostingDirection.OUT, l.quantity(),
                    "SALES_OUT", DOC_TYPE_SHIPMENT, String.valueOf(shipmentId),
                    String.valueOf(shipmentLineId), operatorId));
        }

        advanceAfterShipment(tenantId, orderId, operatorId);

        outbox.record(tenantId, "Shipment", String.valueOf(shipmentId), PostedDocument.SALES,
                new PostedDocument(DOC_TYPE_ORDER, String.valueOf(orderId), order.orderNo(),
                        DOC_TYPE_SHIPMENT, String.valueOf(shipmentId), shipmentNo,
                        order.companyId(), order.customerId(), postedAmount, currency,
                        order.orgPath(), operatorId));
        return shipmentId;
    }

    /** 发运。出库与发运是两件事：出库后可能暂存待发。 */
    @Transactional(timeout = 30)
    public void deliver(long tenantId, long shipmentId) {
        repository.markDelivered(tenantId, shipmentId);
    }

    /** 客户签收。只允许已发运的单据签收；应收按既有 ShipmentPosted 契约在出库时生成。 */
    @Transactional(timeout = 30)
    public void sign(long tenantId, long shipmentId) {
        repository.markSigned(tenantId, shipmentId);
    }

    private void advanceAfterShipment(long tenantId, long orderId, long operatorId) {
        SalesRepository.OrderHeader order = requireOrder(tenantId, orderId);
        boolean allShipped = repository.findOrderLines(tenantId, orderId).stream()
                .allMatch(l -> l.outstanding().signum() == 0);

        DocumentState current = DocumentState.of(order.state());
        long v = order.version();
        if (current == DocumentState.APPROVED) {
            v = transition(order, DocumentEvent.PROCESS, v, operatorId);
            current = DocumentState.PROCESSING;
            order = requireOrder(tenantId, orderId);
        }
        if (allShipped) {
            transition(order, DocumentEvent.FINISH, v, operatorId);
        } else if (current == DocumentState.PROCESSING || current == DocumentState.PARTIAL_FINISHED) {
            transition(order, DocumentEvent.PARTIAL_FINISH, v, operatorId);
        }
    }

    // ------------------------------------------------------------- 取消

    /**
     * 取消订单：释放全部未消耗预占，并释放信用占用。
     *
     * <p>已发生出库则拒绝——出库是既成事实，取消无法把货追回。
     */
    @Transactional(timeout = 30)
    public void cancel(long tenantId, long orderId, long operatorId) {
        SalesRepository.OrderHeader order = requireOrder(tenantId, orderId);
        if (repository.hasAnyShipment(tenantId, orderId)) {
            throw new DomainException(SalesErrorCode.CANCEL_AFTER_SHIP,
                    Map.of("orderId", orderId, "suggestion", "closeRemaining"));
        }
        if (order.reserved()) {
            for (SalesRepository.OrderLine line : repository.findOrderLines(tenantId, orderId)) {
                reservations.releaseRemaining(bucket(order, line), DOC_TYPE_ORDER,
                        String.valueOf(orderId), String.valueOf(line.id()));
            }
            repository.markReserved(tenantId, orderId, false);
        }
        // 信用占用随订单取消一并释放，否则客户额度会被已取消的订单长期占着
        repository.releaseCredit(tenantId, order.customerId(), order.totalAmount());
        transition(order, DocumentEvent.CANCEL, order.version(), operatorId);
    }

    // ------------------------------------------------------------- 辅助

    private InventoryBucket bucket(SalesRepository.OrderHeader order, SalesRepository.OrderLine line) {
        return InventoryBucket.ofBatch(order.tenantId(), order.companyId(),
                order.warehouseId(), line.skuId(), line.batchNo());
    }

    private long transition(SalesRepository.OrderHeader order, DocumentEvent event,
                            long fromVersion, long operatorId) {
        DocumentState next = documentState.transition(machine, order,
                DocumentState.of(order.state()), event,
                order.tenantId(), DOC_TYPE_ORDER, String.valueOf(order.id()), order.orderNo(),
                fromVersion, operatorId, MDC.get("traceId"));
        repository.updateOrderState(order.tenantId(), order.id(), next.code(), fromVersion + 1);
        return fromVersion + 1;
    }

    private SalesRepository.OrderHeader requireOrder(long tenantId, long orderId) {
        SalesRepository.OrderHeader o = repository.lockOrder(tenantId, orderId);
        if (o == null) {
            throw new DomainException(SalesErrorCode.ORDER_NOT_FOUND, Map.of("orderId", orderId));
        }
        return o;
    }

    private String toJson(MasterDataRef ref) {
        if (ref == null) return null;
        try {
            return json.writeValueAsString(ref);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException("主数据快照无法序列化", e);
        }
    }

    /** 查询订单，不获取写锁。 */
    public SalesRepository.OrderHeader order(long tenantId, long orderId) {
        return repository.findOrder(tenantId, orderId);
    }

    /** 按稳定行号读取订单明细。 */
    public List<SalesRepository.OrderLine> lines(long tenantId, long orderId) {
        return repository.findOrderLines(tenantId, orderId);
    }

    /** 查询客户当前已占用信用。 */
    public BigDecimal usedCredit(long tenantId, long customerId) {
        return repository.usedCredit(tenantId, customerId).subtract(settledCredit.releasedCredit(tenantId, customerId));
    }

    public record NewLine(long skuId, MasterDataRef skuRef, String batchNo,
                          BigDecimal quantity, BigDecimal unitPrice) { }

    public record ShipLine(long orderLineId, BigDecimal quantity) { }
}
