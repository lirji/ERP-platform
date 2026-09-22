package com.lrj.erp.it;
import com.lrj.erp.reporting.application.ReportSnapshotService;
import com.lrj.erp.kernel.reporting.ReportSource.Kind;
import com.lrj.erp.inventory.application.StockPostingService;
import com.lrj.erp.inventory.domain.*;
import com.lrj.erp.procurement.application.PurchaseOrderService;
import com.lrj.erp.sales.application.SalesOrderService;
import com.lrj.erp.finance.application.SettlementService;
import com.lrj.erp.finance.domain.BillType;
import com.lrj.erp.kernel.outbox.OutboxDispatcher;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** 有界重建、持久检查点和原子发布，不以查询业务表冒充读模型。 */
class ReportSnapshotIT extends AbstractPostgresIT {
    static final long T=280;
    @Autowired ReportSnapshotService reports;
    @Autowired StockPostingService posting;
    @Autowired PurchaseOrderService purchase;
    @Autowired SalesOrderService sales;
    @Autowired SettlementService finance;
    @Autowired OutboxDispatcher outbox;
    @Autowired JdbcTemplate jdbc;
    @BeforeEach void setup() {
        for(String table:List.of("rpt_snapshot_current","rpt_snapshot_fact","rpt_snapshot_job","fin_settlement_record","fin_receipt","fin_payment",
                "fin_account_receivable","fin_account_payable","pur_receipt_line","pur_receipt","pur_order_line","pur_order",
                "sal_shipment_line","sal_shipment","sal_order_line","sal_order","sal_credit_account","inv_reservation","inv_transaction","inv_balance",
                "erp_outbox_message","doc_relation","erp_state_transition","apr_instance","num_sequence","num_rule"))
            jdbc.update("DELETE FROM "+table+" WHERE tenant_id=?",T);
        jdbc.update("INSERT INTO md_currency (tenant_id,code,name,is_base,enabled) VALUES (?,'CNY','人民币',TRUE,TRUE) ON CONFLICT DO NOTHING",T);
        for(String rule:List.of("PO","IN","SO","OUT","AR","AP","RCV","PAY"))
            jdbc.update("INSERT INTO num_rule (tenant_id,business_type,prefix,seq_width) VALUES (?,?,?,6)",T,rule,rule);
    }
    @AfterEach void cleanOutbox() { jdbc.update("DELETE FROM erp_outbox_message WHERE tenant_id=?",T); }
    BigDecimal bd(String n) { return new BigDecimal(n); }
    void amount(String n,BigDecimal actual) { assertEquals(0,bd(n).compareTo(actual)); }
    InventoryBucket bucket(long sku) { return InventoryBucket.of(T,1,1,sku); }
    void stock(long sku,String id) { posting.post(new PostingRequest(bucket(sku),PostingDirection.IN,bd("10"),"SEED","SEED",id,"1",1,bd("20"))); }
    long rebuild(Kind kind) {
        long id=reports.start(T,kind);
        for(int i=0;i<100;i++) if(!reports.step(T,id,2).state().equals("BUILDING")) return id;
        throw new AssertionError("重建未结束");
    }
    @Test void inventoryMatchesLedgerAndRebuildIsReproducible() {
        stock(1,"A");stock(2,"B");stock(3,"C");
        long first=rebuild(Kind.INVENTORY);
        var original=reports.currentPage(T,Kind.INVENTORY,0,100).rows();
        assertEquals(3,original.size());
        for(var row:original) amount(""+posting.ledgerSum(bucket(row.skuId())),row.quantity());
        amount("30",reports.totals(T,Kind.INVENTORY).values().quantity());
        amount("60",reports.totals(T,Kind.INVENTORY).values().amount());
        long second=rebuild(Kind.INVENTORY);
        assertNotEquals(first,second);
        assertEquals(original,reports.currentPage(T,Kind.INVENTORY,0,100).rows());
        assertEquals(original,reports.snapshotPage(T,first,0,100).rows());
    }
    @Test void checkpointsResumeAndConcurrentStepsDoNotDuplicate() throws Exception {
        for(int i=1;i<=7;i++) stock(i,"S"+i);
        long id=reports.start(T,Kind.INVENTORY);
        assertEquals(id,reports.start(T,Kind.INVENTORY));
        long cursor=reports.step(T,id,2).cursorId();
        assertTrue(cursor>0);assertEquals(cursor,reports.job(T,id).cursorId());
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->reports.step(T,id,2));var b=pool.submit(()->reports.step(T,id,2));
            a.get(30,TimeUnit.SECONDS);b.get(30,TimeUnit.SECONDS);
        }
        while(reports.step(T,id,2).state().equals("BUILDING")) { }
        assertEquals(7,reports.currentPage(T,Kind.INVENTORY,0,100).rows().size());
        assertThrows(IllegalArgumentException.class,()->reports.snapshotPage(T+1,id,0,10));
        assertThrows(IllegalArgumentException.class,()->reports.currentPage(T,Kind.INVENTORY,0,1001));
    }
    @Test void changedSourceNeverReplacesPreviouslyPublishedSnapshot() {
        stock(1,"A");long previous=rebuild(Kind.INVENTORY);
        long candidate=reports.start(T,Kind.INVENTORY);reports.step(T,candidate,2);
        stock(1,"B");
        assertEquals("STALE",reports.step(T,candidate,2).state());
        assertEquals(previous,reports.currentPage(T,Kind.INVENTORY,0,10).snapshotId());
        assertThrows(IllegalArgumentException.class,()->reports.snapshotPage(T,candidate,0,10));
        rebuild(Kind.INVENTORY);amount("20",reports.totals(T,Kind.INVENTORY).values().quantity());
    }
    @Test void cancelAndBoundedCleanupKeepCurrentSnapshot() {
        stock(1,"A");stock(2,"B");
        long current=rebuild(Kind.INVENTORY);
        long abandoned=reports.start(T,Kind.INVENTORY);reports.step(T,abandoned,1);
        reports.cancel(T,abandoned);
        assertEquals("STALE",reports.job(T,abandoned).state());
        assertFalse(reports.purgeStep(T,abandoned,1));
        assertTrue(reports.purgeStep(T,abandoned,1));
        assertTrue(reports.purgeStep(T,abandoned,1));
        assertThrows(IllegalArgumentException.class,()->reports.purgeStep(T,current,1));
        assertEquals(2,reports.currentPage(T,Kind.INVENTORY,0,10).rows().size());
    }
    @Test void fiveKindsProjectActualPurchaseSaleAndFinance() {
        long po=purchase.createOrder(T,1,2,1,null,List.of(new PurchaseOrderService.NewLine(1,null,bd("10"),bd("2"))),"/1/",1);
        purchase.approve(T,po,purchase.submitForApproval(T,po,1),2);
        long receipt=purchase.receive(T,po,List.of(new PurchaseOrderService.ReceiptLine(purchase.lines(T,po).getFirst().id(),"-",bd("10"))),1);
        long so=sales.createOrder(T,1,2,1,null,bd("1000"),null,List.of(new SalesOrderService.NewLine(1,null,"-",bd("3"),bd("5"))),"/1/",1);
        sales.approve(T,so,sales.submitForApproval(T,so,1),2);sales.reserveStock(T,so);
        long shipment=sales.ship(T,so,List.of(new SalesOrderService.ShipLine(sales.lines(T,so).getFirst().id(),bd("3"))),1);
        for(int i=0;i<5;i++) outbox.dispatchBatch();
        long ar=finance.source(T,BillType.AR,"SALES_SHIPMENT",""+shipment).id();
        long cash=finance.recordCash(T,BillType.AR,ar,bd("5"),"CNY","RCV",1);finance.apply(T,BillType.AR,ar,cash,bd("5"),1);
        for(Kind kind:Kind.values()) rebuild(kind);
        amount("7",reports.totals(T,Kind.INVENTORY).values().quantity());
        amount("20",reports.totals(T,Kind.PURCHASE).values().amount());
        amount("15",reports.totals(T,Kind.SALES).values().amount());
        amount("10",reports.totals(T,Kind.AR).values().unsettled());
        amount("20",reports.totals(T,Kind.AP).values().unsettled());
        amount("10",reports.aging(T,Kind.AR).buckets().getFirst().outstanding());
        assertEquals("0-30",reports.aging(T,Kind.AR).buckets().getFirst().bucket());
    }
}
