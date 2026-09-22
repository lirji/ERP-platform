package com.lrj.erp.it;
import com.lrj.erp.kernel.monitoring.*;
import com.lrj.erp.kernel.outbox.OutboxHealthService;
import com.lrj.erp.inventory.application.StockPostingService;
import com.lrj.erp.inventory.domain.*;
import com.lrj.erp.finance.application.SettlementService;
import com.lrj.erp.finance.domain.BillType;
import com.lrj.erp.kernel.events.PostedDocument;
import com.lrj.erp.app.monitoring.BusinessHealthMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.slf4j.*;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** 实际账务差异、积压及提交后审计日志，不以“有指标类”替代可观测性验证。 */
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
class ObservabilityIT extends AbstractPostgresIT {
    static final long T=290;
    @Autowired List<IntegrityProbe> probes;
    @Autowired OutboxHealthService health;
    @Autowired BusinessHealthMetrics metrics;
    @Autowired MeterRegistry meters;
    @Autowired org.springframework.test.web.servlet.MockMvc http;
    @Autowired StockPostingService posting;
    @Autowired SettlementService finance;
    @Autowired BusinessAudit audit;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @BeforeEach void setup() {
        for(String table:List.of("inv_transaction","inv_balance","fin_account_receivable","erp_outbox_message","num_sequence","num_rule"))
            jdbc.update("DELETE FROM "+table+" WHERE tenant_id=?",T);
        jdbc.update("INSERT INTO num_rule(tenant_id,business_type,prefix,seq_width) VALUES (?,'AR','AR',6)",T);
    }
    @AfterEach void clearOutbox() { jdbc.update("DELETE FROM erp_outbox_message WHERE tenant_id=?",T); }
    IntegrityProbe probe(String name) { return probes.stream().filter(p->p.name().equals(name)).findFirst().orElseThrow(); }
    @Test void inventoryDifferenceIsDetectedAndRepairClearsIt() {
        InventoryBucket b=InventoryBucket.of(T,1,1,1);
        posting.post(new PostingRequest(b,PostingDirection.IN,BigDecimal.TEN,"SEED","SEED","1","1",1));
        long id=posting.balance(b).id();
        assertEquals(0,probe("inventory").scan(id-1,1).mismatches());
        jdbc.update("UPDATE inv_balance SET on_hand=9 WHERE tenant_id=?",T);
        assertEquals(1,probe("inventory").scan(id-1,1).mismatches());
        jdbc.update("UPDATE inv_balance SET on_hand=10 WHERE tenant_id=?",T);
        assertEquals(0,probe("inventory").scan(id-1,1).mismatches());
        assertThrows(IllegalArgumentException.class,()->probe("inventory").scan(0,1001));
    }
    @Test void financeLedgerDifferenceIsDetected() {
        long id=finance.acceptPosted(T,BillType.AR,new PostedDocument("SALES_ORDER","1","SO1","SALES_SHIPMENT","1","OUT1",1,2,BigDecimal.TEN,"CNY","/1/",1));
        assertEquals(0,probe("ar").scan(id-1,1).mismatches());
        jdbc.update("UPDATE fin_account_receivable SET paid_amount=1 WHERE tenant_id=? AND id=?",T,id);
        assertEquals(1,probe("ar").scan(id-1,1).mismatches());
        jdbc.update("UPDATE fin_account_receivable SET paid_amount=0 WHERE tenant_id=? AND id=?",T,id);
    }
    @Test void backlogAgeAndDeadMessagesAreActualDatabaseMetrics() throws Exception {
        long before=health.read().dead();
        jdbc.update("INSERT INTO erp_outbox_message(tenant_id,aggregate_type,aggregate_id,event_type,payload,status,created_at) VALUES (?,'TEST','1','Test.v1','{}','PENDING',now()-interval '2 minutes')",T);
        jdbc.update("INSERT INTO erp_outbox_message(tenant_id,aggregate_type,aggregate_id,event_type,payload,status) VALUES (?,'TEST','2','Test.v1','{}','DEAD')",T);
        assertTrue(health.read().oldestSeconds()>=119);assertEquals(before+1,health.read().dead());
        metrics.refresh();
        assertEquals(before+1,meters.get("erp.outbox.dead").gauge().value());
        assertTrue(meters.get("erp.outbox.oldest.seconds").gauge().value()>=119);
        http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/actuator/metrics/erp.outbox.dead"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.measurements[0].value").value((double)before+1));
        assertTrue(meters.getMeters().stream().filter(m->m.getId().getName().startsWith("erp.")).flatMap(m->m.getId().getTags().stream()).noneMatch(t->t.getKey().equals("tenantId")));
    }
    @Test void businessAuditAppearsOnlyAfterCommitAndContainsSearchKeys() {
        var logger=(ch.qos.logback.classic.Logger)LoggerFactory.getLogger("ERP_BUSINESS_AUDIT");
        var captured=new ListAppender<ILoggingEvent>();captured.start();logger.addAppender(captured);
        try {
            MDC.put("traceId","trace-test-290");
            tx.executeWithoutResult(s->{audit.record(T,7,"SALES_ORDER","123","SO-123","APPROVE");assertTrue(captured.list.isEmpty());});
            assertEquals(1,captured.list.size());
            String message=captured.list.getFirst().getFormattedMessage();
            for(String key:List.of("tenantId=290","userId=7","businessType=SALES_ORDER","businessId=123","documentNo=SO-123","traceId=trace-test-290")) assertTrue(message.contains(key),key);
            tx.executeWithoutResult(s->{audit.record(T,7,"SALES_ORDER","124","SO-124","APPROVE");s.setRollbackOnly();});
            assertEquals(1,captured.list.size());
        } finally { MDC.remove("traceId");logger.detachAppender(captured);captured.stop(); }
    }
}
