package com.lrj.erp.it;

import com.lrj.erp.inventory.application.StockPostingService;
import com.lrj.erp.inventory.domain.*;
import com.lrj.erp.kernel.error.DomainException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** 真实 PostgreSQL 验证成本/数量原子性、加权均价及清仓尾差。 */
class InventoryCostIT extends AbstractPostgresIT {
    private static final long TENANT = 270;
    private final InventoryBucket bucket = InventoryBucket.of(TENANT, 1, 1, 1);
    @Autowired StockPostingService posting;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void reset() {
        jdbc.update("DELETE FROM inv_transaction WHERE tenant_id=?", TENANT);
        jdbc.update("DELETE FROM inv_balance WHERE tenant_id=?", TENANT);
    }
    private PostingRequest request(PostingDirection direction, String qty, String value, String id) {
        return new PostingRequest(bucket, direction, new BigDecimal(qty), "COST_TEST", "COST_TEST", id, "1", 1,
                value == null ? null : new BigDecimal(value));
    }
    private void amount(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
    @Test void weightedAverageAndLedger() {
        posting.post(request(PostingDirection.IN, "10", "100", "A"));
        posting.post(request(PostingDirection.IN, "20", "400", "B"));
        posting.post(request(PostingDirection.OUT, "3", null, "C"));
        amount("450", posting.cost(bucket).inventoryValue());
        amount("27", posting.cost(bucket).quantity());
        amount("-50", jdbc.queryForObject("SELECT signed_value FROM inv_transaction WHERE tenant_id=? AND source_doc_id='C'", BigDecimal.class, TENANT));
        amount("450", jdbc.queryForObject("SELECT sum(signed_value) FROM inv_transaction WHERE tenant_id=?", BigDecimal.class, TENANT));
        assertFalse(posting.post(request(PostingDirection.OUT, "3", null, "C")));
        amount("450", posting.cost(bucket).inventoryValue());
    }
    @Test void clearStockAbsorbsRounding() {
        posting.post(request(PostingDirection.IN, "3", "1", "A"));
        for (int i=0;i<3;i++) posting.post(request(PostingDirection.OUT, "1", null, "O"+i));
        amount("0", posting.cost(bucket).inventoryValue());
        amount("0", jdbc.queryForObject("SELECT sum(signed_value) FROM inv_transaction WHERE tenant_id=?", BigDecimal.class, TENANT));
    }
    @Test void missingHistoricCostStaysUnknownUntilExhausted() {
        posting.post(request(PostingDirection.IN, "2", null, "A"));
        posting.post(request(PostingDirection.IN, "1", "10", "B"));
        assertNull(posting.cost(bucket).inventoryValue());
        posting.post(request(PostingDirection.OUT, "1", null, "C"));
        assertNull(posting.cost(bucket).inventoryValue());
        posting.post(request(PostingDirection.OUT, "2", null, "D"));
        amount("0", posting.cost(bucket).inventoryValue());
        posting.post(request(PostingDirection.IN, "1", "7", "E"));
        amount("7", posting.cost(bucket).inventoryValue());
    }
    @Test void insufficientQuantityRollsBackCostAndLedger() {
        posting.post(request(PostingDirection.IN, "1", "10", "A"));
        assertThrows(DomainException.class, () -> posting.post(request(PostingDirection.OUT, "2", null, "B")));
        amount("10", posting.cost(bucket).inventoryValue());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM inv_transaction WHERE tenant_id=?", Integer.class, TENANT));
    }
    @Test void concurrentReceiptsPreserveExactValue() throws Exception {
        try (var pool = Executors.newFixedThreadPool(8)) {
            var futures = new ArrayList<Future<?>>();
            for(int i=1;i<=40;i++) {
                final int n=i;
                futures.add(pool.submit(() -> posting.post(request(PostingDirection.IN, "1", ""+n, "A"+n))));
            }
            for(var future:futures) future.get(30, TimeUnit.SECONDS);
        }
        amount("40", posting.cost(bucket).quantity());
        amount("820", posting.cost(bucket).inventoryValue());
        posting.post(request(PostingDirection.OUT, "20", null, "OUT"));
        amount("410", posting.cost(bucket).inventoryValue());
    }
    @Test void oldWriterInvalidatesCostInsteadOfKeepingStaleValue() {
        posting.post(request(PostingDirection.IN, "2", "10", "A"));
        // 模拟滚动升级期间旧版数量写入；不能继续把 10 当成三件商品的完整成本。
        jdbc.update("UPDATE inv_balance SET on_hand=3 WHERE tenant_id=?", TENANT);
        assertNull(posting.cost(bucket).inventoryValue());
    }
    @Test void invalidPrecisionAndOutgoingOverrideRejected() {
        assertThrows(DomainException.class, () -> posting.post(request(PostingDirection.IN, "1", "0.0000001", "A")));
        assertThrows(DomainException.class, () -> posting.post(request(PostingDirection.OUT, "1", "1", "B")));
        amount("0", posting.cost(bucket).quantity());
    }
}
