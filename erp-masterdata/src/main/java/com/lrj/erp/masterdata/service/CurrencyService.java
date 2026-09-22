package com.lrj.erp.masterdata.service;
import com.lrj.erp.masterdata.repository.CurrencyMapper;
import com.lrj.erp.kernel.error.DomainException;
import com.lrj.erp.kernel.error.SystemErrorCode;
import org.springframework.stereotype.Service;
import java.util.Map;

/** 币种缺失时拒绝财务事实生成，不把未知币种默认为人民币。 */
@Service
public class CurrencyService {
    private final CurrencyMapper mapper;
    public CurrencyService(CurrencyMapper mapper){this.mapper=mapper;}
    /** 读取租户唯一且启用的本位币。 */
    public String requireBase(long tenantId){
        String code=mapper.findBaseCurrency(tenantId);
        if(code==null)throw new DomainException(SystemErrorCode.MALFORMED_BODY,Map.of("reason","请先维护启用的本位币"));
        return code;
    }
}
