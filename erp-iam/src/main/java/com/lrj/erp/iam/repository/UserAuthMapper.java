package com.lrj.erp.iam.repository;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 装配 AccessContext 所需的查询。
 *
 * <p>关闭租户/数据权限拦截：这些查询发生在 AccessContext <b>建立之前</b>，
 * 此时线程上还没有上下文，拦截器无从注入；租户条件在 SQL 里显式写出。
 * 这是"先有鸡还是先有蛋"的必然处理，而不是绕过隔离。
 */
@Mapper
@InterceptorIgnore(tenantLine = "true", dataPermission = "true")
public interface UserAuthMapper {

    /** 按外部身份标识（OIDC subject）或用户名定位用户；返回 null 表示无此用户。 */
    UserAuthRecord findUser(@Param("tenantCode") String tenantCode,
                            @Param("username") String username);

    /** 用户经全部角色获得的权限点并集。 */
    List<String> findPermissions(@Param("tenantId") long tenantId, @Param("userId") long userId);

    /**
     * 用户全部角色中<b>最宽</b>的数据范围。
     * 多角色取并集时，范围应当取最宽的那个——取最窄会让加角色反而减少可见数据。
     */
    List<String> findDataScopeTypes(@Param("tenantId") long tenantId, @Param("userId") long userId);

    /** 装配上下文所需的用户信息投影。 */
    record UserAuthRecord(long userId, long tenantId, long companyId, String orgPath,
                          String timezone, boolean userEnabled, boolean tenantEnabled) { }
}
