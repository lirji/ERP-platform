package com.lrj.erp.it;

import com.lrj.erp.kernel.masterdata.MasterDataRef;
import com.lrj.erp.masterdata.model.Sku;
import com.lrj.erp.masterdata.service.SkuService;
import com.lrj.erp.kernel.error.DomainException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P2 出口条件 ①②③④：编码唯一（并发）、停用只影响新引用、快照、关键字段锁定。
 */
@DisplayName("主数据规则")
class MasterDataRulesIT extends AbstractPostgresIT {

    private static final long TENANT = 500L;
    /** 跨租户用例使用的第二个租户；setUp 必须一并清理，否则测试不可重复执行。 */
    private static final long OTHER_TENANT = 501L;

    @Autowired private SkuService skuService;
    @Autowired private JdbcTemplate jdbc;

    private long unitId;
    private long otherUnitId;
    private long productId;

    @BeforeEach
    void setUp() {
        // 两个租户都要清：只清 TENANT 会让跨租户用例第二次运行时撞唯一约束，
        // 表现为"第一次绿、重跑红"——这种不可重复的测试比没有测试更糟
        for (long t : new long[]{TENANT, OTHER_TENANT}) {
            jdbc.update("DELETE FROM md_reference WHERE tenant_id = ?", t);
            jdbc.update("DELETE FROM md_sku WHERE tenant_id = ?", t);
            jdbc.update("DELETE FROM md_product WHERE tenant_id = ?", t);
            jdbc.update("DELETE FROM md_unit WHERE tenant_id = ?", t);
        }

        unitId = jdbc.queryForObject("INSERT INTO md_unit (tenant_id, code, name) "
                + "VALUES (?, 'PCS', '个') RETURNING id", Long.class, TENANT);
        otherUnitId = jdbc.queryForObject("INSERT INTO md_unit (tenant_id, code, name) "
                + "VALUES (?, 'BOX', '箱') RETURNING id", Long.class, TENANT);
        productId = jdbc.queryForObject("INSERT INTO md_product (tenant_id, code, name) "
                + "VALUES (?, 'P-1', '复印纸') RETURNING id", Long.class, TENANT);
    }

    private Sku sku(String code, String name) {
        return new Sku(0, TENANT, productId, unitId, code, name, null, null, false, true, 0);
    }

    // ------------------------------------------------------------- ①

    @Test
    @DisplayName("① 编码租户内唯一：50 线程并发建同一编码，只成功一条")
    void 并发建档只成功一条() throws Exception {
        int threads = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CyclicBarrier startLine = new CyclicBarrier(threads);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger dup = new AtomicInteger();

        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    startLine.await(30, TimeUnit.SECONDS);
                    try {
                        skuService.create(sku("SKU-RACE", "并发建档"));
                        ok.incrementAndGet();
                    } catch (DomainException e) {
                        assertEquals("ERP-MD-2001", e.errorCode().code());
                        dup.incrementAndGet();
                    } catch (Exception e) {
                        // 并发下数据库唯一约束也可能以其他包装异常形态冒出，一并计为重复
                        dup.incrementAndGet();
                    }
                    return null;
                }));
            }
            for (Future<?> f : futures) f.get(60, TimeUnit.SECONDS);

            assertEquals(threads, ok.get() + dup.get(), "应当有 50 次建档尝试");
            assertEquals(1, ok.get(), "并发建同一编码必须只成功一条，实际成功 " + ok.get());

            Integer rows = jdbc.queryForObject(
                    "SELECT count(*) FROM md_sku WHERE tenant_id = ? AND code = 'SKU-RACE'",
                    Integer.class, TENANT);
            assertEquals(1, rows, "数据库中只应存在一条");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("① 不同租户可以使用相同编码")
    void 跨租户同码互不影响() {
        skuService.create(sku("SKU-SAME", "租户A的"));
        Long u2 = jdbc.queryForObject("INSERT INTO md_unit (tenant_id, code, name) "
                + "VALUES (?, 'PCS', '个') RETURNING id", Long.class, OTHER_TENANT);
        Long p2 = jdbc.queryForObject("INSERT INTO md_product (tenant_id, code, name) "
                + "VALUES (?, 'P-1', '复印纸') RETURNING id", Long.class, OTHER_TENANT);

        assertDoesNotThrow(() -> skuService.create(
                new Sku(0, OTHER_TENANT, p2, u2, "SKU-SAME", "租户B的", null, null, false, true, 0)),
                "编码唯一是租户内唯一；全局唯一会让 A 租户占用 B 租户的编码");
    }

    // ------------------------------------------------------------- ②

    @Test
    @DisplayName("② 停用的主数据不能被新单据引用，但已有单据完全不受影响")
    void 停用只切断未来引用() {
        long id = skuService.create(sku("SKU-LIFE", "生命周期"));

        // 停用前：老单据成功引用并拿到快照
        MasterDataRef oldRef = skuService.reference(TENANT, id, "PURCHASE_ORDER", "PO-OLD");
        assertEquals("SKU-LIFE", oldRef.code());

        skuService.disable(TENANT, id);

        // 新单据引用被拒
        DomainException ex = assertThrows(DomainException.class,
                () -> skuService.reference(TENANT, id, "PURCHASE_ORDER", "PO-NEW"));
        assertEquals("ERP-MD-3001", ex.errorCode().code());

        // 已有引用记录仍在，老单据的快照仍然有效——停用不得改写已发生的业务事实
        Integer refs = jdbc.queryForObject(
                "SELECT count(*) FROM md_reference WHERE tenant_id = ? AND md_id = ? "
                        + "AND business_id = 'PO-OLD'", Integer.class, TENANT, id);
        assertEquals(1, refs, "停用不得删除或使已有引用失效");
        assertEquals("SKU-LIFE", oldRef.code(), "老单据的快照不随停用改变");
    }

    @Test
    @DisplayName("② 停用的计量单位不能被新 SKU 引用")
    void 停用单位不可被新SKU引用() {
        jdbc.update("UPDATE md_unit SET enabled = FALSE WHERE tenant_id = ? AND id = ?",
                TENANT, otherUnitId);
        Sku s = new Sku(0, TENANT, productId, otherUnitId, "SKU-U", "用停用单位", null, null, false, true, 0);
        DomainException ex = assertThrows(DomainException.class, () -> skuService.create(s));
        assertEquals("ERP-MD-3001", ex.errorCode().code());
    }

    // ------------------------------------------------------------- ③

    @Test
    @DisplayName("③ 改名后历史单据仍显示快照值，而不是新名字")
    void 历史单据显示快照值() {
        long id = skuService.create(sku("SKU-SNAP", "A4 复印纸 70g"));

        MasterDataRef refAtOrderTime = skuService.reference(TENANT, id, "PURCHASE_ORDER", "PO-2026");
        assertEquals("A4 复印纸 70g", refAtOrderTime.name());
        assertEquals("个", refAtOrderTime.unitName());

        // 主数据改名
        Sku current = skuService.get(TENANT, id);
        skuService.updateDescriptive(new Sku(id, TENANT, productId, unitId, "SKU-SNAP",
                "A4 复印纸 80g（改版）", null, null, false, true, current.version()), current.version());

        // 实时值已变
        assertEquals("A4 复印纸 80g（改版）", skuService.get(TENANT, id).name());

        // 但历史单据持有的快照没变——这正是快照存在的理由
        assertEquals("A4 复印纸 70g", refAtOrderTime.name(),
                "历史单据必须显示下单当时的名称；靠 JOIN 取实时值会让历史单据改写自己");

        // 新单据引用时拿到的是新名字
        MasterDataRef refNow = skuService.reference(TENANT, id, "PURCHASE_ORDER", "PO-2027");
        assertEquals("A4 复印纸 80g（改版）", refNow.name());
    }

    // ------------------------------------------------------------- ④

    @Test
    @DisplayName("④ 未被引用时，关键字段可以改")
    void 未被引用可改关键字段() {
        long id = skuService.create(sku("SKU-KEY1", "关键字段"));
        Sku cur = skuService.get(TENANT, id);
        assertDoesNotThrow(() -> skuService.updateKeyFields(
                new Sku(id, TENANT, productId, otherUnitId, "SKU-KEY1-NEW", cur.name(),
                        null, null, false, true, cur.version()), cur.version()));
        assertEquals("SKU-KEY1-NEW", skuService.get(TENANT, id).code());
    }

    @Test
    @DisplayName("④ 被引用后，编码与基本单位不可修改")
    void 被引用后关键字段锁定() {
        long id = skuService.create(sku("SKU-KEY2", "关键字段"));
        skuService.reference(TENANT, id, "PURCHASE_ORDER", "PO-REF");

        Sku cur = skuService.get(TENANT, id);
        DomainException ex = assertThrows(DomainException.class,
                () -> skuService.updateKeyFields(
                        new Sku(id, TENANT, productId, otherUnitId, "SKU-CHANGED", cur.name(),
                                null, null, false, true, cur.version()), cur.version()));
        assertEquals("ERP-MD-3002", ex.errorCode().code());
        @SuppressWarnings("unchecked")
        List<String> locked = (List<String>) ex.details().get("lockedFields");
        assertTrue(locked.contains("baseUnitId"),
                "基本单位必须在锁定之列：改了会让历史库存数量的含义整体改变");

        assertEquals("SKU-KEY2", skuService.get(TENANT, id).code(), "编码不应被改动");
    }

    @Test
    @DisplayName("④ 被引用后，描述字段仍然可以改")
    void 被引用后描述字段仍可改() {
        long id = skuService.create(sku("SKU-KEY3", "旧名"));
        skuService.reference(TENANT, id, "PURCHASE_ORDER", "PO-REF3");
        Sku cur = skuService.get(TENANT, id);

        assertDoesNotThrow(() -> skuService.updateDescriptive(
                new Sku(id, TENANT, productId, unitId, "SKU-KEY3", "新名",
                        "规格A", null, false, true, cur.version()), cur.version()));
        assertEquals("新名", skuService.get(TENANT, id).name());
    }

    @Test
    @DisplayName("乐观锁：版本不匹配时更新必须失败，不得当作成功")
    void 乐观锁冲突不得当成功() {
        long id = skuService.create(sku("SKU-LOCK", "乐观锁"));
        Sku cur = skuService.get(TENANT, id);
        long staleVersion = cur.version() - 1;

        DomainException ex = assertThrows(DomainException.class,
                () -> skuService.updateDescriptive(
                        new Sku(id, TENANT, productId, unitId, "SKU-LOCK", "改名",
                                null, null, false, true, staleVersion), staleVersion));
        assertEquals("ERP-SYS-0005", ex.errorCode().code());
    }

    @Test
    @DisplayName("同一单据重复引用同一主数据不产生多行登记")
    void 重复引用幂等() {
        long id = skuService.create(sku("SKU-IDEM", "幂等"));
        skuService.reference(TENANT, id, "PURCHASE_ORDER", "PO-1");
        skuService.reference(TENANT, id, "PURCHASE_ORDER", "PO-1");

        Integer rows = jdbc.queryForObject(
                "SELECT count(*) FROM md_reference WHERE tenant_id = ? AND md_id = ?",
                Integer.class, TENANT, id);
        assertEquals(1, rows);
    }

    // ------------------------------------------------------------- ⑤

    @Test
    @DisplayName("⑤ 批量导入 1000 行全部合法：全部写入")
    void 批量导入成功() {
        List<Sku> rows = IntStream.rangeClosed(1, 1000)
                .mapToObj(i -> sku("BULK-%04d".formatted(i), "批量商品" + i))
                .toList();

        SkuService.ImportResult result = skuService.importBatch(TENANT, rows);
        assertEquals(1000, result.total());
        assertEquals(1000, result.imported());

        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM md_sku WHERE tenant_id = ? AND code LIKE 'BULK-%'",
                Integer.class, TENANT);
        assertEquals(1000, count);
    }

    @Test
    @DisplayName("⑤ 1000 行中有 1 行非法：整批回滚，一行都不写")
    void 批量导入整批回滚() {
        List<Sku> rows = new ArrayList<>(IntStream.rangeClosed(1, 1000)
                .mapToObj(i -> sku("ROLL-%04d".formatted(i), "批量商品" + i))
                .toList());
        // 第 37 行名称为空 —— 校验失败
        rows.set(36, sku("ROLL-0037", ""));

        SkuService.ImportRejectedException ex = assertThrows(
                SkuService.ImportRejectedException.class,
                () -> skuService.importBatch(TENANT, rows));

        assertEquals(1000, ex.total());
        assertEquals(1, ex.failures().size());
        assertEquals(37, ex.failures().get(0).row(), "必须指出是第几行，用户才好改");

        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM md_sku WHERE tenant_id = ? AND code LIKE 'ROLL-%'",
                Integer.class, TENANT);
        assertEquals(0, count, "整批回滚：部分成功会让调用方无从判断哪些进去了");
    }

    @Test
    @DisplayName("⑤ 多行非法时一次返回全部失败行，而不是遇到第一个就停")
    void 批量导入返回全部失败行() {
        List<Sku> rows = new ArrayList<>(IntStream.rangeClosed(1, 10)
                .mapToObj(i -> sku("MULTI-%02d".formatted(i), "商品" + i))
                .toList());
        rows.set(1, sku("MULTI-02", ""));
        rows.set(4, sku("", "无编码"));
        rows.set(7, sku("MULTI-01", "批内重码"));

        SkuService.ImportRejectedException ex = assertThrows(
                SkuService.ImportRejectedException.class,
                () -> skuService.importBatch(TENANT, rows));

        assertEquals(3, ex.failures().size(),
                "应一次返回全部失败行，让用户改一轮就能全修好，而不是改一行试一次");
        assertEquals(List.of(2, 5, 8), ex.failures().stream().map(f -> f.row()).sorted().toList());
    }
}
