package com.lrj.erp.iam.service;
import java.util.List;
/** 管理边界DTO：新增标识和版本使用十进制字符串，旧接口投影保持不变。 */
public final class RoleModels {
    private RoleModels(){}
    public record RoleScope(String type,List<String> orgIds,List<String> companyIds,String warehouseMode,List<String> warehouseIds){}
    public record Role(String id,String version,String code,String name,boolean enabled,boolean protectedRole,
        List<String> permissions,RoleScope scope,String scopeState){
        @com.fasterxml.jackson.annotation.JsonProperty("protected") public boolean protectedRole(){return protectedRole;}
    }
    public record Page<T>(List<T> list,long total,int page,int size){}
    public record Permission(String code,String label,String group){}
    public record Create(String code,String name){}
    public record Update(String expectedVersion,String name,Boolean enabled){}
    public record Permissions(String expectedVersion,List<String> permissions){}
    public record LegacyRole(long id,String code,String name,String data_scope_type){}
}
