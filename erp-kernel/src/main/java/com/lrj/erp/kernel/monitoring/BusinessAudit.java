package com.lrj.erp.kernel.monitoring;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.*;
import org.slf4j.*;
/** 只在事务真正提交后输出成功审计日志，避免回滚分支留下虚假的成功记录。 */
@Component
public class BusinessAudit {
    private static final Logger LOG=LoggerFactory.getLogger("ERP_BUSINESS_AUDIT");
    /** 日志字段不含业务载荷、账户或原因正文；外部标识去控制字符并限长。 */
    public void record(long tenantId,long userId,String businessType,String businessId,String documentNo,String action) {
        String trace=safe(MDC.get("traceId"));
        Runnable log=()->LOG.info("tenantId={} userId={} businessType={} businessId={} documentNo={} traceId={} action={}",
                tenantId,userId,safe(businessType),safe(businessId),safe(documentNo),trace,safe(action));
        if(TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { log.run(); }
            });
        } else log.run();
    }
    private static String safe(String value) {
        if(value==null) return "-";
        String result=value.replaceAll("[\\p{Cntrl}\\s]","_");
        return result.substring(0,Math.min(128,result.length()));
    }
}
