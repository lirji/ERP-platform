package com.lrj.erp.it;
import com.lrj.erp.app.ErpApplication;
import com.lrj.erp.kernel.events.PostedDocument;
import com.lrj.erp.kernel.outbox.OutboxRecorder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

/** 启用真实后台轮询，记录本地样本的投递滞后；不是生产容量承诺。 */
@SpringBootTest(classes=ErpApplication.class,properties={"erp.outbox.scheduling-enabled=true","erp.outbox.poll-interval-ms=1000","erp.monitoring.enabled=false"})
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class OutboxLatencyIT extends AbstractPostgresIT {
    @Autowired JdbcTemplate jdbc;
    @Autowired OutboxRecorder recorder;
    @Autowired TransactionTemplate tx;
    @Test void 后台投递二十条财务事件的P95不超过六十秒() throws Exception {
        long tenant=261;
        jdbc.update("DELETE FROM erp_outbox_message WHERE tenant_id=?",tenant);
        jdbc.update("DELETE FROM fin_account_payable WHERE tenant_id=?",tenant);
        jdbc.update("DELETE FROM doc_relation WHERE tenant_id=?",tenant);
        jdbc.update("INSERT INTO num_rule (tenant_id,business_type,prefix,seq_width) VALUES (?,'AP','AP',6) ON CONFLICT DO NOTHING",tenant);
        tx.executeWithoutResult(s->{for(int i=0;i<20;i++)recorder.record(tenant,"PurchaseReceipt",""+i,PostedDocument.PURCHASE,
                new PostedDocument("PURCHASE_ORDER",""+i,"PO-"+i,"PURCHASE_RECEIPT",""+i,"IN-"+i,
                        9001,6001,new BigDecimal("10.0000"),"CNY","/9001/",4501));});
        long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(20);
        int published=0;
        while(System.nanoTime()<deadline){
            published=jdbc.queryForObject("SELECT count(*) FROM erp_outbox_message WHERE tenant_id=? AND event_type=? AND status='PUBLISHED'",Integer.class,tenant,PostedDocument.PURCHASE);
            if(published==20)break;
            Thread.sleep(100);
        }
        assertEquals(20,published,"必须由真实后台调度投递完成，不能手动调用代替");
        assertEquals(20,jdbc.queryForObject("SELECT count(*) FROM fin_account_payable WHERE tenant_id=?",Integer.class,tenant));
        Double p95=jdbc.queryForObject("SELECT percentile_cont(0.95) WITHIN GROUP (ORDER BY EXTRACT(EPOCH FROM published_at-created_at)) FROM erp_outbox_message WHERE tenant_id=? AND event_type=?",Double.class,tenant,PostedDocument.PURCHASE);
        assertNotNull(p95);assertTrue(p95>=0 && p95<=60,"P95="+p95);
        System.out.println("P6_OUTBOX_LOCAL samples=20 pollIntervalMs=1000 p95Seconds="+p95);
    }
}
