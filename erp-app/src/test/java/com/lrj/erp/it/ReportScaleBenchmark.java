package com.lrj.erp.it;
import com.lrj.erp.reporting.application.ReportSnapshotService;
import com.lrj.erp.kernel.reporting.ReportSource.Kind;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** 显式运行的规模基准，不混入日常测试：-Dit.test=ReportScaleBenchmark。只使用 erp_it 的租户281。 */
class ReportScaleBenchmark extends AbstractPostgresIT {
    static final long T=281;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @Autowired ReportSnapshotService reports;
    @Test void a04a05Scale() throws Exception {
        clean();
        long started=System.nanoTime();
        try {
            for(int first=1;first<=2_000_000;first+=10_000) {
                final int from=first,to=first+9999;
                tx.executeWithoutResult(status->{
                    jdbc.update("""
                        INSERT INTO inv_balance(tenant_id,company_id,warehouse_id,location_id,sku_id,batch_no,on_hand,inventory_value)
                        SELECT ?,1,((g-1)/500000)+1,0,((g-1)%500000)+1,'-',10,20 FROM generate_series(?,?) g
                        """,T,from,to);
                    jdbc.update("""
                        INSERT INTO inv_transaction(tenant_id,company_id,warehouse_id,location_id,sku_id,batch_no,direction,biz_type,
                        quantity,signed_quantity,signed_value,source_doc_type,source_doc_id,source_line_id,operator_id)
                        SELECT ?,1,((g-1)/500000)+1,0,((g-1)%500000)+1,'-','IN','BENCH',10,10,20,'BENCH',g::text,'1',1
                        FROM generate_series(?,?) g
                        """,T,from,to);
                });
                if(first%100000==1) System.out.println("SCALE seed inventory="+to);
            }
            // 8000 张单/50000 行；两类交易读模型各使用同等规模，事实只在测试专用租户。
            for(String prefix:List.of("pur","sal")) {
                String src=prefix.equals("pur")?"receipt":"shipment";
                String partner=prefix.equals("pur")?"supplier":"customer";
                String quantity=prefix.equals("pur")?"received_qty":"shipped_qty";
                for(int first=1;first<=8000;first+=1000) {
                    jdbc.update("INSERT INTO "+prefix+"_order(tenant_id,company_id,order_no,"+partner+"_id,warehouse_id,org_path,created_by"+(prefix.equals("sal")?",total_amount":"")+") SELECT ?,1,'BENCH-'||g,2,1,'/1/',1"+(prefix.equals("sal")?",20":"")+" FROM generate_series(?,?) g",T,first,first+999);
                    jdbc.update("INSERT INTO "+prefix+"_"+src+"(tenant_id,company_id,"+src+"_no,order_id,warehouse_id,org_path,created_by) SELECT ?,1,o.order_no,o.id,1,'/1/',1 FROM "+prefix+"_order o WHERE tenant_id=? AND order_no IN (SELECT 'BENCH-'||g FROM generate_series(?,?) g)",T,T,first,first+999);
                }
                // 行来源关系只用于规模查询；正向业务行为已由 ReturnE2EIT 等真实用例覆盖。
                jdbc.update("INSERT INTO "+prefix+"_"+src+"_line(tenant_id,"+src+"_id,order_line_id,sku_id,batch_no,"+quantity+",posted_amount,currency) SELECT ?,h.id,g,g,'-',1,20,'CNY' FROM generate_series(1,50000) g JOIN "+prefix+"_"+src+" h ON h.tenant_id=? AND h."+src+"_no='BENCH-'||(((g-1)%8000)+1)",T,T);
            }
            for(String table:List.of("fin_account_receivable","fin_account_payable"))
                jdbc.update("INSERT INTO "+table+"(tenant_id,company_id,partner_id,document_no,currency,amount,source_doc_type,source_doc_id,source_doc_no,order_id,org_path,created_by) SELECT ?,1,2,'BENCH-'||g,'CNY',20,'BENCH',g::text,g::text,g::text,'/1/',1 FROM generate_series(1,8000) g",T);
            jdbc.execute("ANALYZE inv_balance");
            var measurements=new LinkedHashMap<String,Double>();
            for(Kind kind:Kind.values()) {
                long id=reports.start(T,kind);int steps=0;
                while(reports.step(T,id,1000).state().equals("BUILDING")) {
                    if(++steps%100==0) System.out.println("SCALE rebuild kind="+kind+" steps="+steps);
                }
                assertEquals("PUBLISHED",reports.job(T,id).state());
                jdbc.execute("ANALYZE rpt_snapshot_fact");
                long expected=kind==Kind.INVENTORY?2_000_000:kind==Kind.PURCHASE || kind==Kind.SALES?50_000:8_000;
                assertEquals(expected,reports.totals(T,kind).values().rows());
                for(int warm=0;warm<3;warm++) reports.totals(T,kind);
                List<Double> ms=new ArrayList<>();
                for(int i=0;i<30;i++) {
                    long before=System.nanoTime();
                    reports.totals(T,kind);reports.currentPage(T,kind,0,200);
                    if(kind==Kind.AR || kind==Kind.AP) reports.aging(T,kind);
                    ms.add((System.nanoTime()-before)/1_000_000.0);
                }
                Collections.sort(ms);double p95=ms.get((int)Math.ceil(ms.size()*.95)-1);
                measurements.put(kind.name(),p95);
                System.out.println("SCALE P95 kind="+kind+" ms="+p95);
            }
            String json=new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(Map.of(
                    "inventoryBuckets",2_000_000,"distinctSkus",500_000,"documentsPerTradeKind",8000,"linesPerTradeKind",50_000,
                    "samplesPerKind",30,"query","totals + first 200 rows + AR/AP aging","p95Milliseconds",measurements,
                    "elapsedSeconds",(System.nanoTime()-started)/1_000_000_000.0));
            Files.writeString(Path.of("target/report-scale-result.json"),json);
            for(var entry:measurements.entrySet()) assertTrue(entry.getValue()<1000,entry.getKey()+" P95="+entry.getValue()+"ms");
        } finally { clean(); }
    }
    private void clean() {
        for(String table:List.of("rpt_snapshot_current","rpt_snapshot_fact","rpt_snapshot_job","inv_transaction","inv_balance",
                "pur_receipt_line","pur_receipt","pur_order","sal_shipment_line","sal_shipment","sal_order",
                "fin_account_receivable","fin_account_payable"))
            jdbc.update("DELETE FROM "+table+" WHERE tenant_id=?",T);
    }
}
