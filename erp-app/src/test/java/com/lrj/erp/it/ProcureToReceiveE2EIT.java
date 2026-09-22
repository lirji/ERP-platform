package com.lrj.erp.it;

import com.lrj.erp.approval.service.ApprovalPort;
import com.lrj.erp.document.service.DocumentLineageService;
import com.lrj.erp.inventory.application.StockPostingService;
import com.lrj.erp.inventory.domain.InventoryBucket;
import com.lrj.erp.kernel.error.DomainException;
import com.lrj.erp.kernel.masterdata.MasterDataRef;
import com.lrj.erp.kernel.outbox.OutboxDispatcher;
import com.lrj.erp.procurement.application.PurchaseOrderService;
import com.lrj.erp.procurement.application.PurchaseOrderService.NewLine;
import com.lrj.erp.procurement.application.PurchaseOrderService.ReceiptLine;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P4 出口条件 ①②③④⑤⑥：采购到入库的完整链路。
 *
 * <p>业务决策（用户 2026-09-21）：<b>不允许超收</b>。
 * 因此出口条件 ② 由「超过配置比例被拒」调整为「任何超收一律被拒」，无配置开关。
 */
@DisplayName("采购到入库 E2E")
class ProcureToReceiveE2EIT extends AbstractPostgresIT {

    private static final long TENANT = 200L;
    private static final long COMPANY = 9001L;
    private static final long WAREHOUSE = 7001L;
    private static final long SKU = 5001L;
    private static final long SUPPLIER = 6001L;
    private static final long OPERATOR = 4501L;
    private static final String ORG = "/9001/";

    @Autowired private PurchaseOrderService purchase;
    @Autowired private StockPostingService posting;
    @Autowired private ApprovalPort approval;
    @Autowired private DocumentLineageService lineage;
    @Autowired private OutboxDispatcher dispatcher;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        for (String table : List.of("fin_settlement_record","fin_payment","fin_receipt","fin_account_payable","fin_account_receivable")) {
            jdbc.update("DELETE FROM " + table + " WHERE tenant_id=?", TENANT);
        }
        jdbc.update("INSERT INTO md_currency (tenant_id,code,name,is_base,enabled) VALUES (?, 'CNY','人民币',TRUE,TRUE) ON CONFLICT DO NOTHING", TENANT);
        jdbc.update("DELETE FROM doc_relation WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM erp_outbox_message WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM erp_state_transition WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM apr_instance WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM pur_receipt_line WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM pur_receipt WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM pur_order_line WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM pur_order WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM inv_transaction WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM inv_balance WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM num_sequence WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM num_rule WHERE tenant_id = ?", TENANT);
        jdbc.update("INSERT INTO num_rule (tenant_id, business_type, prefix, seq_width) "
                + "VALUES (?, 'PO', 'PO', 6), (?, 'IN', 'IN', 6)", TENANT, TENANT);
        for (String code : List.of("AR","AP","RCV","PAY")) {
            jdbc.update("INSERT INTO num_rule (tenant_id,business_type,prefix,seq_width) VALUES (?,?,?,6) ON CONFLICT DO NOTHING", TENANT,code,code);
        }
    }

    private long newApprovedOrder(String qty) {
        long orderId = purchase.createOrder(TENANT, COMPANY, SUPPLIER, WAREHOUSE,
                new MasterDataRef(SUPPLIER, "SUP-001", "示例供应商", null),
                List.of(new NewLine(SKU, new MasterDataRef(SKU, "SKU-001", "A4 复印纸", "个"),
                        new BigDecimal(qty), new BigDecimal("12.5"))),
                ORG, OPERATOR);
        long instanceId = purchase.submitForApproval(TENANT, orderId, OPERATOR);
        purchase.approve(TENANT, orderId, instanceId, 4502L);
        return orderId;
    }

    private InventoryBucket bucket() {
        return InventoryBucket.of(TENANT, COMPANY, WAREHOUSE, SKU);
    }

    // ------------------------------------------------------------- ①

    @Test
    @DisplayName("① 申请→审批→下单→分 3 次收货→全部入库，库存增加量 == 收货总量")
    void 全链路分批收货() {
        long orderId = newApprovedOrder("100");
        assertEquals("APPROVED", purchase.order(TENANT, orderId).state());
        assertEquals("APPROVED", approval.statusOf(TENANT, "PURCHASE_ORDER", String.valueOf(orderId)));

        long lineId = purchase.lines(TENANT, orderId).get(0).id();

        purchase.receive(TENANT, orderId, List.of(new ReceiptLine(lineId, "-", new BigDecimal("30"))), OPERATOR);
        assertEquals("PARTIAL_FINISHED", purchase.order(TENANT, orderId).state());

        purchase.receive(TENANT, orderId, List.of(new ReceiptLine(lineId, "-", new BigDecimal("45"))), OPERATOR);
        purchase.receive(TENANT, orderId, List.of(new ReceiptLine(lineId, "-", new BigDecimal("25"))), OPERATOR);

        // 收齐后订单完成
        assertEquals("FINISHED", purchase.order(TENANT, orderId).state());
        assertEquals(0, purchase.lines(TENANT, orderId).get(0).receivedQty()
                .compareTo(new BigDecimal("100")));

        // 库存增加量 == 收货总量
        assertEquals(0, posting.balance(bucket()).onHand().compareTo(new BigDecimal("100")),
                "库存增加量必须等于三次收货之和");
        // 并且 INV-01 仍然成立
        assertEquals(0, posting.balance(bucket()).onHand().compareTo(posting.ledgerSum(bucket())));
    }

    // ------------------------------------------------------------- ②

    @Test
    @DisplayName("② 不允许超收：任何超出订购量的收货都被拒（用户决策，无配置开关）")
    void 超收一律被拒() {
        long orderId = newApprovedOrder("100");
        long lineId = purchase.lines(TENANT, orderId).get(0).id();

        purchase.receive(TENANT, orderId, List.of(new ReceiptLine(lineId, "-", new BigDecimal("80"))), OPERATOR);

        DomainException ex = assertThrows(DomainException.class,
                () -> purchase.receive(TENANT, orderId,
                        List.of(new ReceiptLine(lineId, "-", new BigDecimal("21"))), OPERATOR));
        assertEquals("ERP-PUR-3003", ex.errorCode().code());

        // 被拒后整笔回滚：已收量、库存、收货单都不得变化
        assertEquals(0, purchase.lines(TENANT, orderId).get(0).receivedQty()
                .compareTo(new BigDecimal("80")), "超收被拒后已收量不得变化");
        assertEquals(0, posting.balance(bucket()).onHand().compareTo(new BigDecimal("80")),
                "超收被拒后库存不得变化");
        Integer receipts = jdbc.queryForObject(
                "SELECT count(*) FROM pur_receipt WHERE tenant_id = ? AND order_id = ?",
                Integer.class, TENANT, orderId);
        assertEquals(1, receipts, "超收被拒后不得留下半张收货单");
    }

    @Test
    @DisplayName("② 恰好收满允许，多一点都不行")
    void 恰好收满可以() {
        long orderId = newApprovedOrder("100");
        long lineId = purchase.lines(TENANT, orderId).get(0).id();
        assertDoesNotThrow(() -> purchase.receive(TENANT, orderId,
                List.of(new ReceiptLine(lineId, "-", new BigDecimal("100"))), OPERATOR));
        assertThrows(DomainException.class, () -> purchase.receive(TENANT, orderId,
                List.of(new ReceiptLine(lineId, "-", new BigDecimal("0.000001"))), OPERATOR));
    }

    // ------------------------------------------------------------- ③

    @Test
    @DisplayName("③ 少收后可关闭剩余，已收部分保留")
    void 少收后关闭剩余() {
        long orderId = newApprovedOrder("100");
        long lineId = purchase.lines(TENANT, orderId).get(0).id();
        purchase.receive(TENANT, orderId, List.of(new ReceiptLine(lineId, "-", new BigDecimal("60"))), OPERATOR);

        purchase.closeRemaining(TENANT, orderId, OPERATOR);

        assertEquals("CLOSED", purchase.order(TENANT, orderId).state());
        var line = purchase.lines(TENANT, orderId).get(0);
        assertTrue(line.closed());
        assertEquals(0, line.receivedQty().compareTo(new BigDecimal("60")), "已收部分必须保留");
        assertEquals(0, posting.balance(bucket()).onHand().compareTo(new BigDecimal("60")),
                "关闭剩余不得影响已入库的库存");

        // 关闭后不能再收
        DomainException ex = assertThrows(DomainException.class,
                () -> purchase.receive(TENANT, orderId,
                        List.of(new ReceiptLine(lineId, "-", new BigDecimal("10"))), OPERATOR));
        assertEquals("ERP-PUR-3004", ex.errorCode().code());
    }

    // ------------------------------------------------------------- ④

    @Test
    @DisplayName("④ 已收货的订单不能取消")
    void 已收货不得取消() {
        long orderId = newApprovedOrder("100");
        long lineId = purchase.lines(TENANT, orderId).get(0).id();
        purchase.receive(TENANT, orderId, List.of(new ReceiptLine(lineId, "-", new BigDecimal("10"))), OPERATOR);

        DomainException ex = assertThrows(DomainException.class,
                () -> purchase.cancel(TENANT, orderId, OPERATOR));
        assertEquals("ERP-PUR-3005", ex.errorCode().code());
        assertEquals("closeRemaining", ex.details().get("suggestion"),
                "拒绝时应当指出正确做法，否则用户只会反复试");
    }

    @Test
    @DisplayName("④ 未收货的订单可以取消")
    void 未收货可取消() {
        long orderId = newApprovedOrder("100");
        assertDoesNotThrow(() -> purchase.cancel(TENANT, orderId, OPERATOR));
        assertEquals("CANCELLED", purchase.order(TENANT, orderId).state());
    }

    // ------------------------------------------------------------- ⑤

    @Test
    @DisplayName("⑤ 单据图双向可追溯：订单↔入库单 正查与反查都成立")
    void 单据图双向可追溯() {
        long orderId = newApprovedOrder("100");
        long lineId = purchase.lines(TENANT, orderId).get(0).id();
        long receiptId = purchase.receive(TENANT, orderId,
                List.of(new ReceiptLine(lineId, "-", new BigDecimal("40"))), OPERATOR);

        // 关系图由事件驱动构建，先把 Outbox 投递出去
        assertTrue(dispatcher.dispatchBatch() > 0, "应当有待投递的收货事件");

        // 正查：从采购订单找到入库单
        List<Map<String, Object>> down = lineage.downstream(TENANT, "PURCHASE_ORDER", String.valueOf(orderId));
        assertEquals(1, down.size(), "从采购订单应能正查到入库单");
        assertEquals("PURCHASE_RECEIPT", down.get(0).get("childType"));
        assertEquals(String.valueOf(receiptId), down.get(0).get("childId"));

        // 反查：从入库单找回采购订单
        List<Map<String, Object>> up = lineage.upstream(TENANT, "PURCHASE_RECEIPT", String.valueOf(receiptId));
        assertEquals(1, up.size(), "从入库单应能反查到采购订单");
        assertEquals("PURCHASE_ORDER", up.get(0).get("parentType"));
        assertEquals(String.valueOf(orderId), up.get(0).get("parentId"));
        assertNotNull(up.get(0).get("parentNo"), "反查应带上游单号，否则还要再查一次才能展示");
    }

    // ------------------------------------------------------------- ⑥

    @Test
    @DisplayName("⑥ 重复提交同一张收货单不产生二次入库")
    void 重复收货不二次入库() {
        long orderId = newApprovedOrder("100");
        long lineId = purchase.lines(TENANT, orderId).get(0).id();
        long receiptId = purchase.receive(TENANT, orderId,
                List.of(new ReceiptLine(lineId, "-", new BigDecimal("30"))), OPERATOR);

        BigDecimal afterFirst = posting.balance(bucket()).onHand();
        assertEquals(0, afterFirst.compareTo(new BigDecimal("30")));

        // 模拟重复提交：用同一张收货单的同一行再过账一次
        Long receiptLineId = jdbc.queryForObject(
                "SELECT id FROM pur_receipt_line WHERE tenant_id = ? AND receipt_id = ?",
                Long.class, TENANT, receiptId);
        boolean applied = posting.post(new com.lrj.erp.inventory.domain.PostingRequest(
                bucket(), com.lrj.erp.inventory.domain.PostingDirection.IN, new BigDecimal("30"),
                "PURCHASE_IN", "PURCHASE_RECEIPT", String.valueOf(receiptId),
                String.valueOf(receiptLineId), OPERATOR));

        assertFalse(applied, "同一收货单行重复过账必须被幂等跳过");
        assertEquals(0, posting.balance(bucket()).onHand().compareTo(afterFirst),
                "重复提交收货单不得产生二次入库");
    }

    @Test
    @DisplayName("状态流转全程留痕，可按单据回放")
    void 状态流转留痕() {
        long orderId = newApprovedOrder("100");
        List<Map<String, Object>> history = jdbc.queryForList("""
                SELECT from_state, to_state, event FROM erp_state_transition
                WHERE tenant_id = ? AND business_type = 'PURCHASE_ORDER' AND business_id = ?
                ORDER BY id
                """, TENANT, String.valueOf(orderId));
        assertEquals(List.of("SUBMIT", "START_APPROVAL", "APPROVE"),
                history.stream().map(h -> (String) h.get("event")).toList());
    }
}
