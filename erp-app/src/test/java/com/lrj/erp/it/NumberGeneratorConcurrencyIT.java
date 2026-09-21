package com.lrj.erp.it;

import com.lrj.erp.numbering.service.NumberGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P1 出口条件 ④：并发 50 线程取号无重复、无空洞。
 *
 * <p>必须在真实 PostgreSQL + 真实事务 + 真实并发下验证。纯 Mock 恰恰会绕过
 * 要验证的语义（ON CONFLICT 的行级原子性），证明不了任何事。
 */
@DisplayName("编号中心并发取号")
class NumberGeneratorConcurrencyIT extends AbstractPostgresIT {

    private static final long TENANT = 123L;
    private static final String BIZ_TYPE = "PO";
    private static final LocalDate BIZ_DATE = LocalDate.of(2026, 9, 21);
    private static final int THREADS = 50;

    @Autowired private NumberGenerator generator;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM num_sequence WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM num_rule WHERE tenant_id = ?", TENANT);
        jdbc.update("""
                INSERT INTO num_rule (tenant_id, business_type, prefix, seq_width, enabled)
                VALUES (?, ?, 'PO', 6, TRUE)
                """, TENANT, BIZ_TYPE);
    }

    @Test
    @DisplayName("50 线程并发取号：无重复、无空洞，且确实并发")
    void 并发取号无重复无空洞() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        // 所有线程在同一栅栏上起跑，否则线程池可能把任务串行跑完，测出来的并发是假的
        CyclicBarrier startLine = new CyclicBarrier(THREADS);
        List<Future<String>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < THREADS; i++) {
                futures.add(pool.submit(() -> {
                    startLine.await(30, TimeUnit.SECONDS);
                    return generator.next(TENANT, BIZ_TYPE, BIZ_DATE);
                }));
            }

            List<String> numbers = new ArrayList<>();
            for (Future<String> f : futures) {
                numbers.add(f.get(60, TimeUnit.SECONDS));
            }

            // 反空跑：必须真的拿到 50 个号，否则下面的断言都是在空集合上成立
            assertEquals(THREADS, numbers.size(), "应当取到 50 个单据号");

            // 无重复
            Set<String> distinct = new HashSet<>(numbers);
            assertEquals(THREADS, distinct.size(),
                    "出现重号——并发下编号不唯一是本项目不可接受的缺陷。实际：" + numbers);

            // 无空洞：流水部分应恰好是 1..50
            Set<Integer> seqs = numbers.stream()
                    .map(n -> Integer.parseInt(n.substring(n.length() - 6)))
                    .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
            Set<Integer> expected = IntStream.rangeClosed(1, THREADS).boxed()
                    .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
            assertEquals(expected, seqs, "流水号出现空洞或越界");

            // 格式正确
            numbers.forEach(n -> assertTrue(n.matches("PO20260921\\d{6}"),
                    "单据号格式不符合 PO+yyyyMMdd+6位流水：" + n));

            // 计数器与实际发放数一致
            Long current = jdbc.queryForObject(
                    "SELECT current_value FROM num_sequence WHERE tenant_id=? AND business_type=? AND biz_date=?",
                    Long.class, TENANT, BIZ_TYPE, BIZ_DATE);
            assertEquals((long) THREADS, current, "计数器与实际发放数不一致");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("未配置编号规则时显式失败，不得静默返回一个号")
    void 未配置规则时失败() {
        var ex = assertThrows(com.lrj.erp.kernel.error.DomainException.class,
                () -> generator.next(TENANT, "UNKNOWN_TYPE", BIZ_DATE));
        assertEquals("ERP-NUM-3002", ex.errorCode().code());
    }

    @Test
    @DisplayName("不同业务日期的流水互相独立，各自从 1 开始")
    void 跨日流水独立() {
        String d1 = generator.next(TENANT, BIZ_TYPE, LocalDate.of(2026, 9, 21));
        String d2 = generator.next(TENANT, BIZ_TYPE, LocalDate.of(2026, 9, 22));
        assertTrue(d1.endsWith("000001"), "9/21 首个号应为 000001，实际 " + d1);
        assertTrue(d2.endsWith("000001"), "9/22 应重新从 000001 开始，实际 " + d2);
    }
}
