package com.lrj.erp.inventory.infrastructure;
import com.lrj.erp.inventory.domain.*;
import java.math.BigDecimal;
import org.springframework.stereotype.Repository;

/** 将影响行数转换为领域结果；单据必须存在的更新失败不得吞掉。 */
@Repository
public class MyBatisStockOperationRepository implements StockOperationRepository {
    private final StockOperationMapper mapper;
    public MyBatisStockOperationRepository(StockOperationMapper mapper) { this.mapper=mapper; }
    @Override public long insert(StockOperation o) { return mapper.insert(o); }
    @Override public StockOperation lock(long tenantId,long id) { return mapper.lock(tenantId,id); }
    @Override public boolean transition(StockOperation o,String next) { return mapper.transition(o,next)==1; }
    @Override public boolean freeze(InventoryBucket b,long id) { return mapper.freeze(b,id)==1; }
    @Override public boolean unfreeze(InventoryBucket b,long id) { return mapper.unfreeze(b,id)==1; }
    @Override public boolean transit(InventoryBucket b,BigDecimal delta) { return mapper.transit(b,delta)==1; }
    @Override public void recordCount(StockOperation o,BigDecimal difference) { requireOne(mapper.recordCount(o,difference)); }
    @Override public void recordValue(StockOperation o,BigDecimal value) { requireOne(mapper.recordValue(o,value)); }
    private void requireOne(int changed) { if(changed!=1) throw new IllegalStateException("库存作业并发变更"); }
}
