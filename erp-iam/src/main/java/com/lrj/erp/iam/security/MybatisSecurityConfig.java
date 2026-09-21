package com.lrj.erp.iam.security;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.annotation.DbType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 把租户与数据权限注册为 SQL 层拦截器。
 *
 * <p>顺序有意义：<b>租户在前，数据权限在后</b>。租户是最外层的硬隔离，
 * 任何情况下都必须存在；数据权限是租户内的细分。反过来注册会让数据权限
 * 先改写 SQL，租户条件再套上去，可读性与调试难度都变差。
 *
 * <p>分页拦截器放最后：它需要看到最终的 WHERE 条件才能算对 count。
 */
@Configuration
public class MybatisSecurityConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor(TenantAndDataScopeHandler handler) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(handler));
        interceptor.addInnerInterceptor(new DataPermissionInterceptor(handler));
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
        return interceptor;
    }
}
