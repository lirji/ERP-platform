package com.lrj.erp.it;

import com.lrj.erp.kernel.statemachine.DocumentStateService;
import com.lrj.erp.kernel.error.DomainException;
import com.lrj.erp.kernel.statemachine.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P1 出口条件 ⑤ 后半：并发状态迁移只有一个成功。
 *
 * <p>用真实 PostgreSQL 验证——并发胜出由 {@code uk_doc_transition_version} 唯一索引决定，
 * 这正是 Mock 会绕过的部分。
 */
@DisplayName("并发状态迁移")
class DocumentStateConcurrencyIT extends AbstractPostgresIT {

    private static final long TENANT = 123L;
    private static final String BIZ_TYPE = "PURCHASE_ORDER";
    private static final int THREADS = 50;

    record Order(boolean creditOk) { }

    private final StateMachine<Order> machine = StandardDocumentStateMachine.<Order>builder().build();

    @Autowired private DocumentStateService service;
    @Autowired private JdbcTemplate jdbc;

    private String businessId;

    @BeforeEach
    void setUp() {
        businessId = "PO-" + UUID.randomUUID();
    }

    @Test
    @DisplayName("50 线程基于同一版本并发迁移，恰好一个成功，其余报 ERP-DOC-2002")
    void 并发迁移只有一个成功() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CyclicBarrier startLine = new CyclicBarrier(THREADS);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger conflicted = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < THREADS; i++) {
                futures.add(pool.submit(() -> {
                    startLine.await(30, TimeUnit.SECONDS);
                    try {
                        service.transition(machine, new Order(true),
                                DocumentState.DRAFT, DocumentEvent.SUBMIT,
                                TENANT, BIZ_TYPE, businessId, "PO20260921000001",
                                0L, 4501L, "trace-concurrent");
                        succeeded.incrementAndGet();
                    } catch (DomainException e) {
                        assertEquals("ERP-DOC-2002", e.errorCode().code(),
                                "并发冲突应报 2002，实际 " + e.errorCode().code());
                        conflicted.incrementAndGet();
                    }
                    return null;
                }));
            }
            for (Future<?> f : futures) {
                f.get(60, TimeUnit.SECONDS);
            }

            // 反空跑：必须确实有 50 次尝试，否则下面的等式在空集合上也成立
            assertEquals(THREADS, succeeded.get() + conflicted.get(),
                    "应当有 50 次迁移尝试");
            assertEquals(1, succeeded.get(),
                    "并发迁移必须恰好一个成功，实际成功 " + succeeded.get());
            assertEquals(THREADS - 1, conflicted.get());

            // 数据库中确实只留下一条流转记录
            Integer rows = jdbc.queryForObject("""
                    SELECT count(*) FROM erp_state_transition
                    WHERE tenant_id = ? AND business_type = ? AND business_id = ?
                    """, Integer.class, TENANT, BIZ_TYPE, businessId);
            assertEquals(1, rows, "同一版本只应留下一条流转记录");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("流转历史可按单据完整追溯，且顺序正确")
    void 流转历史可追溯() {
        Order o = new Order(true);
        service.transition(machine, o, DocumentState.DRAFT, DocumentEvent.SUBMIT,
                TENANT, BIZ_TYPE, businessId, "PO-X", 0L, 4501L, "t1");
        service.transition(machine, o, DocumentState.SUBMITTED, DocumentEvent.START_APPROVAL,
                TENANT, BIZ_TYPE, businessId, "PO-X", 1L, 4501L, "t2");
        service.transition(machine, o, DocumentState.APPROVING, DocumentEvent.APPROVE,
                TENANT, BIZ_TYPE, businessId, "PO-X", 2L, 4502L, "t3");

        var history = service.history(TENANT, BIZ_TYPE, businessId);
        assertEquals(3, history.size());
        assertEquals(List.of("DRAFT", "SUBMITTED", "APPROVING"),
                history.stream().map(h -> h.fromState()).toList());
        assertEquals(List.of("SUBMITTED", "APPROVING", "APPROVED"),
                history.stream().map(h -> h.toState()).toList());
        assertEquals("t3", history.get(2).traceId(), "每次迁移都应留下 traceId 以便串联日志");
    }

    @Test
    @DisplayName("非法迁移在落库之前就被拒绝，不留下脏记录")
    void 非法迁移不留痕() {
        assertThrows(DomainException.class, () ->
                service.transition(machine, new Order(true),
                        DocumentState.DRAFT, DocumentEvent.APPROVE,
                        TENANT, BIZ_TYPE, businessId, "PO-Y", 0L, 4501L, "t"));

        Integer rows = jdbc.queryForObject("""
                SELECT count(*) FROM erp_state_transition
                WHERE tenant_id = ? AND business_id = ?
                """, Integer.class, TENANT, businessId);
        assertEquals(0, rows, "非法迁移不得在流转日志中留下记录");
    }
}
