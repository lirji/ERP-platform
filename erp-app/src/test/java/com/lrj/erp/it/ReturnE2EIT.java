package com.lrj.erp.it;
import com.lrj.erp.procurement.application.*;
import com.lrj.erp.sales.application.*;
import com.lrj.erp.finance.application.*;
import com.lrj.erp.finance.domain.*;
import com.lrj.erp.inventory.application.StockPostingService;
import com.lrj.erp.inventory.domain.*;
import com.lrj.erp.kernel.error.DomainException;
import com.lrj.erp.kernel.outbox.OutboxDispatcher;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** 正向单据到退货、红字和退款的完整真实数据库链路。 */
class ReturnE2EIT extends AbstractPostgresIT {
    static final long T=273,C=1,W=1,S=1,P=2,U=1;
    @Autowired PurchaseOrderService purchases;
    @Autowired SalesOrderService sales;
    @Autowired PurchaseReturnService purchaseReturns;
    @Autowired SalesReturnService salesReturns;
    @Autowired SettlementService finance;
    @Autowired CreditAdjustmentService credits;
    @Autowired StockPostingService posting;
    @Autowired OutboxDispatcher dispatcher;
    @Autowired JdbcTemplate jdbc;
    @BeforeEach void setup() {
        for(String table:List.of("fin_refund_record","fin_credit_adjustment","fin_settlement_record","fin_receipt","fin_payment",
                "fin_account_receivable","fin_account_payable","pur_return","sal_return","pur_receipt_line","pur_receipt",
                "pur_order_line","pur_order","sal_shipment_line","sal_shipment","sal_order_line","sal_order","sal_credit_account",
                "inv_reservation","inv_transaction","inv_balance","doc_relation","erp_outbox_message","erp_state_transition","apr_instance","num_sequence","num_rule"))
            jdbc.update("DELETE FROM "+table+" WHERE tenant_id=?",T);
        jdbc.update("INSERT INTO md_currency (tenant_id,code,name,is_base,enabled) VALUES (?,'CNY','人民币',TRUE,TRUE) ON CONFLICT DO NOTHING",T);
        for(String rule:List.of("PO","IN","SO","OUT","AR","AP","RCV","PAY","CREDIT","PR","SR"))
            jdbc.update("INSERT INTO num_rule (tenant_id,business_type,prefix,seq_width) VALUES (?,?,?,6)",T,rule,rule);
    }
    @AfterEach void cleanOutbox() { jdbc.update("DELETE FROM erp_outbox_message WHERE tenant_id=?",T); }
    BigDecimal bd(String n) { return new BigDecimal(n); }
    void amount(String n,BigDecimal actual) { assertNotNull(actual);assertEquals(0,bd(n).compareTo(actual)); }
    void drain() { for(int i=0;i<5;i++) dispatcher.dispatchBatch(); }
    record Source(long id,long line) {}
    Source purchase() {
        long order=purchases.createOrder(T,C,P,W,null,List.of(new PurchaseOrderService.NewLine(S,null,bd("10"),bd("10"))),"/1/",U);
        purchases.approve(T,order,purchases.submitForApproval(T,order,U),2);
        long orderLine=purchases.lines(T,order).getFirst().id();
        long receipt=purchases.receive(T,order,List.of(new PurchaseOrderService.ReceiptLine(orderLine,"B1",bd("10"))),U);
        long line=jdbc.queryForObject("SELECT id FROM pur_receipt_line WHERE tenant_id=? AND receipt_id=?",Long.class,T,receipt);
        return new Source(receipt,line);
    }
    Source sale() {
        posting.post(new PostingRequest(InventoryBucket.ofBatch(T,C,W,S,"B1"),PostingDirection.IN,bd("3"),"SEED","SEED","1","1",U,bd("21")));
        long order=sales.createOrder(T,C,P,W,null,bd("1000"),null,List.of(new SalesOrderService.NewLine(S,null,"B1",bd("3"),bd("0.333333"))),"/1/",U);
        sales.approve(T,order,sales.submitForApproval(T,order,U),2);
        sales.reserveStock(T,order);
        long shipment=sales.ship(T,order,List.of(new SalesOrderService.ShipLine(sales.lines(T,order).getFirst().id(),bd("3"))),U);
        long line=jdbc.queryForObject("SELECT id FROM sal_shipment_line WHERE tenant_id=? AND shipment_id=?",Long.class,T,shipment);
        return new Source(shipment,line);
    }
    void approvePurchase(long id) { purchaseReturns.approve(T,id,purchaseReturns.submit(T,id,U),2); }
    void approveSales(long id) { salesReturns.approve(T,id,salesReturns.submit(T,id,U),2); }
    long credit(String type,long returnId) { return jdbc.queryForObject("SELECT id FROM fin_credit_adjustment WHERE tenant_id=? AND return_type=? AND return_id=?",Long.class,T,type,""+returnId); }

    @Test void paidPurchaseReturnCreatesRefundReceivableWithoutChangingPayment() {
        Source s=purchase();drain();
        long bill=finance.source(T,BillType.AP,"PURCHASE_RECEIPT",""+s.id()).id();
        long cash=finance.recordCash(T,BillType.AP,bill,bd("100"),"CNY","PAY",U);
        finance.apply(T,BillType.AP,bill,cash,bd("100"),U);
        long id=purchaseReturns.create(T,s.line(),bd("4"),"B1","RET","损坏退货",U);
        assertThrows(DomainException.class,()->purchaseReturns.execute(T,id,U));
        approvePurchase(id);purchaseReturns.execute(T,id,U);purchaseReturns.execute(T,id,U);drain();
        amount("6",posting.balance(InventoryBucket.ofBatch(T,C,W,S,"B1")).onHand());
        amount("60",posting.cost(InventoryBucket.ofBatch(T,C,W,S,"B1")).inventoryValue());
        long c=credit("PURCHASE_RETURN",id);amount("40",credits.get(T,c).refundDue());
        credits.recordRefund(T,c,bd("40"),"CNY","SUPPLIER_REFUND",U);
        amount("100",finance.cash(T,BillType.AP,cash).amount());
        assertThrows(DomainException.class,()->purchaseReturns.create(T,s.line(),bd("7"),"B1","OVER","超量",U));
        assertThrows(DomainException.class,()->purchaseReturns.cancel(T,id,U));
    }
    @Test void salesReturnRequiresBatchAndRestoresHistoricalCostAndRounding() {
        Source s=sale();drain();
        assertThrows(DomainException.class,()->salesReturns.create(T,s.line(),bd("1"),null,"BAD","退货",U));
        long bill=finance.source(T,BillType.AR,"SALES_SHIPMENT",""+s.id()).id();
        long cash=finance.recordCash(T,BillType.AR,bill,bd("1"),"CNY","RCV",U);
        finance.apply(T,BillType.AR,bill,cash,bd("1"),U);
        for(int i=0;i<3;i++) {
            long id=salesReturns.create(T,s.line(),bd("1"),"RETURNED","R"+i,"客户退货",U);
            assertEquals(id,salesReturns.create(T,s.line(),bd("1"),"RETURNED","R"+i,"客户退货",U));
            approveSales(id);salesReturns.execute(T,id,U);
        }
        drain();
        amount("3",posting.balance(InventoryBucket.ofBatch(T,C,W,S,"RETURNED")).onHand());
        amount("21",posting.cost(InventoryBucket.ofBatch(T,C,W,S,"RETURNED")).inventoryValue());
        amount("0",posting.balance(InventoryBucket.ofBatch(T,C,W,S,"B1")).onHand());
        amount("1",finance.bill(T,BillType.AR,bill).creditedAmount());
        amount("1",jdbc.queryForObject("SELECT sum(refund_due) FROM fin_credit_adjustment WHERE tenant_id=?",BigDecimal.class,T));
        assertEquals(3,jdbc.queryForObject("SELECT count(*) FROM doc_relation WHERE tenant_id=? AND child_type='SALES_RETURN'",Integer.class,T));
    }
    @Test void insufficientPurchaseStockRollsBackReturnAmountAndEvent() {
        Source s=purchase();
        long id=purchaseReturns.create(T,s.line(),bd("10"),"B1","R","退货",U);approvePurchase(id);
        posting.post(new PostingRequest(InventoryBucket.ofBatch(T,C,W,S,"B1"),PostingDirection.OUT,bd("1"),"OTHER","OTHER","1","1",U));
        assertThrows(DomainException.class,()->purchaseReturns.execute(T,id,U));
        assertEquals("APPROVED",purchaseReturns.get(T,id).state());
        amount("0",jdbc.queryForObject("SELECT returned_qty FROM pur_receipt_line WHERE tenant_id=? AND id=?",BigDecimal.class,T,s.line()));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM erp_outbox_message WHERE tenant_id=? AND event_type='PurchaseReturnPosted.v1'",Integer.class,T));
    }
    @Test void concurrentApprovedDraftsCannotReturnMoreThanShipped() throws Exception {
        Source s=sale();
        long a=salesReturns.create(T,s.line(),bd("2"),"RETURNED","R1","客户退货",U);
        long b=salesReturns.create(T,s.line(),bd("2"),"RETURNED","R2","客户退货",U);
        approveSales(a);approveSales(b);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var fa=pool.submit(()->executeSales(a));var fb=pool.submit(()->executeSales(b));
            assertEquals(1,fa.get(30,TimeUnit.SECONDS)+fb.get(30,TimeUnit.SECONDS));
        }
        amount("2",posting.balance(InventoryBucket.ofBatch(T,C,W,S,"RETURNED")).onHand());
    }
    int executeSales(long id) { try { salesReturns.execute(T,id,U);return 1; } catch(DomainException e) { return 0; } }
    @Test void historicUnknownAmountAndWrongTenantRejected() {
        Source s=purchase();
        assertThrows(DomainException.class,()->purchaseReturns.create(T+1,s.line(),bd("1"),"B1","R","退货",U));
        jdbc.update("UPDATE pur_receipt_line SET posted_amount=NULL WHERE tenant_id=? AND id=?",T,s.line());
        assertThrows(DomainException.class,()->purchaseReturns.create(T,s.line(),bd("1"),"B1","R","退货",U));
    }
}
