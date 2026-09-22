package com.lrj.erp.finance.application;
import com.lrj.erp.kernel.finance.SettledCreditQuery;
import com.lrj.erp.finance.domain.FinanceRepository;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;

/** 只读本模块财务事实，向销售提供权威查询；不反向写销售表。 */
@Service
public class SettledCreditService implements SettledCreditQuery {
    private final FinanceRepository mapper;
    public SettledCreditService(FinanceRepository mapper){this.mapper=mapper;}
    /** 不缓存资金决策所依赖的核销净额，反核销提交后下一次查询即可看到。 */
    @Override public BigDecimal netSettled(long tenantId,long customerId){return mapper.netSettled(tenantId,customerId);}
}
