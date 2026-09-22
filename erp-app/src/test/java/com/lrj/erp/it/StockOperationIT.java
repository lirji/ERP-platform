package com.lrj.erp.it;

import com.lrj.erp.inventory.application.*;
import com.lrj.erp.inventory.domain.*;
import com.lrj.erp.kernel.error.DomainException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** 调拨守恒、盘点冻结与真实审批的 PostgreSQL 验证。 */
class StockOperationIT extends AbstractPostgresIT {
    static final long T=271;
    final InventoryBucket source=InventoryBucket.ofBatch(T,1,1,1,"B1");
    final InventoryBucket target=InventoryBucket.ofBatch(T,1,2,1,"B1");
    @Autowired StockOperationService operations;
    @Autowired StockPostingService posting;
    @Autowired JdbcTemplate jdbc;
    @BeforeEach void setup() {
        for(String table:List.of("inv_stock_operation","inv_transaction","inv_balance","apr_instance","erp_state_transition","num_sequence","num_rule"))
            jdbc.update("DELETE FROM "+table+" WHERE tenant_id=?",T);
        jdbc.update("INSERT INTO num_rule (tenant_id,business_type,prefix,seq_width) VALUES (?,'STOCK','ST',6)",T);
        posting.post(new PostingRequest(source,PostingDirection.IN,bd("10"),"SEED","SEED","1","1",1,bd("100")));
    }
    BigDecimal bd(String n) { return new BigDecimal(n); }
    void amount(String expected,BigDecimal actual) { assertNotNull(actual); assertEquals(0,bd(expected).compareTo(actual)); }
    void approve(long id) { operations.approve(T,id,operations.submit(T,id,1),2); }
    @Test void transferConservesQuantityAndValueAndIsIdempotent() {
        long id=operations.createTransfer(source,target,bd("4"),"补货",1);
        assertThrows(DomainException.class,()->operations.execute(T,id,1));
        approve(id);
        operations.execute(T,id,1);
        operations.execute(T,id,1);
        amount("6",posting.balance(source).onHand());
        amount("4",posting.balance(source).inTransit());
        amount("0",posting.balance(target).onHand());
        amount("40",operations.get(T,id).inventoryValue());
        assertThrows(DomainException.class,()->operations.cancel(T,id,1));
        operations.receiveTransfer(T,id,1);
        operations.receiveTransfer(T,id,1);
        amount("0",posting.balance(source).inTransit());
        amount("4",posting.balance(target).onHand());
        amount("100",posting.cost(source).inventoryValue().add(posting.cost(target).inventoryValue()));
    }
    @Test void countFreezesAndPostsOnlyAfterApproval() {
        long id=operations.beginCount(source,"月末盘点",1);
        assertThrows(DomainException.class,()->posting.post(new PostingRequest(source,PostingDirection.OUT,bd("1"),"SALE","SALE","2","1",1)));
        assertThrows(Exception.class,()->jdbc.update("UPDATE inv_balance SET on_hand=9 WHERE tenant_id=?",T));
        assertThrows(DomainException.class,()->operations.beginCount(source,"重复盘点",1));
        operations.recordCount(T,id,bd("8"));
        assertThrows(DomainException.class,()->operations.execute(T,id,1));
        amount("10",posting.balance(source).onHand());
        approve(id);
        operations.execute(T,id,1);
        amount("8",posting.balance(source).onHand());
        amount("80",posting.cost(source).inventoryValue());
        amount("8",posting.ledgerSum(source));
        operations.beginCount(source,"再次盘点",1);
    }
    @Test void cancellationReleasesFreezeAndWrongTenantCannotAct() {
        long id=operations.beginCount(source,"盘点",1);
        assertThrows(DomainException.class,()->operations.cancel(T+1,id,1));
        operations.cancel(T,id,1);
        operations.cancel(T,id,1);
        posting.post(new PostingRequest(source,PostingDirection.OUT,bd("1"),"SALE","SALE","2","1",1));
        amount("9",posting.balance(source).onHand());
    }
    @Test void receivingIntoFrozenTargetRollsBackTransit() {
        long id=operations.createTransfer(source,target,bd("3"),"调拨",1);approve(id);operations.execute(T,id,1);
        long count=operations.beginCount(target,"目标盘点",1);
        assertThrows(DomainException.class,()->operations.receiveTransfer(T,id,1));
        amount("3",posting.balance(source).inTransit());
        assertEquals("PROCESSING",operations.get(T,id).state());
        operations.cancel(T,count,1);operations.receiveTransfer(T,id,1);
        amount("3",posting.balance(target).onHand());
    }
    @Test void adjustmentsNeedApprovalAndPreserveSourceLedger() {
        long id=operations.createAdjustment(source,bd("2"),bd("30"),AdjustmentReason.CORRECTION,"纠正漏入库",1);approve(id);operations.execute(T,id,1);
        amount("12",posting.ledgerSum(source));amount("130",posting.cost(source).inventoryValue());
        assertThrows(DomainException.class,()->operations.createAdjustment(source,bd("1"),null,AdjustmentReason.CORRECTION,"",1));
    }
    @Test void concurrentExecutionHappensOnce() throws Exception {
        long id=operations.createTransfer(source,target,bd("3"),"并发调拨",1);approve(id);
        try(var pool=Executors.newFixedThreadPool(4)) {
            var futures=new java.util.ArrayList<Future<?>>();
            for(int i=0;i<8;i++) futures.add(pool.submit(()->operations.execute(T,id,1)));
            for(var f:futures) f.get(30,TimeUnit.SECONDS);
        }
        amount("7",posting.balance(source).onHand());amount("3",posting.balance(source).inTransit());
    }
}
