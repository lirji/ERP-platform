package com.lrj.erp.iam.service;
import com.lrj.erp.iam.repository.RoleManagementMapper;
import com.lrj.erp.iam.security.*;
import com.lrj.erp.kernel.command.HttpCommandService;
import com.lrj.erp.kernel.context.AccessContextHolder;
import com.lrj.erp.kernel.error.*;
import com.lrj.erp.kernel.monitoring.BusinessAudit;
import org.springframework.stereotype.Service;
import java.util.*;
import static com.lrj.erp.iam.service.RoleModels.*;
/** 普通角色管理用例；受保护管理员的生命周期仅由受控运维入口处理。 */
@Service
public class RoleManagementService {
    private final RoleManagementMapper mapper;private final PermissionChecker checker;private final HttpCommandService commands;
    private final com.fasterxml.jackson.databind.ObjectMapper json;private final PermissionCatalog catalog;private final BusinessAudit audit;
    public RoleManagementService(RoleManagementMapper mapper,PermissionChecker checker,HttpCommandService commands,PermissionCatalog catalog,BusinessAudit audit,com.fasterxml.jackson.databind.ObjectMapper json){
        this.mapper=mapper;this.checker=checker;this.commands=commands;this.catalog=catalog;this.audit=audit;this.json=json;
    }
    /** 旧数组响应的字段与顺序兼容，仅把SQL移回模块持久化层。 */
    public List<LegacyRole> legacy(){checker.requireAll("iam:role:read");return mapper.legacy(tenant());}
    /** 固定目录不返回受保护权限，普通角色不可通过列表复制获得管理员能力。 */
    public List<Permission> permissions(){checker.requireAll("iam:role:read");return catalog.entries();}
    /** 有界分页，排序列由XML允许列表选择；不插入客户端SQL。 */
    public Page<Role> directory(int page,int size,String q,String sort){
        checker.requireAll("iam:role:read");
        if(page<1||size<1||size>200)throw new DomainException(SystemErrorCode.INVALID_PAGINATION);
        if(q!=null&&q.length()>128)throw bad();
        if(!Set.of("code","name","id").contains(sort))throw new DomainException(SystemErrorCode.INVALID_SORT_FIELD);
        var rows=mapper.list(tenant(),q,sort,(long)(page-1)*size,size);
        var grants=rows.isEmpty()?Map.<Long,List<String>>of():mapper.grants(tenant(),rows.stream().map(RoleManagementMapper.Row::id).toList()).stream()
            .collect(java.util.stream.Collectors.groupingBy(RoleManagementMapper.Grant::roleId,java.util.stream.Collectors.mapping(RoleManagementMapper.Grant::permission,java.util.stream.Collectors.toList())));
        return new Page<>(rows.stream().map(r->view(r,grants.getOrDefault(r.id(),List.of()))).toList(),mapper.count(tenant(),q),page,size);
    }
    /** 详情显式tenant过滤，跨租户与不存在均为404。 */
    public Role detail(long id){checker.requireAll("iam:role:read");return view(required(id));}
    /** 新建默认SELF、无功能权限；同一幂等键只产生一次效果。 */
    public Role create(String key,Create input){
        text(input.code(),64);text(input.name(),128);
        return commands.execute("POST /iam/roles",key,input,201,Role.class,this::authorize,()->{
            Long id=mapper.insert(tenant(),input.code(),input.name());
            if(id==null)throw new DomainException(SystemErrorCode.UNIQUE_VIOLATION);
            return record(id,"CREATE",null);
        });
    }
    /** 更新名称和启停必须匹配版本，不允许更新受保护角色。 */
    public Role update(long id,String key,Update input){
        text(input.name(),128);if(input.enabled()==null)throw bad();long version=version(input.expectedVersion());
        return commands.execute("PUT /iam/roles/"+id,key,input,200,Role.class,()->{authorize();mutable(id);},()->{
            Role before=view(required(id));
            if(mapper.update(tenant(),id,version,input.name(),input.enabled())!=1)throw new DomainException(SystemErrorCode.OPTIMISTIC_LOCK);
            return record(id,"UPDATE",before);
        });
    }
    /** 权限替换与角色版本递增原子提交；不接受客户端自定义权限。 */
    public Role assignPermissions(long id,String key,Permissions input){
        long version=version(input.expectedVersion());
        if(input.permissions()==null||input.permissions().size()>200||input.permissions().stream().anyMatch(p->!catalog.contains(p)))throw bad();
        var normalized=new Permissions(input.expectedVersion(),input.permissions().stream().distinct().sorted().toList());
        return commands.execute("PUT /iam/roles/"+id+"/permissions",key,normalized,200,Role.class,()->{authorize();mutable(id);},()->{
            Role before=view(required(id));
            if(mapper.bump(tenant(),id,version)!=1)throw new DomainException(SystemErrorCode.OPTIMISTIC_LOCK);
            mapper.clearPermissions(tenant(),id);normalized.permissions().forEach(p->mapper.addPermission(tenant(),id,p));
            return record(id,"PERMISSIONS",before);
        });
    }
    private void authorize(){
        checker.requireAll("iam:role:write","iam:security:admin");
        // 管理配置当前使用租户行锁串行化，事务最多10秒；撤权/重放都在锁内重读身份。
        if(mapper.lockTenant(tenant())==null||!mapper.isAdmin(tenant(),AccessContextHolder.require().userId()))throw new DomainException(AuthErrorCode.PERMISSION_DENIED);
    }
    private void mutable(long id){if(required(id).protectedRole())throw new DomainException(AuthErrorCode.PROTECTED_GRANT);}
    private RoleManagementMapper.Row required(long id){var row=mapper.find(tenant(),id);if(row==null)throw new DomainException(AuthErrorCode.OUT_OF_DATA_SCOPE);return row;}
    private Role view(RoleManagementMapper.Row row){return view(row,mapper.permissions(tenant(),row.id()));}
    private Role view(RoleManagementMapper.Row row,List<String> permissions){
        boolean configured=!Set.of("SPECIFIED_ORG","SPECIFIED_COMPANY").contains(row.scopeType());
        var scope=configured?new RoleScope(row.scopeType(),List.of(),List.of(),"NONE",List.of()):null;
        return new Role(Long.toString(row.id()),Long.toString(row.version()),row.code(),row.name(),row.enabled(),row.protectedRole(),permissions,scope,configured?"VALID":"UNCONFIGURED");
    }
    private Role record(long id,String action,Role before){
        Role after=view(required(id));var ctx=AccessContextHolder.require();
        try{mapper.audit(tenant(),id,ctx.userId(),action,before==null?null:json.writeValueAsString(before),json.writeValueAsString(after),ctx.traceId()==null?"":ctx.traceId());}
        catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException("角色审计序列化失败",e);}
        audit.record(tenant(),ctx.userId(),"IAM_ROLE",Long.toString(id),null,action);return after;
    }
    private static long tenant(){return AccessContextHolder.require().tenantId();}
    private static void text(String value,int max){if(value==null||value.isBlank()||value.length()>max||value.chars().anyMatch(Character::isISOControl))throw bad();}
    private static long version(String value){try{if(value==null||!value.matches("0|[1-9][0-9]*"))throw bad();return Long.parseLong(value);}catch(NumberFormatException e){throw bad();}}
    private static DomainException bad(){return new DomainException(SystemErrorCode.MALFORMED_BODY);}
}
