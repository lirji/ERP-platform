package com.lrj.erp.app.monitoring;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 只记录慢语句标识与耗时，不打印 SQL 绑定值，避免日志泄露账户和财务载荷。 */
@Component
@Intercepts({@Signature(type=Executor.class,method="query",args={MappedStatement.class,Object.class,RowBounds.class,ResultHandler.class}),
             @Signature(type=Executor.class,method="update",args={MappedStatement.class,Object.class})})
public class SlowQueryInterceptor implements Interceptor {
    private static final Logger LOG=LoggerFactory.getLogger(SlowQueryInterceptor.class);
    private final long threshold;
    public SlowQueryInterceptor(@Value("${erp.monitoring.slow-query-ms:500}") long threshold) {
        if(threshold<1 || threshold>60000) throw new IllegalArgumentException("慢查询阈值须为1–60000ms");this.threshold=threshold;
    }
    @Override public Object intercept(Invocation invocation) throws Throwable {
        long started=System.nanoTime();
        try { return invocation.proceed(); }
        finally {
            long elapsed=(System.nanoTime()-started)/1_000_000;
            if(elapsed>=threshold) LOG.warn("slow query statementId={} elapsedMs={}",((MappedStatement)invocation.getArgs()[0]).getId(),elapsed);
        }
    }
}
