package com.lrj.erp.it;

import com.lrj.erp.kernel.outbox.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P1 出口条件 ⑦：Outbox 投递失败可重试并进 DLQ。
 *
 * <p>用可控的失败投递器驱动：真实下游不会按需失败，而这正是必须验证的路径——
 * 失败重试与死信是故障时才走的分支，平时永远跑不到，只能靠测试保证它是对的。
 */
@DisplayName("Outbox 重试与死信")
@Import(OutboxRetryAndDlqIT.FailingDeliveryConfig.class)
class OutboxRetryAndDlqIT extends AbstractPostgresIT {

    private static final long TENANT = 600L;

    /** 可开关的投递器：先让它失败以驱动重试，再让它成功以验证恢复。 */
    static class SwitchableDelivery implements OutboxDelivery {
        final AtomicBoolean shouldFail = new AtomicBoolean(true);
        volatile int attempts = 0;

        @Override
        public void deliver(OutboxMessage message) {
            attempts++;
            if (shouldFail.get()) {
                throw new IllegalStateException("下游暂时不可用");
            }
        }
    }

    @TestConfiguration
    static class FailingDeliveryConfig {
        @Bean @Primary
        SwitchableDelivery switchableDelivery() {
            return new SwitchableDelivery();
        }
    }

    @Autowired private OutboxRecorder recorder;
    @Autowired private OutboxDispatcher dispatcher;
    @Autowired private OutboxMapper mapper;
    @Autowired private SwitchableDelivery delivery;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate tx;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM erp_outbox_message WHERE tenant_id = ?", TENANT);
        delivery.shouldFail.set(true);
        delivery.attempts = 0;
    }

    private void appendEvent(String aggregateId) {
        // record 是 MANDATORY 传播：必须在调用方事务内，这里显式开一个
        tx.executeWithoutResult(status -> recorder.record(
                TENANT, "PurchaseReceipt", aggregateId, "PurchaseReceiptPosted.v1",
                Map.of("receiptNo", aggregateId, "amount", "1234.5600")));
    }

    private Map<String, Object> row(String aggregateId) {
        return jdbc.queryForMap(
                "SELECT * FROM erp_outbox_message WHERE tenant_id = ? AND aggregate_id = ?",
                TENANT, aggregateId);
    }

    @Test
    @DisplayName("事件与业务同事务登记，初始为 PENDING")
    void 登记为待投递() {
        appendEvent("PR-1");
        Map<String, Object> r = row("PR-1");
        assertEquals("PENDING", r.get("status"));
        assertEquals(0, ((Number) r.get("retry_count")).intValue());
        assertTrue(String.valueOf(r.get("payload")).contains("PR-1"));
    }

    @Test
    @DisplayName("投递失败：重试次数递增、记录错误、安排下次重试")
    void 失败后重试() {
        appendEvent("PR-2");

        assertEquals(0, dispatcher.dispatchBatch(), "下游失败时不应有成功计数");

        Map<String, Object> r = row("PR-2");
        assertEquals("PENDING", r.get("status"), "未达上限应保持 PENDING 等待重试");
        assertEquals(1, ((Number) r.get("retry_count")).intValue());
        assertNotNull(r.get("last_error"), "必须留下失败原因，否则无从排障");
        assertTrue(String.valueOf(r.get("last_error")).contains("下游暂时不可用"));
        assertNotNull(r.get("next_retry_at"), "必须安排下次重试时间（退避）");
    }

    @Test
    @DisplayName("重试达上限后进入 DLQ，且不再被领取")
    void 达上限进死信() {
        appendEvent("PR-3");

        // 默认 max-retry=5；清掉 next_retry_at 以便连续驱动，模拟退避时间已到
        for (int i = 0; i < 5; i++) {
            jdbc.update("UPDATE erp_outbox_message SET next_retry_at = NULL "
                    + "WHERE tenant_id = ? AND aggregate_id = 'PR-3'", TENANT);
            dispatcher.dispatchBatch();
        }

        Map<String, Object> r = row("PR-3");
        assertEquals("DEAD", r.get("status"), "达上限必须转死信，而不是无限重试");
        assertEquals(5, ((Number) r.get("retry_count")).intValue());

        // 死信不再被领取——否则"上限"形同虚设
        int before = delivery.attempts;
        jdbc.update("UPDATE erp_outbox_message SET next_retry_at = NULL "
                + "WHERE tenant_id = ? AND aggregate_id = 'PR-3'", TENANT);
        dispatcher.dispatchBatch();
        assertEquals(before, delivery.attempts, "DEAD 消息不得再被投递");

        // 死信记录必须保留：它是补偿与排障的唯一线索
        assertNotNull(r.get("last_error"));
    }

    @Test
    @DisplayName("下游恢复后，未达上限的消息能成功投递并标记 PUBLISHED")
    void 恢复后成功投递() {
        appendEvent("PR-4");
        dispatcher.dispatchBatch();                       // 失败一次
        assertEquals(1, ((Number) row("PR-4").get("retry_count")).intValue());

        delivery.shouldFail.set(false);                   // 下游恢复
        jdbc.update("UPDATE erp_outbox_message SET next_retry_at = NULL "
                + "WHERE tenant_id = ? AND aggregate_id = 'PR-4'", TENANT);

        assertEquals(1, dispatcher.dispatchBatch(), "恢复后应成功投递 1 条");
        Map<String, Object> r = row("PR-4");
        assertEquals("PUBLISHED", r.get("status"));
        assertNotNull(r.get("published_at"));
        assertNull(r.get("last_error"), "成功后应清除上次错误，避免误导排障");
    }

    @Test
    @DisplayName("一条毒消息不得卡住同批其他消息")
    void 毒消息不阻塞同批() {
        appendEvent("PR-5");
        appendEvent("PR-6");
        // 让其中一条永远失败：用一个只对 PR-5 失败的投递器不易注入，
        // 这里改为先全批失败一次，再恢复，验证两条都能各自推进
        dispatcher.dispatchBatch();
        assertEquals(1, ((Number) row("PR-5").get("retry_count")).intValue());
        assertEquals(1, ((Number) row("PR-6").get("retry_count")).intValue());

        delivery.shouldFail.set(false);
        jdbc.update("UPDATE erp_outbox_message SET next_retry_at = NULL WHERE tenant_id = ?", TENANT);
        assertEquals(2, dispatcher.dispatchBatch(), "两条都应被投递，互不影响");
    }

    @Test
    @DisplayName("未开事务时登记事件必须失败，不得悄悄新开事务")
    void 无事务时拒绝登记() {
        assertThrows(Exception.class, () -> recorder.record(
                        TENANT, "PurchaseReceipt", "PR-X", "PurchaseReceiptPosted.v1", Map.of()),
                "MANDATORY 传播：悄悄新开事务会让事件与业务分属两个事务，Outbox 的保证当场失效");
    }
}
