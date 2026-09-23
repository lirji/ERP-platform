package com.lrj.erp.iam.repository;
import org.apache.ibatis.annotations.*;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.lrj.erp.iam.service.RoleModels.LegacyRole;
import java.util.List;
/** 管理查询显式限定tenant；租户锁约束管理员配置写入，避免判权后撤权竞态。 */
@Mapper
@InterceptorIgnore(tenantLine="true",dataPermission="true")
public interface RoleManagementMapper {
    record Grant(long roleId,String permission){}
    record Row(long id,long version,String code,String name,boolean enabled,boolean protectedRole,String scopeType){}
    List<LegacyRole> legacy(@Param("tenant") long tenant);
    List<Row> list(@Param("tenant") long tenant,@Param("q") String q,@Param("sort") String sort,@Param("offset") long offset,@Param("size") int size);
    List<Grant> grants(@Param("tenant") long tenant,@Param("ids") List<Long> ids);
    long count(@Param("tenant") long tenant,@Param("q") String q);
    Row find(@Param("tenant") long tenant,@Param("id") long id);
    List<String> permissions(@Param("tenant") long tenant,@Param("id") long id);
    Long lockTenant(@Param("tenant") long tenant);
    boolean isAdmin(@Param("tenant") long tenant,@Param("user") long user);
    Long insert(@Param("tenant") long tenant,@Param("code") String code,@Param("name") String name);
    int update(@Param("tenant") long tenant,@Param("id") long id,@Param("version") long version,@Param("name") String name,@Param("enabled") boolean enabled);
    int bump(@Param("tenant") long tenant,@Param("id") long id,@Param("version") long version);
    int audit(@Param("tenant") long tenant,@Param("id") long id,@Param("actor") long actor,@Param("action") String action,
        @Param("before") String before,@Param("after") String after,@Param("trace") String trace);
    int clearPermissions(@Param("tenant") long tenant,@Param("id") long id);
    int addPermission(@Param("tenant") long tenant,@Param("id") long id,@Param("permission") String permission);
}
