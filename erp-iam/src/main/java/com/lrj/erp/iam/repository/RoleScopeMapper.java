package com.lrj.erp.iam.repository;
import org.apache.ibatis.annotations.*;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import java.util.List;
import com.lrj.erp.kernel.context.ActionScope;
/** 范围明细由IAM独立拥有，SQL始终显式绑定tenant，不跨模块查询业务数据。 */
@Mapper @InterceptorIgnore(tenantLine="true",dataPermission="true")
public interface RoleScopeMapper {
    record Reference(long roleId,long id,String path,boolean active){}
    record ScopeRole(long id,String type){}
    record Org(long id,Long parentId,String type,String code,String name,boolean enabled,String path){}
    List<Reference> orgs(@Param("tenant") long tenant,@Param("ids") List<Long> ids);
    List<Reference> companies(@Param("tenant") long tenant,@Param("ids") List<Long> ids);
    List<ScopeRole> forPermission(@Param("tenant") long tenant,@Param("user") long user,@Param("permission") String permission);
    long validReferences(@Param("tenant") long tenant,@Param("ids") List<Long> ids,@Param("companies") boolean companies);
    int clearOrgs(@Param("tenant") long tenant,@Param("id") long id);
    int clearCompanies(@Param("tenant") long tenant,@Param("id") long id);
    int addOrg(@Param("tenant") long tenant,@Param("id") long id,@Param("org") long org);
    int addCompany(@Param("tenant") long tenant,@Param("id") long id,@Param("company") long company);
    int updateType(@Param("tenant") long tenant,@Param("id") long id,@Param("version") long version,@Param("type") String type);
    List<Org> tree(@Param("scope") ActionScope scope,@Param("admin") boolean admin);
}
