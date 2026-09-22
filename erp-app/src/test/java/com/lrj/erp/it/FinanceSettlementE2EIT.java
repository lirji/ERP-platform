package com.lrj.erp.it;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lrj.erp.finance.application.*;
import com.lrj.erp.finance.domain.*;
import com.lrj.erp.kernel.error.DomainException;
import com.lrj.erp.kernel.events.PostedDocument;
import com.lrj.erp.kernel.outbox.*;
import com.lrj.erp.procurement.application.PurchaseOrderService;
import com.lrj.erp.sales.application.SalesOrderService;
import com.lrj.erp.document.service.DocumentLineageService;
import com.lrj.erp.inventory.application.StockPostingService;
import com.lrj.erp.inventory.domain.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** P6 两条闭环和财务不变量；全部在真实 PostgreSQL 事务上断言。 */
class FinanceSettlementE2EIT extends AbstractPostgresIT {
    static final long T=260,C=9001,W=7001,SKU=5001,PARTNER=6001,U=4501;
    @Autowired SettlementService finance;
    @Autowired FinancialPostingListener consumer;
    @Autowired PurchaseOrderService purchase;
    @Autowired SalesOrderService sales;
    @Autowired StockPostingService posting;
    @Autowired OutboxDispatcher dispatcher;
    @Autowired OutboxRecorder recorder;
    @Autowired DocumentLineageService lineage;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;

    @BeforeEach void setup(){
        for(String table:List.of("fin_settlement_record","fin_payment","fin_receipt","fin_account_payable","fin_account_receivable",
                "doc_relation","erp_outbox_message","erp_state_transition","apr_instance","sal_shipment_line","sal_shipment",
                "sal_order_line","sal_order","sal_credit_account","pur_receipt_line","pur_receipt","pur_order_line","pur_order",
                "inv_reservation","inv_transaction","inv_balance","num_sequence","num_rule"))
            jdbc.update("DELETE FROM "+table+" WHERE tenant_id=?",T);
        jdbc.update("INSERT INTO md_currency (tenant_id,code,name,is_base,enabled) VALUES (?,'CNY','人民币',TRUE,TRUE) ON CONFLICT DO NOTHING",T);
        for(String code:List.of("PO","IN","SO","OUT","AR","AP","RCV","PAY"))
            jdbc.update("INSERT INTO num_rule (tenant_id,business_type,prefix,seq_width) VALUES (?,?,?,6)",T,code,code);
    }
    BigDecimal bd(String n){return new BigDecimal(n);}
    void equal(String n,BigDecimal actual){assertEquals(0,bd(n).compareTo(actual));}
    PostedDocument fact(String id,BillType type,String amount){
        return new PostedDocument(type==BillType.AP?"PURCHASE_ORDER":"SALES_ORDER","ORDER-"+id,"ORDER-"+id,
                type==BillType.AP?"PURCHASE_RECEIPT":"SALES_SHIPMENT",id,"DOC-"+id,C,PARTNER,bd(amount),"CNY","/9001/",U);
    }
    long bill(BillType type,String id,String amount){return finance.acceptPosted(T,type,fact(id,type,amount));}
    void drain(){for(int i=0;i<5;i++)dispatcher.dispatchBatch();}

    @Test void 采购到付款核销完整闭环并追溯源头(){
        long order=purchase.createOrder(T,C,PARTNER,W,null,List.of(new PurchaseOrderService.NewLine(SKU,null,bd("10"),bd("12.5"))),"/9001/",U);
        purchase.approve(T,order,purchase.submitForApproval(T,order,U),U+1);
        long receipt=purchase.receive(T,order,List.of(new PurchaseOrderService.ReceiptLine(purchase.lines(T,order).get(0).id(),"-",bd("10"))),U);
        drain();
        var ap=finance.source(T,BillType.AP,"PURCHASE_RECEIPT",""+receipt);assertNotNull(ap);equal("125",ap.amount());
        long pay=finance.recordCash(T,BillType.AP,ap.id(),bd("125"),"CNY","P2P-1",U);
        long settlement=finance.apply(T,BillType.AP,ap.id(),pay,bd("125"),U);
        equal("125",finance.bill(T,BillType.AP,ap.id()).writtenOffAmount());
        equal("125",finance.cash(T,BillType.AP,pay).writtenOffAmount());
        assertNotNull(finance.settlement(T,settlement));drain();
        assertEquals(""+receipt,lineage.upstream(T,"PAYABLE",""+ap.id()).get(0).get("parentId"));
        assertEquals(""+order,lineage.upstream(T,"PURCHASE_RECEIPT",""+receipt).get(0).get("parentId"));
        assertEquals(""+ap.id(),lineage.upstream(T,"PAYMENT",""+pay).get(0).get("parentId"));
    }
    @Test void 订单到收款核销闭环且信用随反核销恢复(){
        posting.post(new PostingRequest(InventoryBucket.of(T,C,W,SKU),PostingDirection.IN,bd("10"),"OPENING","TEST","1","1",U));
        long order=sales.createOrder(T,C,PARTNER,W,null,bd("100"),null,List.of(new SalesOrderService.NewLine(SKU,null,"-",bd("10"),bd("10"))),"/9001/",U);
        sales.approve(T,order,sales.submitForApproval(T,order,U),U+1);sales.reserveStock(T,order);
        long shipment=sales.ship(T,order,List.of(new SalesOrderService.ShipLine(sales.lines(T,order).get(0).id(),bd("10"))),U);
        sales.deliver(T,shipment);sales.sign(T,shipment);drain();
        var ar=finance.source(T,BillType.AR,"SALES_SHIPMENT",""+shipment);equal("100",ar.amount());
        long receipt=finance.recordCash(T,BillType.AR,ar.id(),bd("100"),"CNY","O2C-1",U);
        long settlement=finance.apply(T,BillType.AR,ar.id(),receipt,bd("100"),U);
        equal("100",finance.bill(T,BillType.AR,ar.id()).writtenOffAmount());equal("0",sales.usedCredit(T,PARTNER));
        finance.reverse(T,settlement,U,"误核销更正");equal("100",sales.usedCredit(T,PARTNER));
        finance.apply(T,BillType.AR,ar.id(),receipt,bd("100"),U);equal("0",sales.usedCredit(T,PARTNER));drain();
        assertEquals(""+shipment,lineage.upstream(T,"RECEIVABLE",""+ar.id()).get(0).get("parentId"));
        assertEquals(""+order,lineage.upstream(T,"SALES_SHIPMENT",""+shipment).get(0).get("parentId"));
        assertEquals(""+ar.id(),lineage.upstream(T,"RECEIPT",""+receipt).get(0).get("parentId"));
        // 已核销金额释放授信后可继续下单，而不是永久占住额度。
        assertDoesNotThrow(()->sales.createOrder(T,C,PARTNER,W,null,bd("100"),null,
                List.of(new SalesOrderService.NewLine(SKU,null,"-",bd("10"),bd("10"))),"/9001/",U));
    }
    @Test void 同一入库事件投递十次仅一张应付() throws Exception {
        var event=fact("DUP",BillType.AP,"123.45");
        OutboxMessage message=new OutboxMessage(0,T,"PurchaseReceipt","DUP",PostedDocument.PURCHASE,json.writeValueAsString(event),"PENDING",0,null,null);
        for(int i=0;i<10;i++)consumer.on(message);
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM fin_account_payable WHERE tenant_id=?",Integer.class,T));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM erp_outbox_message WHERE tenant_id=? AND event_type='PayableCreated.v1'",Integer.class,T));
    }
    @Test void 并发重复事件唯一且冲突内容拒绝() throws Exception {
        var event=fact("DUP-C",BillType.AP,"100");
        race(()->finance.acceptPosted(T,BillType.AP,event),()->finance.acceptPosted(T,BillType.AP,event));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM fin_account_payable WHERE tenant_id=?",Integer.class,T));
        assertThrows(DomainException.class,()->finance.acceptPosted(T,BillType.AP,fact("DUP-C",BillType.AP,"101")));
    }
    @Test void 超额付款重复核销与超额核销拒绝且两端回滚(){
        long id=bill(BillType.AP,"GUARD","100");
        assertThrows(DomainException.class,()->finance.recordCash(T,BillType.AP,id,bd("101"),"CNY","bad",U));
        equal("0",finance.bill(T,BillType.AP,id).paidAmount());
        long pay=finance.recordCash(T,BillType.AP,id,bd("60"),"CNY","ok",U);
        finance.recordCash(T,BillType.AP,id,bd("40"),"CNY","second",U);
        assertThrows(DomainException.class,()->finance.apply(T,BillType.AP,id,pay,bd("61"),U));
        equal("0",finance.bill(T,BillType.AP,id).writtenOffAmount());equal("0",finance.cash(T,BillType.AP,pay).writtenOffAmount());
        finance.apply(T,BillType.AP,id,pay,bd("50"),U);
        assertThrows(DomainException.class,()->finance.apply(T,BillType.AP,id,pay,bd("10"),U));
        equal("50",finance.bill(T,BillType.AP,id).writtenOffAmount());
    }
    @Test void 反核销生成负记录并恢复两端且可再核销(){
        for(BillType type:BillType.values()){
            long id=bill(type,"REV-"+type,"100"),cash=finance.recordCash(T,type,id,bd("100"),"CNY","REV-"+type,U);
            long original=finance.apply(T,type,id,cash,bd("80"),U);
            long reversed=finance.reverse(T,original,U,"金额录入错误");
            equal("-80",finance.settlement(T,reversed).amount());assertEquals(original,finance.settlement(T,reversed).reversalOf());
            equal("80",finance.settlement(T,original).amount());assertTrue(finance.settlement(T,original).reversed());
            equal("0",finance.bill(T,type,id).writtenOffAmount());equal("0",finance.cash(T,type,cash).writtenOffAmount());
            equal("100",finance.bill(T,type,id).paidAmount());
            assertThrows(DomainException.class,()->finance.reverse(T,original,U,"重复"));
            finance.apply(T,type,id,cash,bd("100"),U);equal("100",finance.bill(T,type,id).writtenOffAmount());
        }
    }
    @Test void 收付款幂等同键不同内容拒绝(){
        long id=bill(BillType.AR,"IDEM","100");
        long first=finance.recordCash(T,BillType.AR,id,bd("60"),"CNY","same",U);
        assertEquals(first,finance.recordCash(T,BillType.AR,id,bd("60"),"CNY","same",U));
        assertThrows(DomainException.class,()->finance.recordCash(T,BillType.AR,id,bd("61"),"CNY","same",U));
        equal("60",finance.bill(T,BillType.AR,id).paidAmount());
    }
    @Test void 跨租户币种串单及非法金额拒绝(){
        long a=bill(BillType.AP,"A","100"),b=bill(BillType.AP,"B","100");
        long cash=finance.recordCash(T,BillType.AP,a,bd("50"),"CNY","A",U);
        assertThrows(DomainException.class,()->finance.apply(T+1,BillType.AP,a,cash,bd("1"),U));
        assertThrows(DomainException.class,()->finance.apply(T,BillType.AP,b,cash,bd("1"),U));
        assertThrows(DomainException.class,()->finance.recordCash(T,BillType.AP,a,bd("1"),"USD","usd",U));
        for(String amount:List.of("-1","0","0.00001"))assertThrows(DomainException.class,()->finance.recordCash(T,BillType.AP,a,bd(amount),"CNY","bad",U));
        equal("0",finance.bill(T,BillType.AP,a).writtenOffAmount());
    }
    @Test void 并发付款与核销不超额且并发反核销一次() throws Exception {
        long id=bill(BillType.AP,"RACE","100");AtomicInteger paid=new AtomicInteger();
        race(()->attempt(()->finance.recordCash(T,BillType.AP,id,bd("60"),"CNY","a",U),paid),
                ()->attempt(()->finance.recordCash(T,BillType.AP,id,bd("60"),"CNY","b",U),paid));
        assertEquals(1,paid.get());equal("60",finance.bill(T,BillType.AP,id).paidAmount());
        long cash=jdbc.queryForObject("SELECT id FROM fin_payment WHERE tenant_id=?",Long.class,T);
        AtomicInteger applied=new AtomicInteger();
        race(()->attempt(()->finance.apply(T,BillType.AP,id,cash,bd("60"),U),applied),()->attempt(()->finance.apply(T,BillType.AP,id,cash,bd("60"),U),applied));
        assertEquals(1,applied.get());long sid=jdbc.queryForObject("SELECT id FROM fin_settlement_record WHERE tenant_id=?",Long.class,T);
        AtomicInteger reversed=new AtomicInteger();
        race(()->attempt(()->finance.reverse(T,sid,U,"更正"),reversed),()->attempt(()->finance.reverse(T,sid,U,"更正"),reversed));
        assertEquals(1,reversed.get());equal("0",finance.bill(T,BillType.AP,id).writtenOffAmount());equal("0",finance.cash(T,BillType.AP,cash).writtenOffAmount());
    }
    @Test void 坏财务事件回滚且同批好消息仍成功(){
        var good=fact("GOOD",BillType.AP,"100");
        tx.executeWithoutResult(s->{recorder.record(T,"PurchaseReceipt","BAD",PostedDocument.PURCHASE,Map.of("amount","1"));recorder.record(T,"PurchaseReceipt","GOOD",PostedDocument.PURCHASE,good);});
        dispatcher.dispatchBatch();
        assertNotNull(finance.source(T,BillType.AP,"PURCHASE_RECEIPT","GOOD"));
        assertEquals("PENDING",jdbc.queryForObject("SELECT status FROM erp_outbox_message WHERE tenant_id=? AND aggregate_id='BAD'",String.class,T));
        assertEquals(1,jdbc.queryForObject("SELECT retry_count FROM erp_outbox_message WHERE tenant_id=? AND aggregate_id='BAD'",Integer.class,T));
    }
    @Test void 缺财务编号时失败回滚恢复配置后重投成功(){
        jdbc.update("DELETE FROM num_rule WHERE tenant_id=? AND business_type='AP'",T);
        tx.executeWithoutResult(s->recorder.record(T,"PurchaseReceipt","RETRY",PostedDocument.PURCHASE,fact("RETRY",BillType.AP,"100")));
        dispatcher.dispatchBatch();assertNull(finance.source(T,BillType.AP,"PURCHASE_RECEIPT","RETRY"));
        jdbc.update("INSERT INTO num_rule (tenant_id,business_type,prefix,seq_width) VALUES (?,'AP','AP',6)",T);
        jdbc.update("UPDATE erp_outbox_message SET next_retry_at=now() WHERE tenant_id=?",T);
        dispatcher.dispatchBatch();assertNotNull(finance.source(T,BillType.AP,"PURCHASE_RECEIPT","RETRY"));
    }
    @Test void 分批舍入金额总和等于整单金额(){
        long order=purchase.createOrder(T,C,PARTNER,W,null,List.of(new PurchaseOrderService.NewLine(SKU,null,bd("3"),bd("0.00005"))),"/9001/",U);
        purchase.approve(T,order,purchase.submitForApproval(T,order,U),U+1);
        for(int i=0;i<3;i++)purchase.receive(T,order,List.of(new PurchaseOrderService.ReceiptLine(purchase.lines(T,order).get(0).id(),"-",bd("1"))),U);
        drain();equal("0.0002",jdbc.queryForObject("SELECT sum(amount) FROM fin_account_payable WHERE tenant_id=?",BigDecimal.class,T));
    }
    @Test void 多行销售舍入与信用本金一致(){
        posting.post(new PostingRequest(InventoryBucket.of(T,C,W,SKU),PostingDirection.IN,bd("2"),"OPENING","TEST","ROUND","1",U));
        var item=new SalesOrderService.NewLine(SKU,null,"-",bd("1"),bd("0.00005"));
        long order=sales.createOrder(T,C,PARTNER,W,null,bd("1"),null,List.of(item,item),"/9001/",U);
        equal("0.0002",sales.order(T,order).totalAmount());
        sales.approve(T,order,sales.submitForApproval(T,order,U),U+1);sales.reserveStock(T,order);
        long shipment=sales.ship(T,order,sales.lines(T,order).stream().map(l->new SalesOrderService.ShipLine(l.id(),bd("1"))).toList(),U);
        drain();var ar=finance.source(T,BillType.AR,"SALES_SHIPMENT",""+shipment);equal("0.0002",ar.amount());
        long cash=finance.recordCash(T,BillType.AR,ar.id(),ar.amount(),"CNY","ROUND",U);
        finance.apply(T,BillType.AR,ar.id(),cash,ar.amount(),U);equal("0",sales.usedCredit(T,PARTNER));
    }
    @Test void 关系投影失败必须重试且不阻断同批财务事实(){
        tx.executeWithoutResult(s->{
            recorder.record(T,"Projection","BAD-LINK","Projection.v1",Map.of("parentType","SALES_ORDER","childType","SALES_SHIPMENT"));
            recorder.record(T,"PurchaseReceipt","GOOD-LINK",PostedDocument.PURCHASE,fact("GOOD-LINK",BillType.AP,"10"));
        });
        dispatcher.dispatchBatch();
        assertEquals("PENDING",jdbc.queryForObject("SELECT status FROM erp_outbox_message WHERE tenant_id=? AND aggregate_id='BAD-LINK'",String.class,T));
        assertEquals(1,jdbc.queryForObject("SELECT retry_count FROM erp_outbox_message WHERE tenant_id=? AND aggregate_id='BAD-LINK'",Integer.class,T));
        assertNotNull(finance.source(T,BillType.AP,"PURCHASE_RECEIPT","GOOD-LINK"));
    }

    @Test void 并发分批收货的累计舍入不重复计算() throws Exception {
        long order=purchase.createOrder(T,C,PARTNER,W,null,List.of(new PurchaseOrderService.NewLine(SKU,null,bd("2"),bd("0.00005"))),"/9001/",U);
        purchase.approve(T,order,purchase.submitForApproval(T,order,U),U+1);
        long line=purchase.lines(T,order).get(0).id();
        Runnable receive=()->purchase.receive(T,order,List.of(new PurchaseOrderService.ReceiptLine(line,"-",bd("1"))),U);
        race(receive,receive);drain();
        equal("0.0001",jdbc.queryForObject("SELECT sum(amount) FROM fin_account_payable WHERE tenant_id=?",BigDecimal.class,T));
        assertEquals("FINISHED",purchase.order(T,order).state());
    }

    private void attempt(Runnable work,AtomicInteger success){try{work.run();success.incrementAndGet();}catch(DomainException expected){}}
    private void race(Runnable a,Runnable b) throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        try{var one=pool.submit(()->{start.await();a.run();return null;});var two=pool.submit(()->{start.await();b.run();return null;});
            start.countDown();one.get(20,TimeUnit.SECONDS);two.get(20,TimeUnit.SECONDS);
        }finally{pool.shutdownNow();}
    }
}
