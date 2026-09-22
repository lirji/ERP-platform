package com.lrj.erp.it;
import com.lrj.erp.app.ErpApplication;
import com.lrj.erp.iam.service.EmployeeService;
import com.lrj.erp.masterdata.service.SkuService;
import com.lrj.erp.masterdata.model.Sku;
import com.lrj.erp.kernel.masterdata.MasterDataRef;
import com.lrj.erp.procurement.application.PurchaseOrderService;
import com.lrj.erp.sales.application.SalesOrderService;
import com.lrj.erp.finance.application.SettlementService;
import com.lrj.erp.finance.domain.BillType;
import com.lrj.erp.inventory.application.StockPostingService;
import com.lrj.erp.inventory.domain.InventoryBucket;
import com.lrj.erp.kernel.outbox.OutboxMessage;
import com.lrj.erp.reporting.application.ReportSnapshotService;
import com.lrj.erp.kernel.reporting.ReportSource.Kind;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.slf4j.MDC;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** 显式演示命令，不匹配默认测试命名。仅脚本指定执行；不创建新中间件、不运行迁移。 */
@SpringBootTest(classes=ErpApplication.class,properties={"spring.flyway.enabled=false","erp.outbox.scheduling-enabled=false","erp.monitoring.enabled=false"})
class DemoDataSeed {
    static final long T=990001;
    static final String CODE="ERP_DEMO_P11";
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired TransactionTemplate tx;
    @Autowired EmployeeService employees;
    @Autowired SkuService skus;
    @Autowired PurchaseOrderService purchase;
    @Autowired SalesOrderService sales;
    @Autowired SettlementService finance;
    @Autowired StockPostingService posting;
    @Autowired ApplicationEventPublisher events;
    @Autowired ReportSnapshotService reports;
    private static final List<String> OWNED=List.of("rpt_snapshot_current","rpt_snapshot_fact","rpt_snapshot_job",
            "fin_refund_record","fin_credit_adjustment","fin_settlement_record","fin_receipt","fin_payment","fin_account_receivable","fin_account_payable",
            "pur_return","sal_return","pur_receipt_line","pur_receipt","pur_order_line","pur_order","pur_request",
            "sal_shipment_line","sal_shipment","sal_order_line","sal_order","sal_credit_account",
            "inv_stock_operation","inv_reservation","inv_transaction","inv_balance","doc_relation","erp_state_transition","apr_instance","erp_outbox_message",
            "md_reference","md_sku","md_product","md_category","md_unit","md_supplier","md_customer","md_warehouse_location","md_warehouse",
            "md_currency","md_tax_rate","md_settlement_method","num_sequence","num_rule",
            "iam_employee","iam_user_role","iam_role_permission","iam_role","iam_user","iam_org");
    record Ids(long company,long dept,long user,long supplier,long customer,long sku,long warehouse) {}
    BigDecimal bd(String s) { return new BigDecimal(s); }
    long insert(String sql,Object... args) { return Objects.requireNonNull(jdbc.queryForObject(sql,Long.class,args)); }
    long count(String table) { return jdbc.queryForObject("SELECT count(*) FROM "+table+" WHERE tenant_id=?",Long.class,T); }
    long id(String table) { return jdbc.queryForObject("SELECT id FROM "+table+" WHERE tenant_id=? ORDER BY id LIMIT 1",Long.class,T); }
    @Test void command() throws Exception {
        String action=System.getenv("ERP_SEED_ACTION");
        assertTrue(Set.of("init","verify","cleanup").contains(Objects.requireNonNull(action,"请通过 test-data 脚本显式执行")));
        assertFalse(jdbc.queryForObject("SELECT current_database()",String.class).toLowerCase(Locale.ROOT).matches(".*(prod|online).*"));
        // 单独连接持会话锁，串行同一演示租户的命令，不阻塞其他租户业务。
        try(Connection lock=dataSource.getConnection();var statement=lock.createStatement()) {
            statement.execute("SELECT pg_advisory_lock(990001)");
            try {
                guard();MDC.put("traceId","demo-"+UUID.randomUUID());
                if(action.equals("cleanup")) { cleanup();return; }
                if(action.equals("init")) {
                    // 编号使用独立事务；先提交编号规则，业务事务才能读取配置。
                    Ids ids=tx.execute(status->foundation());
                    tx.executeWithoutResult(status->orders(ids));
                    dispatchOwn();settle();dispatchOwn();
                    for(Kind kind:Kind.values()) {
                        Integer exists=jdbc.queryForObject("SELECT count(*) FROM rpt_snapshot_current WHERE tenant_id=? AND kind=?",Integer.class,T,kind.code());
                        if(exists==0) {
                            long job=reports.start(T,kind);
                            while(reports.step(T,job,1000).state().equals("BUILDING")) { }
                            assertEquals("PUBLISHED",reports.job(T,job).state());
                        }
                    }
                }
                verify();
                System.out.println("TEST_DATA action="+action+" tenant="+T+" DATA_VERIFY=PASS APP_VERIFY=PASS_INTERNAL_SERVICES HTTP_BUSINESS=NOT_IMPLEMENTED");
            } finally { MDC.remove("traceId");statement.execute("SELECT pg_advisory_unlock(990001)"); }
        }
    }
    private void guard() {
        var rows=jdbc.queryForList("SELECT code FROM iam_tenant WHERE id=?",String.class,T);
        if(!rows.isEmpty()) assertEquals(CODE,rows.getFirst(),"保留ID被其他租户占用，停止且不修改数据");
        var ids=jdbc.queryForList("SELECT id FROM iam_tenant WHERE code=?",Long.class,CODE);
        if(!ids.isEmpty()) assertEquals(T,ids.getFirst(),"租户标记对应ID不同，停止");
        if(rows.isEmpty()) for(String table:OWNED) assertEquals(0,count(table),"保留租户ID存在未标记数据："+table);
    }
    private Ids foundation() {
        jdbc.update("INSERT INTO iam_tenant(id,code,name) VALUES (?,?,'ERP本地演示租户') ON CONFLICT DO NOTHING",T,CODE);
        long company=insert("INSERT INTO iam_org(tenant_id,code,name,org_type,org_path) VALUES (?,'DEMO_COMP','演示公司','COMPANY','/demo/') ON CONFLICT(tenant_id,code) DO UPDATE SET code=excluded.code RETURNING id",T);
        jdbc.update("UPDATE iam_org SET org_path=? WHERE tenant_id=? AND id=?","/"+company+"/",T,company);
        long dept=insert("INSERT INTO iam_org(tenant_id,parent_id,code,name,org_type,org_path) VALUES (?,?,'DEMO_DEPT','演示业务部','DEPT','/demo/dept/') ON CONFLICT(tenant_id,code) DO UPDATE SET code=excluded.code RETURNING id",T,company);
        String orgPath="/"+company+"/"+dept+"/";
        jdbc.update("UPDATE iam_org SET org_path=? WHERE tenant_id=? AND id=?",orgPath,T,dept);
        long user=insert("INSERT INTO iam_user(tenant_id,company_id,org_id,username,display_name) VALUES (?,?,?,'demo_operator','演示操作员') ON CONFLICT(tenant_id,username) DO UPDATE SET username=excluded.username RETURNING id",T,company,dept);
        long employee=employees.enroll(T,company,dept,"DEMO_E001","演示员工",user);employees.bindUser(T,employee,user,user);
        long role=insert("INSERT INTO iam_role(tenant_id,code,name,data_scope_type) VALUES (?,'DEMO_OPERATOR','演示角色','ALL') ON CONFLICT(tenant_id,code) DO UPDATE SET code=excluded.code RETURNING id",T);
        jdbc.update("INSERT INTO iam_user_role(tenant_id,user_id,role_id) VALUES (?,?,?) ON CONFLICT DO NOTHING",T,user,role);
        jdbc.update("INSERT INTO iam_role_permission(tenant_id,role_id,permission) VALUES (?,?,'iam:role:read') ON CONFLICT DO NOTHING",T,role);
        long unit=insert("INSERT INTO md_unit(tenant_id,code,name) VALUES (?,'DEMO_PCS','件') ON CONFLICT(tenant_id,code) DO UPDATE SET code=excluded.code RETURNING id",T);
        long category=insert("INSERT INTO md_category(tenant_id,code,name) VALUES (?,'DEMO_CAT','演示分类') ON CONFLICT(tenant_id,code) DO UPDATE SET code=excluded.code RETURNING id",T);
        long product=insert("INSERT INTO md_product(tenant_id,category_id,code,name) VALUES (?,?,'DEMO_PRODUCT','演示商品') ON CONFLICT(tenant_id,code) DO UPDATE SET code=excluded.code RETURNING id",T,category);
        if(count("md_sku")==0) skus.create(new Sku(0,T,product,unit,"DEMO_SKU","演示标准件","标准规格",null,true,true,0));
        long sku=id("md_sku");
        long supplier=insert("INSERT INTO md_supplier(tenant_id,code,name) VALUES (?,'DEMO_SUPPLIER','演示供应商') ON CONFLICT(tenant_id,code) DO UPDATE SET code=excluded.code RETURNING id",T);
        long customer=insert("INSERT INTO md_customer(tenant_id,code,name,credit_limit) VALUES (?,'DEMO_CUSTOMER','演示客户',10000) ON CONFLICT(tenant_id,code) DO UPDATE SET code=excluded.code RETURNING id",T);
        long warehouse=insert("INSERT INTO md_warehouse(tenant_id,company_id,code,name) VALUES (?,?,'DEMO_WH','演示仓库') ON CONFLICT(tenant_id,code) DO UPDATE SET code=excluded.code RETURNING id",T,company);
        jdbc.update("INSERT INTO md_warehouse_location(tenant_id,warehouse_id,code,name,enabled) VALUES (?,?,'DEMO_A01','演示库位（未启用）',FALSE) ON CONFLICT DO NOTHING",T,warehouse);
        jdbc.update("INSERT INTO md_currency(tenant_id,code,name,is_base) VALUES (?,'CNY','人民币',TRUE) ON CONFLICT DO NOTHING",T);
        for(String code:List.of("PO","IN","SO","OUT","AR","AP","RCV","PAY","CREDIT","PR","SR","STOCK"))
            jdbc.update("INSERT INTO num_rule(tenant_id,business_type,prefix,seq_width) VALUES (?,?,?,6) ON CONFLICT DO NOTHING",T,code,code);
        return new Ids(company,dept,user,supplier,customer,sku,warehouse);
    }
    private void orders(Ids x) {
        String path="/"+x.company()+"/"+x.dept()+"/";
        MasterDataRef sku=new MasterDataRef(x.sku(),"DEMO_SKU","演示标准件","件");
        if(count("pur_order")==0) {
            long po=purchase.createOrder(T,x.company(),x.supplier(),x.warehouse(),new MasterDataRef(x.supplier(),"DEMO_SUPPLIER","演示供应商",null),
                    List.of(new PurchaseOrderService.NewLine(x.sku(),sku,bd("20"),bd("10"))),path,x.user());
            skus.reference(T,x.sku(),"PURCHASE_ORDER",""+po);
            purchase.approve(T,po,purchase.submitForApproval(T,po,x.user()),x.user());
            purchase.receive(T,po,List.of(new PurchaseOrderService.ReceiptLine(purchase.lines(T,po).getFirst().id(),"DEMO_BATCH",bd("20"))),x.user());
        }
        if(count("sal_order")==0) {
            long so=sales.createOrder(T,x.company(),x.customer(),x.warehouse(),new MasterDataRef(x.customer(),"DEMO_CUSTOMER","演示客户",null),bd("10000"),null,
                    List.of(new SalesOrderService.NewLine(x.sku(),sku,"DEMO_BATCH",bd("5"),bd("25"))),path,x.user());
            skus.reference(T,x.sku(),"SALES_ORDER",""+so);
            sales.approve(T,so,sales.submitForApproval(T,so,x.user()),x.user());sales.reserveStock(T,so);
            long shipment=sales.ship(T,so,List.of(new SalesOrderService.ShipLine(sales.lines(T,so).getFirst().id(),bd("5"))),x.user());
            sales.deliver(T,shipment);sales.sign(T,shipment);
        }
    }
    private void dispatchOwn() {
        // 只发布本工具租户事件；不调用跨租户的后台 dispatchBatch。
        for(int pass=0;pass<10;pass++) {
            var pending=jdbc.query("SELECT id,aggregate_type,aggregate_id,event_type,payload::text,retry_count FROM erp_outbox_message WHERE tenant_id=? AND status='PENDING' ORDER BY id LIMIT 100",
                    (rs,n)->new OutboxMessage(rs.getLong(1),T,rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),"PENDING",rs.getInt(6),null,null),T);
            if(pending.isEmpty()) return;
            for(var message:pending) {
                events.publishEvent(message);
                jdbc.update("UPDATE erp_outbox_message SET status='PUBLISHED',published_at=clock_timestamp(),last_error=NULL WHERE tenant_id=? AND id=? AND status='PENDING'",T,message.id());
            }
        }
        throw new IllegalStateException("演示事件未排空，请检查后恢复；未处理其他租户消息");
    }
    private void settle() {
        long operator=id("iam_user");
        for(BillType type:BillType.values()) {
            long billId=id(type==BillType.AR?"fin_account_receivable":"fin_account_payable");
            var bill=finance.bill(T,type,billId);
            long cash=finance.recordCash(T,type,billId,bill.amount(),bill.currency(),"DEMO_"+type.code(),operator);
            if(bill.writtenOffAmount().signum()==0) finance.apply(T,type,billId,cash,bill.amount(),operator);
            else assertEquals(0,bill.amount().compareTo(bill.writtenOffAmount()),"演示账已被部分修改，不覆盖");
        }
    }
    private void verify() {
        guard();
        for(String table:List.of("iam_employee","iam_user","iam_role","md_supplier","md_customer","md_product","md_sku","md_warehouse","md_warehouse_location",
                "pur_order","pur_receipt","sal_order","sal_shipment","fin_account_receivable","fin_account_payable","fin_receipt","fin_payment")) assertEquals(1,count(table),table);
        assertEquals(2,count("iam_org"));assertEquals(2,count("fin_settlement_record"));
        var bucket=InventoryBucket.ofBatch(T,id("iam_org"),id("md_warehouse"),id("md_sku"),"DEMO_BATCH");
        assertEquals(0,bd("15").compareTo(posting.balance(bucket).onHand()));
        assertEquals(0,bd("15").compareTo(posting.ledgerSum(bucket)));
        assertEquals(0,bd("150").compareTo(posting.cost(bucket).inventoryValue()));
        for(var type:BillType.values()) {
            var bill=finance.bill(T,type,id(type==BillType.AR?"fin_account_receivable":"fin_account_payable"));
            assertEquals(0,bill.amount().compareTo(bill.paidAmount()));assertEquals(0,bill.amount().compareTo(bill.writtenOffAmount()));
        }
        assertEquals(5,count("rpt_snapshot_current"));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM erp_outbox_message WHERE tenant_id=? AND status!='PUBLISHED'",Integer.class,T));
    }
    private void cleanup() {
        if(jdbc.queryForObject("SELECT count(*) FROM iam_tenant WHERE id=? AND code=?",Integer.class,T,CODE)==0) {
            System.out.println("TEST_DATA cleanup=ALREADY_ABSENT tenant="+T);return;
        }
        dispatchOwn();
        tx.executeWithoutResult(status->{
            // 等待本租户在途消息释放行锁，避免背景消费者在清理后留下派生记录。
            jdbc.queryForList("SELECT id FROM erp_outbox_message WHERE tenant_id=? FOR UPDATE",T);
            for(String table:OWNED) jdbc.update("DELETE FROM "+table+" WHERE tenant_id=?",T);
            assertEquals(1,jdbc.update("DELETE FROM iam_tenant WHERE id=? AND code=?",T,CODE));
        });
        for(String table:OWNED) assertEquals(0,count(table),table);
        System.out.println("TEST_DATA cleanup=PASS tenant="+T+" otherTenants=UNTOUCHED");
    }
}
