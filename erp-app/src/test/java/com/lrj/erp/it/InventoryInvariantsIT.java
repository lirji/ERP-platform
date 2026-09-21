package com.lrj.erp.it;

import com.lrj.erp.inventory.application.ReservationService;
import com.lrj.erp.inventory.application.StockPostingService;
import com.lrj.erp.inventory.domain.*;
import com.lrj.erp.kernel.error.DomainException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P3 出口条件：库存内核的三条不可妥协不变量。
 *
 * <p>全部用真实 PostgreSQL 验证——INV-02 的并发安全来自 UPDATE 的 WHERE 条件，
 * INV-04 的幂等来自唯一索引，二者都是 Mock 会绕过的部分。
 */
@DisplayName("库存不变量")
class InventoryInvariantsIT extends AbstractPostgresIT {

    private static final long TENANT = 300L;
    private static final long COMPANY = 9001L;
    private static final long WAREHOUSE = 7001L;
    private static final long SKU = 5001L;

    @Autowired private StockPostingService posting;
    @Autowired private ReservationService reservations;
    @Autowired private JdbcTemplate jdbc;

    private InventoryBucket bucket;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM inv_transaction WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM inv_reservation WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM inv_balance WHERE tenant_id = ?", TENANT);
        bucket = InventoryBucket.of(TENANT, COMPANY, WAREHOUSE, SKU);
    }

    private PostingRequest req(PostingDirection dir, String qty, String docId, String lineId) {
        return new PostingRequest(bucket, dir, new BigDecimal(qty),
                dir == PostingDirection.IN ? "PURCHASE_IN" : "SALES_OUT",
                dir == PostingDirection.IN ? "PURCHASE_RECEIPT" : "SHIPMENT",
                docId, lineId, 4501L);
    }

    // ===================================================== INV-01

    @Test
    @DisplayName("INV-01：随机 1000 次过账后，余额恒等于流水代数和")
    void 余额等于流水代数和() {
        Random rnd = new Random(20260921L);   // 固定种子：失败可复现
        BigDecimal expected = BigDecimal.ZERO;
        int posted = 0;

        for (int i = 0; i < 1000; i++) {
            boolean in = rnd.nextInt(100) < 60;    // 偏向入库，避免长期可用量为 0
            BigDecimal qty = BigDecimal.valueOf(rnd.nextInt(20) + 1);
            try {
                boolean applied = posting.post(req(
                        in ? PostingDirection.IN : PostingDirection.OUT,
                        qty.toPlainString(), "DOC-" + i, "LINE-1"));
                if (applied) {
                    expected = in ? expected.add(qty) : expected.subtract(qty);
                    posted++;
                }
            } catch (DomainException e) {
                // 可用量不足属于预期业务失败，整笔回滚，不影响不变量
                assertEquals("ERP-INV-3001", e.errorCode().code());
            }
        }

        // 反空跑：必须真的发生过大量过账，否则下面的等式在"都没跑"时也成立
        assertTrue(posted > 500, "实际过账次数过少(" + posted + ")，不变量未被真正施压");

        BigDecimal onHand = posting.balance(bucket).onHand();
        BigDecimal ledger = posting.ledgerSum(bucket);

        assertEquals(0, onHand.compareTo(ledger),
                "INV-01 破裂：余额 %s != 流水代数和 %s".formatted(onHand, ledger));
        assertEquals(0, onHand.compareTo(expected),
                "余额与独立累计值不符：%s != %s".formatted(onHand, expected));
    }

    // ===================================================== INV-02

    @Test
    @DisplayName("INV-02：50 线程并发扣同一桶，无负库存、无超卖")
    void 并发扣减不超卖() throws Exception {
        posting.post(req(PostingDirection.IN, "100", "SEED", "L1"));

        int threads = 50;
        BigDecimal each = new BigDecimal("3");     // 50*3=150 > 100，必然有人失败
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CyclicBarrier startLine = new CyclicBarrier(threads);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger insufficient = new AtomicInteger();

        try {
            List<Future<?>> fs = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                int n = i;
                fs.add(pool.submit(() -> {
                    startLine.await(30, TimeUnit.SECONDS);
                    try {
                        posting.post(req(PostingDirection.OUT, each.toPlainString(),
                                "OUT-" + n, "L1"));
                        ok.incrementAndGet();
                    } catch (DomainException e) {
                        assertEquals("ERP-INV-3001", e.errorCode().code());
                        insufficient.incrementAndGet();
                    }
                    return null;
                }));
            }
            for (Future<?> f : fs) f.get(60, TimeUnit.SECONDS);

            assertEquals(threads, ok.get() + insufficient.get(), "应有 50 次扣减尝试");
            assertEquals(33, ok.get(), "100 / 3 = 33 次成功，第 34 次起可用量不足");

            BigDecimal onHand = posting.balance(bucket).onHand();
            assertEquals(0, onHand.compareTo(new BigDecimal("1")),
                    "100 - 33*3 = 1，实际 " + onHand);
            assertTrue(onHand.signum() >= 0, "绝不允许负库存");

            // 并发之后 INV-01 仍须成立
            assertEquals(0, onHand.compareTo(posting.ledgerSum(bucket)),
                    "并发扣减后余额与流水不一致");
        } finally {
            pool.shutdownNow();
        }
    }

    // ===================================================== INV-04

    @Test
    @DisplayName("INV-04：同一来源行过账 10 次只有一次效果")
    void 重复过账幂等() {
        int applied = 0;
        for (int i = 0; i < 10; i++) {
            if (posting.post(req(PostingDirection.IN, "50", "RECEIPT-1", "LINE-1"))) {
                applied++;
            }
        }
        assertEquals(1, applied, "只有第一次应真实过账");
        assertEquals(0, posting.balance(bucket).onHand().compareTo(new BigDecimal("50")),
                "重复过账不得累加库存");

        Integer txns = jdbc.queryForObject("""
                SELECT count(*) FROM inv_transaction
                WHERE tenant_id = ? AND source_doc_id = 'RECEIPT-1' AND source_line_id = 'LINE-1'
                """, Integer.class, TENANT);
        assertEquals(1, txns, "流水也只应有一条");
    }

    @Test
    @DisplayName("INV-04：并发重复过账同样只有一次效果")
    void 并发重复过账幂等() throws Exception {
        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CyclicBarrier startLine = new CyclicBarrier(threads);
        AtomicInteger applied = new AtomicInteger();

        try {
            List<Future<?>> fs = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                fs.add(pool.submit(() -> {
                    startLine.await(30, TimeUnit.SECONDS);
                    try {
                        if (posting.post(req(PostingDirection.IN, "10", "RACE-DOC", "L1"))) {
                            applied.incrementAndGet();
                        }
                    } catch (Exception ignored) {
                        // 并发下唯一索引冲突可能以包装异常形态冒出；不计为已应用
                    }
                    return null;
                }));
            }
            for (Future<?> f : fs) f.get(60, TimeUnit.SECONDS);

            assertEquals(1, applied.get(), "并发重复过账只应有一次生效");
            assertEquals(0, posting.balance(bucket).onHand().compareTo(new BigDecimal("10")));
        } finally {
            pool.shutdownNow();
        }
    }

    // ===================================================== 可追溯

    @Test
    @DisplayName("每条流水都能反查到来源单据行")
    void 流水可反查来源() {
        posting.post(req(PostingDirection.IN, "20", "PR-2026-001", "LINE-7"));

        Map<String, Object> txn = jdbc.queryForMap("""
                SELECT source_doc_type, source_doc_id, source_line_id, direction, signed_quantity
                FROM inv_transaction WHERE tenant_id = ? AND sku_id = ?
                """, TENANT, SKU);

        assertEquals("PURCHASE_RECEIPT", txn.get("source_doc_type"));
        assertEquals("PR-2026-001", txn.get("source_doc_id"));
        assertEquals("LINE-7", txn.get("source_line_id"));
        assertEquals("IN", txn.get("direction"));
        assertEquals(0, ((BigDecimal) txn.get("signed_quantity")).compareTo(new BigDecimal("20")));
    }

    // ===================================================== 预占链

    @Test
    @DisplayName("预占 → 部分消耗 → 释放剩余：数量链正确，释放不超额")
    void 预占消耗释放链() {
        posting.post(req(PostingDirection.IN, "100", "SEED-R", "L1"));

        reservations.reserve(bucket, "SALES_ORDER", "SO-1", "L1", new BigDecimal("30"));
        assertEquals(0, posting.balance(bucket).available().compareTo(new BigDecimal("70")),
                "预占应减少可用量，但不减少在库量");
        assertEquals(0, posting.balance(bucket).onHand().compareTo(new BigDecimal("100")));

        // 部分出库：消耗 12
        reservations.consume(bucket, "SALES_ORDER", "SO-1", "L1", new BigDecimal("12"));
        posting.post(req(PostingDirection.OUT, "12", "SHIP-1", "L1"));

        var b = posting.balance(bucket);
        assertEquals(0, b.onHand().compareTo(new BigDecimal("88")), "在库 100-12");
        assertEquals(0, b.reserved().compareTo(new BigDecimal("18")), "预占 30-12");
        assertEquals(0, b.available().compareTo(new BigDecimal("70")), "可用 88-18");

        // 取消订单：释放剩余 18
        BigDecimal released = reservations.releaseRemaining(bucket, "SALES_ORDER", "SO-1", "L1");
        assertEquals(0, released.compareTo(new BigDecimal("18")));
        var after = posting.balance(bucket);
        assertEquals(0, after.reserved().compareTo(BigDecimal.ZERO), "预占应归零");
        assertEquals(0, after.available().compareTo(new BigDecimal("88")), "可用回到在库量");
    }

    @Test
    @DisplayName("释放量不得超过未消耗的预占量")
    void 释放不得超额() {
        posting.post(req(PostingDirection.IN, "50", "SEED-X", "L1"));
        reservations.reserve(bucket, "SALES_ORDER", "SO-2", "L1", new BigDecimal("20"));
        reservations.consume(bucket, "SALES_ORDER", "SO-2", "L1", new BigDecimal("15"));

        DomainException ex = assertThrows(DomainException.class,
                () -> reservations.release(bucket, "SALES_ORDER", "SO-2", "L1", new BigDecimal("10")));
        assertEquals("ERP-INV-3002", ex.errorCode().code(),
                "超额释放会把 reserved 扣成负数，让可用量虚高进而超卖");
    }

    @Test
    @DisplayName("可用量不足时预占失败")
    void 可用量不足预占失败() {
        posting.post(req(PostingDirection.IN, "10", "SEED-Y", "L1"));
        DomainException ex = assertThrows(DomainException.class,
                () -> reservations.reserve(bucket, "SALES_ORDER", "SO-3", "L1", new BigDecimal("11")));
        assertEquals("ERP-INV-3001", ex.errorCode().code());
    }

    // ===================================================== 批次维度

    @Test
    @DisplayName("批次是台账主键的一部分：不同批次是不同的桶")
    void 批次独立成桶() {
        InventoryBucket b1 = InventoryBucket.ofBatch(TENANT, COMPANY, WAREHOUSE, SKU, "B2026A");
        InventoryBucket b2 = InventoryBucket.ofBatch(TENANT, COMPANY, WAREHOUSE, SKU, "B2026B");

        posting.post(new PostingRequest(b1, PostingDirection.IN, new BigDecimal("10"),
                "PURCHASE_IN", "PR-B1", "D1", "L1", 4501L));
        posting.post(new PostingRequest(b2, PostingDirection.IN, new BigDecimal("25"),
                "PURCHASE_IN", "PR-B2", "D2", "L1", 4501L));

        assertEquals(0, posting.balance(b1).onHand().compareTo(new BigDecimal("10")));
        assertEquals(0, posting.balance(b2).onHand().compareTo(new BigDecimal("25")));
        assertEquals(0, posting.balance(bucket).onHand().compareTo(BigDecimal.ZERO),
                "无批次桶与批次桶互不混淆");
    }

    @Test
    @DisplayName("过账必须带来源单据，否则拒绝")
    void 无来源单据拒绝过账() {
        PostingRequest bad = new PostingRequest(bucket, PostingDirection.IN,
                new BigDecimal("5"), "ADJUST", null, null, null, 4501L);
        DomainException ex = assertThrows(DomainException.class, () -> posting.post(bad));
        assertEquals("ERP-INV-1001", ex.errorCode().code());
    }
}
