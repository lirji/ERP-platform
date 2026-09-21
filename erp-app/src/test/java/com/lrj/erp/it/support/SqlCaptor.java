package com.lrj.erp.it.support;

import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.*;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 捕获最终执行的 SQL，供数据权限测试断言。
 *
 * <p>P1 出口条件 ③ 要求断言<b>生成的 SQL</b> 中确实出现 org_path 前缀条件，
 * 而不是只断言返回的行数。理由：行数正确可能只是数据恰好如此——
 * 数据权限失效时，如果库里本来就只有该部门的数据，结果照样"正确"。
 * 只有 SQL 正确才证明机制真的生效。
 */
@Component
@Intercepts(@Signature(type = StatementHandler.class, method = "prepare",
        args = {Connection.class, Integer.class}))
public class SqlCaptor implements Interceptor {

    private final List<String> captured = new CopyOnWriteArrayList<>();

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        StatementHandler handler = (StatementHandler) invocation.getTarget();
        captured.add(handler.getBoundSql().getSql());
        return invocation.proceed();
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) { }

    public void clear() { captured.clear(); }

    public List<String> captured() { return List.copyOf(captured); }

    /** 最近一条包含给定表名的 SQL；没有则返回 null（调用方须断言非 null 以防空跑）。 */
    public String lastSqlContaining(String fragment) {
        for (int i = captured.size() - 1; i >= 0; i--) {
            if (captured.get(i).contains(fragment)) {
                return captured.get(i);
            }
        }
        return null;
    }
}
