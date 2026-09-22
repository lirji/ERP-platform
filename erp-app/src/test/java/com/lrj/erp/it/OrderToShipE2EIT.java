package com.lrj.erp.it;

import com.lrj.erp.approval.service.ApprovalPort;
import com.lrj.erp.document.service.DocumentLineageService;
import com.lrj.erp.inventory.application.ReservationService;
import com.lrj.erp.inventory.application.StockPostingService;
import com.lrj.erp.inventory.domain.*;
import com.lrj.erp.kernel.error.DomainException;
import com.lrj.erp.kernel.masterdata.MasterDataRef;
import com.lrj.erp.kernel.outbox.OutboxDispatcher;
import com.lrj.erp.sales.application.SalesOrderService;
import com.lrj.erp.sales.application.SalesOrderService.NewLine;
import com.lrj.erp.sales.application.SalesOrderService.ShipLine;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** P5：在隔离 PostgreSQL 测试库验证库存、预占、信用及状态的事务不变量。 */
class OrderToShipE2EIT extends AbstractPostgresIT {
    private static final long T = 250, C = 9001, W = 7001, SKU = 5001, CUSTOMER = 6001, USER = 4501;
    @Autowired SalesOrderService sales;
    @Autowired StockPostingService posting;
    @Autowired ReservationService reservations;
    @Autowired ApprovalPort approval;
    @Autowired OutboxDispatcher dispatcher;
    @Autowired DocumentLineageService lineage;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void setup() {
        for (String table : List.of("fin_settlement_record","fin_payment","fin_receipt","fin_account_payable","fin_account_receivable")) {
            jdbc.update("DELETE FROM " + table + " WHERE tenant_id=?", T);
        }
        jdbc.update("INSERT INTO md_currency (tenant_id,code,name,is_base,enabled) VALUES (?, 'CNY','人民币',TRUE,TRUE) ON CONFLICT DO NOTHING", T);
        for (String table : List.of("doc_relation", "erp_outbox_message", "erp_state_transition", "apr_instance",
                "sal_shipment_line", "sal_shipment", "sal_order_line", "sal_order", "sal_credit_account",
                "inv_reservation", "inv_transaction", "inv_balance", "num_sequence", "num_rule")) {
            jdbc.update("DELETE FROM " + table + " WHERE tenant_id = ?", T);
        }
        jdbc.update("INSERT INTO num_rule (tenant_id,business_type,prefix,seq_width) VALUES (?, 'SO','SO',6),(?,'OUT','OUT',6)", T,T);
        posting.post(new PostingRequest(bucket(), PostingDirection.IN, bd("100"), "OPENING", "TEST", "1", "1", USER));
        for (String code : List.of("AR","AP","RCV","PAY")) {
            jdbc.update("INSERT INTO num_rule (tenant_id,business_type,prefix,seq_width) VALUES (?,?,?,6) ON CONFLICT DO NOTHING", T,code,code);
        }
    }
    private BigDecimal bd(String value) { return new BigDecimal(value); }
    private InventoryBucket bucket() { return InventoryBucket.of(T,C,W,SKU); }
    private List<NewLine> lines(String qty) {
        return List.of(new NewLine(SKU,new MasterDataRef(SKU,"SKU","纸\n张","个"),"-",bd(qty),bd("10")));
    }
    private long create(String qty, BigDecimal limit, Long override) {
        return sales.createOrder(T,C,CUSTOMER,W,new MasterDataRef(CUSTOMER,"CUS","客户",null),limit,override,lines(qty),"/9001/",USER);
    }
    private long approved(String qty) {
        long id=create(qty,bd("100000"),null);
        long approvalId=sales.submitForApproval(T,id,USER);
        sales.approve(T,id,approvalId,USER+1);
        return id;
    }
    private long line(long id) { return sales.lines(T,id).get(0).id(); }
    private long ship(long id,String qty) { return sales.ship(T,id,List.of(new ShipLine(line(id),bd(qty))),USER); }
    private void equal(String expected,BigDecimal actual) { assertEquals(0,bd(expected).compareTo(actual)); }

    @Test void 分两批出库发运签收且双向追溯() {
        long id=approved("100"); sales.reserveStock(T,id);
        equal("100",posting.balance(bucket()).reserved());
        long first=ship(id,"40");
        assertEquals("PARTIAL_FINISHED",sales.order(T,id).state());
        long second=ship(id,"60");
        for(long shipment:List.of(first,second)) { sales.deliver(T,shipment); sales.sign(T,shipment); }
        assertEquals("FINISHED",sales.order(T,id).state());
        equal("0",posting.balance(bucket()).onHand());
        equal("0",posting.balance(bucket()).reserved());
        equal("0",reservations.find(T,"SALES_ORDER",""+id,""+line(id)).remaining());
        equal("0",posting.ledgerSum(bucket()));
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM sal_shipment WHERE tenant_id=? AND signed_at IS NOT NULL",Integer.class,T));
        dispatcher.dispatchBatch();
        assertEquals(2,lineage.downstream(T,"SALES_ORDER",""+id).size());
        assertEquals(""+id,lineage.upstream(T,"SALES_SHIPMENT",""+first).get(0).get("parentId"));
        assertEquals(""+id,lineage.upstream(T,"SALES_SHIPMENT",""+second).get(0).get("parentId"));
    }
    @Test void 库存不足回滚且保持已审批() {
        long id=approved("101");
        assertThrows(DomainException.class,()->sales.reserveStock(T,id));
        assertEquals("APPROVED",sales.order(T,id).state());
        assertFalse(sales.order(T,id).reserved());
        assertNull(reservations.find(T,"SALES_ORDER",""+id,""+line(id)));
        equal("0",posting.balance(bucket()).reserved());
    }
    @Test void 取消同时释放预占余额和信用且不能再次预占() {
        long id=approved("80"); sales.reserveStock(T,id); sales.cancel(T,id,USER);
        assertEquals("CANCELLED",sales.order(T,id).state());
        equal("80",reservations.find(T,"SALES_ORDER",""+id,""+line(id)).releasedQty());
        equal("0",posting.balance(bucket()).reserved()); equal("100",posting.balance(bucket()).onHand());
        equal("0",sales.usedCredit(T,CUSTOMER));
        assertThrows(DomainException.class,()->sales.reserveStock(T,id));
        assertThrows(DomainException.class,()->sales.cancel(T,id,USER));
        equal("0",sales.usedCredit(T,CUSTOMER));
    }
    @Test void 信用超限必须真实审批且只能使用一次() {
        assertThrows(DomainException.class,()->create("10",bd("50"),null));
        equal("0",sales.usedCredit(T,CUSTOMER));
        long aid=sales.requestCreditApproval(T,C,CUSTOMER,W,bd("50"),lines("10"),"/9001/",USER);
        assertThrows(DomainException.class,()->create("10",bd("50"),aid));
        approval.approve(T,aid,USER+1);
        assertThrows(DomainException.class,()->create("11",bd("50"),aid));
        long id=create("10",bd("50"),aid);
        equal("100",sales.usedCredit(T,CUSTOMER));
        assertThrows(RuntimeException.class,()->create("10",bd("50"),aid));
        equal("100",sales.usedCredit(T,CUSTOMER));
        sales.cancel(T,id,USER);
        assertThrows(RuntimeException.class,()->create("10",bd("50"),aid));
        equal("0",sales.usedCredit(T,CUSTOMER));
    }
    @Test void 草稿不能预占且审批不可串单() {
        long a=create("10",bd("10000"),null), b=create("10",bd("10000"),null);
        assertThrows(DomainException.class,()->sales.reserveStock(T,a));
        long aid=sales.submitForApproval(T,a,USER); sales.submitForApproval(T,b,USER);
        assertThrows(DomainException.class,()->sales.approve(T,b,aid,USER+1));
        assertEquals("PENDING",approval.statusOf(T,"SALES_ORDER",""+a));
    }
    @Test void 出库拒绝其他订单行且整笔回滚() {
        long a=approved("50"),b=approved("50"); sales.reserveStock(T,a);sales.reserveStock(T,b);
        assertThrows(DomainException.class,()->sales.ship(T,a,List.of(new ShipLine(line(b),bd("10"))),USER));
        equal("0",sales.lines(T,b).get(0).shippedQty());
        equal("100",posting.balance(bucket()).onHand());equal("100",posting.balance(bucket()).reserved());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM sal_shipment WHERE tenant_id=?",Integer.class,T));
    }
    @Test void 多行预占失败整体回滚() {
        List<NewLine> items=List.of(lines("20").get(0),new NewLine(SKU+1,null,"-",bd("1"),bd("10")));
        long id=sales.createOrder(T,C,CUSTOMER,W,null,bd("10000"),null,items,"/9001/",USER);
        sales.approve(T,id,sales.submitForApproval(T,id,USER),USER+1);
        assertThrows(DomainException.class,()->sales.reserveStock(T,id));
        equal("0",posting.balance(bucket()).reserved());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM inv_reservation WHERE tenant_id=?",Integer.class,T));
    }
    @Test void 超发负数和超精度被拒且未发运不能签收() {
        long id=approved("50");sales.reserveStock(T,id);
        for(String qty:List.of("51","-1","0","0.0000001")) assertThrows(DomainException.class,()->ship(id,qty));
        equal("100",posting.balance(bucket()).onHand());equal("50",posting.balance(bucket()).reserved());
        long shipment=ship(id,"20");
        assertThrows(DomainException.class,()->sales.sign(T,shipment));
        assertThrows(DomainException.class,()->sales.cancel(T,id,USER));
        sales.deliver(T,shipment);sales.sign(T,shipment);
        assertThrows(DomainException.class,()->sales.sign(T,shipment));
        assertThrows(DomainException.class,()->sales.deliver(T+1,shipment));
    }
    @Test void 并发预占与取消不会遗留预占() throws Exception {
        long id=approved("50");
        race(()->{try {sales.reserveStock(T,id);}catch(DomainException expected){}},()->sales.cancel(T,id,USER));
        assertEquals("CANCELLED",sales.order(T,id).state());
        equal("0",posting.balance(bucket()).reserved());equal("0",sales.usedCredit(T,CUSTOMER));
        var reservation=reservations.find(T,"SALES_ORDER",""+id,""+line(id));
        if(reservation!=null) equal("0",reservation.remaining());
    }
    @Test void 并发分批出库不超发() throws Exception {
        long id=approved("100");sales.reserveStock(T,id);
        java.util.concurrent.atomic.AtomicInteger success=new java.util.concurrent.atomic.AtomicInteger();
        Runnable action=()->{try {ship(id,"60");success.incrementAndGet();}catch(DomainException expected){}};
        race(action,action);assertEquals(1,success.get());
        equal("60",sales.lines(T,id).get(0).shippedQty());equal("40",posting.balance(bucket()).onHand());
        equal("40",posting.balance(bucket()).reserved());
    }
    @Test void 并发信用占用不突破额度() throws Exception {
        java.util.concurrent.atomic.AtomicInteger success=new java.util.concurrent.atomic.AtomicInteger();
        Runnable action=()->{try{create("6",bd("100"),null);success.incrementAndGet();}catch(DomainException expected){}};
        race(action,action);assertEquals(1,success.get());equal("60",sales.usedCredit(T,CUSTOMER));
    }
    @Test void 跨租户不能预占取消或查询订单() {
        long id=approved("10");
        assertNull(sales.order(T+1,id));
        assertThrows(DomainException.class,()->sales.reserveStock(T+1,id));
        assertThrows(DomainException.class,()->sales.cancel(T+1,id,USER));
        equal("100",sales.usedCredit(T,CUSTOMER));
    }
    /** 双线程同时起跑，超时必须失败而不能挂住验收。 */
    private void race(Runnable a,Runnable b) throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        try {
            var one=pool.submit(()->{start.await();a.run();return null;});
            var two=pool.submit(()->{start.await();b.run();return null;});
            start.countDown();one.get(15,TimeUnit.SECONDS);two.get(15,TimeUnit.SECONDS);
        } finally {pool.shutdownNow();}
    }
}
