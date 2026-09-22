package com.lrj.erp.procurement.infrastructure;
import com.lrj.erp.procurement.domain.PurchaseReturnRepository;
import java.math.BigDecimal;
import org.springframework.stereotype.Repository;
/** 退货仓储：关键写入检查影响行数。 */
@Repository
public class MyBatisPurchaseReturnRepository implements PurchaseReturnRepository {
    private final PurchaseReturnMapper mapper;
    public MyBatisPurchaseReturnRepository(PurchaseReturnMapper mapper) { this.mapper=mapper; }
    public Source lockSource(long t,long id) { return mapper.lockSource(t,id); }
    public Return lock(long t,long id) { return mapper.lock(t,id); }
    public Return command(long t,String command) { return mapper.command(t,command); }
    public long insert(Return r) { return mapper.insert(r); }
    public boolean addReturned(long t,long id,BigDecimal qty) { return mapper.addReturned(t,id,qty)==1; }
    public boolean transition(Return r,String next) { return mapper.transition(r,next)==1; }
    public void amount(Return r,BigDecimal amount) { if(mapper.amount(r,amount)!=1) throw new IllegalStateException("退货金额写入失败"); }
}
