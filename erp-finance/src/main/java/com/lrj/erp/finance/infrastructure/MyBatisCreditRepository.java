package com.lrj.erp.finance.infrastructure;
import com.lrj.erp.finance.domain.*;
import java.math.BigDecimal;
import org.springframework.stereotype.Repository;
/** 影响行数为零表示额度不足，交由用例转换业务错误。 */
@Repository
public class MyBatisCreditRepository implements CreditRepository {
    private final CreditMapper mapper;
    public MyBatisCreditRepository(CreditMapper mapper) { this.mapper=mapper; }
    public Credit findSource(long t,String type,String id) { return mapper.findSource(t,type,id); }
    public Credit find(long t,long id) { return mapper.find(t,id); }
    public boolean addCredit(long t,BillType type,long id,BigDecimal amount) { return mapper.addCredit(t,type,id,amount)==1; }
    public long insert(Credit c,long operator) { return mapper.insert(c,operator); }
    public Refund findCommand(long t,String command) { return mapper.findCommand(t,command); }
    public boolean addRefund(long t,long id,BigDecimal amount) { return mapper.addRefund(t,id,amount)==1; }
    public long insertRefund(long t,long id,BigDecimal amount,String currency,String command,long operator) { return mapper.insertRefund(t,id,amount,currency,command,operator); }
}
