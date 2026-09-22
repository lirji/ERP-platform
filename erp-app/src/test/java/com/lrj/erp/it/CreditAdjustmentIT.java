package com.lrj.erp.it;
import com.lrj.erp.finance.application.*;
import com.lrj.erp.finance.domain.*;
import com.lrj.erp.kernel.events.*;
import com.lrj.erp.kernel.error.DomainException;
import com.lrj.erp.kernel.finance.SettledCreditQuery;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** 已结算退货采用独立红字/退款事实，原现金与核销流水不被冲改。 */
class CreditAdjustmentIT extends AbstractPostgresIT {
    static final long T=272;
    @Autowired SettlementService finance;
    @Autowired CreditAdjustmentService credits;
    @Autowired SettledCreditQuery creditQuery;
    @Autowired JdbcTemplate jdbc;
    @BeforeEach void setup() {
        for(String table:List.of("fin_refund_record","fin_credit_adjustment","fin_settlement_record","fin_receipt","fin_payment",
                "fin_account_receivable","fin_account_payable","erp_outbox_message","doc_relation","num_sequence","num_rule"))
            jdbc.update("DELETE FROM "+table+" WHERE tenant_id=?",T);
        for(String rule:List.of("AR","AP","RCV","PAY","CREDIT"))
            jdbc.update("INSERT INTO num_rule (tenant_id,business_type,prefix,seq_width) VALUES (?,?,?,6)",T,rule,rule);
    }
    @AfterEach void cleanOutbox() { jdbc.update("DELETE FROM erp_outbox_message WHERE tenant_id=?",T); }
    BigDecimal bd(String n) { return new BigDecimal(n); }
    void amount(String n,BigDecimal actual) { assertEquals(0,bd(n).compareTo(actual)); }
    long bill(BillType type) {
        return finance.acceptPosted(T,type,new PostedDocument(type==BillType.AR?"SALES_ORDER":"PURCHASE_ORDER","O1","O1",
                type==BillType.AR?"SALES_SHIPMENT":"PURCHASE_RECEIPT","S1","S1",1,2,bd("100"),"CNY","/1/",1));
    }
    ReturnPosted event(BillType type,String id,String amount) {
        return new ReturnPosted(type==BillType.AR?"SALES_SHIPMENT":"PURCHASE_RECEIPT","S1",
                type==BillType.AR?"SALES_RETURN":"PURCHASE_RETURN",id,id,1,2,bd(amount),"CNY",1);
    }
    @Test void settledSaleReturnKeepsCashAndSettlementCreatesRefund() {
        long bill=bill(BillType.AR);
        long cash=finance.recordCash(T,BillType.AR,bill,bd("100"),"CNY","cash",1);
        long settlement=finance.apply(T,BillType.AR,bill,cash,bd("100"),1);
        long credit=credits.accept(T,BillType.AR,event(BillType.AR,"R1","40"));
        amount("40",credits.get(T,credit).refundDue());
        amount("-40",credits.get(T,credit).amount());
        amount("100",finance.bill(T,BillType.AR,bill).amount());
        amount("100",finance.cash(T,BillType.AR,cash).amount());
        amount("100",finance.settlement(T,settlement).amount());
        amount("100",creditQuery.releasedCredit(T,2));
        long refund=credits.recordRefund(T,credit,bd("15"),"CNY","refund1",1);
        assertEquals(refund,credits.recordRefund(T,credit,bd("15"),"CNY","refund1",1));
        assertThrows(DomainException.class,()->credits.recordRefund(T,credit,bd("26"),"CNY","tooMuch",1));
        credits.recordRefund(T,credit,bd("25"),"CNY","refund2",1);
        amount("40",credits.get(T,credit).refundedAmount());
    }
    @Test void unpaidThenPartPaidPurchaseSplitsCreditAndRefund() {
        long bill=bill(BillType.AP);
        finance.recordCash(T,BillType.AP,bill,bd("60"),"CNY","cash",1);
        long first=credits.accept(T,BillType.AP,event(BillType.AP,"R1","30"));
        amount("0",credits.get(T,first).refundDue());
        long second=credits.accept(T,BillType.AP,event(BillType.AP,"R2","30"));
        amount("20",credits.get(T,second).refundDue());
        assertThrows(DomainException.class,()->finance.recordCash(T,BillType.AP,bill,bd("1"),"CNY","extra",1));
        assertThrows(DomainException.class,()->credits.accept(T,BillType.AP,event(BillType.AP,"R3","41")));
        amount("60",finance.bill(T,BillType.AP,bill).creditedAmount());
    }
    @Test void duplicateCreditRejectsChangedContents() {
        bill(BillType.AR);
        long id=credits.accept(T,BillType.AR,event(BillType.AR,"R1","20"));
        assertEquals(id,credits.accept(T,BillType.AR,event(BillType.AR,"R1","20")));
        assertThrows(DomainException.class,()->credits.accept(T,BillType.AR,event(BillType.AR,"R1","21")));
        assertThrows(DomainException.class,()->credits.get(T+1,id));
    }
    @Test void zeroPricedCreditHasNoRefund() {
        bill(BillType.AR);
        long id=credits.accept(T,BillType.AR,event(BillType.AR,"ZERO","0"));
        amount("0",credits.get(T,id).amount());amount("0",credits.get(T,id).refundDue());
    }
    @Test void creditPreventsNewExcessSettlementButAllowsReversal() {
        long bill=bill(BillType.AR);
        long cash=finance.recordCash(T,BillType.AR,bill,bd("100"),"CNY","cash",1);
        long settlement=finance.apply(T,BillType.AR,bill,cash,bd("100"),1);
        credits.accept(T,BillType.AR,event(BillType.AR,"R1","40"));
        finance.reverse(T,settlement,1,"保留补偿轨迹");
        assertThrows(DomainException.class,()->finance.apply(T,BillType.AR,bill,cash,bd("61"),1));
        finance.apply(T,BillType.AR,bill,cash,bd("60"),1);
        amount("100",creditQuery.releasedCredit(T,2));
    }
    @Test void concurrentCreditsCannotExceedOriginal() throws Exception {
        long bill=bill(BillType.AR);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->tryCredit("R1"));var b=pool.submit(()->tryCredit("R2"));
            assertEquals(1,a.get(30,TimeUnit.SECONDS)+b.get(30,TimeUnit.SECONDS));
        }
        amount("60",finance.bill(T,BillType.AR,bill).creditedAmount());
    }
    int tryCredit(String id) { try { credits.accept(T,BillType.AR,event(BillType.AR,id,"60"));return 1; } catch(DomainException e) { assertEquals(FinanceErrorCode.EXCEEDS,e.errorCode());return 0; } }
    @Test void concurrentRefundCannotExceedObligation() throws Exception {
        long bill=bill(BillType.AR);
        finance.recordCash(T,BillType.AR,bill,bd("100"),"CNY","cash",1);
        long credit=credits.accept(T,BillType.AR,event(BillType.AR,"R1","40"));
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->tryRefund(credit,"A"));var b=pool.submit(()->tryRefund(credit,"B"));
            assertEquals(1,a.get(30,TimeUnit.SECONDS)+b.get(30,TimeUnit.SECONDS));
        }
        amount("30",credits.get(T,credit).refundedAmount());
    }
    int tryRefund(long credit,String command) {
        try { credits.recordRefund(T,credit,bd("30"),"CNY",command,1);return 1; }
        catch(DomainException e) { assertEquals(FinanceErrorCode.EXCEEDS,e.errorCode());return 0; }
    }
    @Test void missingOriginalIsRetryableFailure() {
        assertThrows(DomainException.class,()->credits.accept(T,BillType.AR,event(BillType.AR,"R1","20")));
        bill(BillType.AR);
        assertTrue(credits.accept(T,BillType.AR,event(BillType.AR,"R1","20"))>0);
    }
}
