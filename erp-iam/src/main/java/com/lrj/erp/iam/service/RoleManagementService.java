package com.lrj.erp.iam.service;
import com.lrj.erp.iam.repository.RoleManagementMapper;
import com.lrj.erp.iam.repository.RoleScopeMapper;
import com.lrj.erp.kernel.context.DataScopeType;
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
    private final RoleScopeMapper scopeMapper;private final ActionScopeService actionScopes;
    private final RoleManagementMapper mapper;private final PermissionChecker checker;private final HttpCommandService commands;
    private final com.fasterxml.jackson.databind.ObjectMapper json;private final PermissionCatalog catalog;private final BusinessAudit audit;
    public RoleManagementService(RoleManagementMapper mapper,PermissionChecker checker,HttpCommandService commands,PermissionCatalog catalog,BusinessAudit audit,com.fasterxml.jackson.databind.ObjectMapper json,RoleScopeMapper scopeMapper,ActionScopeService actionScopes){
        this.mapper=mapper;this.checker=checker;this.commands=commands;this.catalog=catalog;this.audit=audit;this.json=json;this.scopeMapper=scopeMapper;this.actionScopes=actionScopes;
    }
    /** 旧数组响应的字段与顺序兼容，仅把SQL移回模块持久化层。 */
    public List<LegacyRole> legacy(){checker.requireAll("iam:role:read");return mapper.legacy(tenant());}
    /** 固定目录不返回受保护权限，普通角色不可通过列表复制获得管理员能力。 */
    public List<Permission> permissions(){checker.requireAll("iam:role:read");return catalog.entries();}
    /** 有界分页，排序列由XML允许列表选择；不插入客户端SQL。 */
    @org.springframework.transaction.annotation.Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Page<Role> directory(int page,int size,String q,String sort){
        checker.requireAll("iam:role:read");
        if(page<1||size<1||size>200)throw new DomainException(SystemErrorCode.INVALID_PAGINATION);
        if(q!=null&&q.length()>128)throw bad();
        if(!Set.of("code","name","id").contains(sort))throw new DomainException(SystemErrorCode.INVALID_SORT_FIELD);
        var rows=mapper.list(tenant(),q,sort,(long)(page-1)*size,size);
        var grants=rows.isEmpty()?Map.<Long,List<String>>of():mapper.grants(tenant(),rows.stream().map(RoleManagementMapper.Row::id).toList()).stream()
            .collect(java.util.stream.Collectors.groupingBy(RoleManagementMapper.Grant::roleId,java.util.stream.Collectors.mapping(RoleManagementMapper.Grant::permission,java.util.stream.Collectors.toList())));
        var scopeViews=scopeViews(rows);
        return new Page<>(rows.stream().map(r->view(r,grants.getOrDefault(r.id(),List.of()),scopeViews.get(r.id()))).toList(),mapper.count(tenant(),q),page,size);
    }
    /** 详情显式tenant过滤，跨租户与不存在均为404。 */
    @org.springframework.transaction.annotation.Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
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
    /** 范围编辑只变更主体范围；仓库授权在S7B提供前保持NONE。 */
    public Role assignScope(long id,String key,ScopeUpdate input){
        long version=version(input.expectedVersion());RoleScope scope=input.scope();
        if(scope==null||scope.type()==null||scope.orgIds()==null||scope.companyIds()==null||scope.warehouseIds()==null
            || !"NONE".equals(scope.warehouseMode())||!scope.warehouseIds().isEmpty())throw bad();
        DataScopeType type;try{type=DataScopeType.of(scope.type());}catch(IllegalArgumentException e){throw bad();}
        var orgs=ids(scope.orgIds());var companies=ids(scope.companyIds());
        if((type==DataScopeType.SPECIFIED_ORG)!=!orgs.isEmpty() || (type==DataScopeType.SPECIFIED_COMPANY)!=!companies.isEmpty())throw bad();
        var normalized=new ScopeUpdate(input.expectedVersion(),new RoleScope(type.code(),orgs.stream().map(String::valueOf).toList(),companies.stream().map(String::valueOf).toList(),"NONE",List.of()));
        return commands.execute("PUT /iam/roles/"+id+"/data-scope",key,normalized,200,Role.class,()->{authorize();mutable(id);},()->{
            if(!orgs.isEmpty()&&scopeMapper.validReferences(tenant(),orgs,false)!=orgs.size()
                || !companies.isEmpty()&&scopeMapper.validReferences(tenant(),companies,true)!=companies.size())throw bad();
            Role before=view(required(id));
            if(scopeMapper.updateType(tenant(),id,version,type.code())!=1)throw new DomainException(SystemErrorCode.OPTIMISTIC_LOCK);
            scopeMapper.clearOrgs(tenant(),id);scopeMapper.clearCompanies(tenant(),id);
            orgs.forEach(org->scopeMapper.addOrg(tenant(),id,org));companies.forEach(company->scopeMapper.addCompany(tenant(),id,company));
            return record(id,"DATA_SCOPE",before);
        });
    }
    /** 安全管理员可管理本租户组织；其他用户仅看到其组织引用范围内的森林。 */
    @org.springframework.transaction.annotation.Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public List<OrgNode> organizationTree(){
        checker.requireAll("iam:org:read");
        var rows=scopeMapper.tree(actionScopes.forPermission("iam:org:read"),mapper.isAdmin(tenant(),AccessContextHolder.require().userId()));
        if(rows.size()>2000)throw new DomainException(AuthErrorCode.SCOPE_TOO_LARGE);
        Set<Long> visible=rows.stream().map(RoleScopeMapper.Org::id).collect(java.util.stream.Collectors.toSet());
        Map<Long,List<RoleScopeMapper.Org>> children=new HashMap<>();
        for(var row:rows)children.computeIfAbsent(row.parentId()!=null&&visible.contains(row.parentId())?row.parentId():0L,k->new ArrayList<>()).add(row);
        Set<Long> visited=new HashSet<>();var result=buildTree(0L,children,visited,0);
        if(visited.size()!=rows.size())throw new DomainException(AuthErrorCode.SCOPE_TOO_LARGE);
        return result;
    }
    private List<OrgNode> buildTree(long parent,Map<Long,List<RoleScopeMapper.Org>> children,Set<Long> visited,int depth){
        if(depth>64)throw new DomainException(AuthErrorCode.SCOPE_TOO_LARGE);
        var result=new ArrayList<OrgNode>();
        for(var row:children.getOrDefault(parent,List.of())){
            if(!visited.add(row.id()))throw new DomainException(AuthErrorCode.SCOPE_TOO_LARGE);
            result.add(new OrgNode(Long.toString(row.id()),parent==0?null:Long.toString(parent),row.type(),row.code(),row.name(),row.enabled(),buildTree(row.id(),children,visited,depth+1)));
        }
        return result;
    }
    private static List<Long> ids(List<String> values){
        if(values.size()>200)throw bad();
        try{return values.stream().map(v->{if(v==null||!v.matches("[1-9][0-9]*"))throw bad();return Long.parseLong(v);}).distinct().sorted().toList();}
        catch(NumberFormatException e){throw bad();}
    }
    private Map<Long,RoleScope> scopeViews(List<RoleManagementMapper.Row> rows){
        if(rows.isEmpty())return Map.of();
        var roleIds=rows.stream().map(RoleManagementMapper.Row::id).toList();
        var orgs=scopeMapper.orgs(tenant(),roleIds).stream().collect(java.util.stream.Collectors.groupingBy(RoleScopeMapper.Reference::roleId));
        var companies=scopeMapper.companies(tenant(),roleIds).stream().collect(java.util.stream.Collectors.groupingBy(RoleScopeMapper.Reference::roleId));
        Map<Long,RoleScope> result=new HashMap<>();
        for(var row:rows){
            var orgIds=orgs.getOrDefault(row.id(),List.of()).stream().map(ref->Long.toString(ref.id())).toList();
            var companyIds=companies.getOrDefault(row.id(),List.of()).stream().map(ref->Long.toString(ref.id())).toList();
            if(row.scopeType().equals("SPECIFIED_ORG")&&orgIds.isEmpty() || row.scopeType().equals("SPECIFIED_COMPANY")&&companyIds.isEmpty())continue;
            result.put(row.id(),new RoleScope(row.scopeType(),orgIds,companyIds,"NONE",List.of()));
        }
        return result;
    }
    private Role view(RoleManagementMapper.Row row){return view(row,mapper.permissions(tenant(),row.id()),scopeViews(List.of(row)).get(row.id()));}
    private Role view(RoleManagementMapper.Row row,List<String> permissions,RoleScope scope){
        return new Role(Long.toString(row.id()),Long.toString(row.version()),row.code(),row.name(),row.enabled(),row.protectedRole(),permissions,scope,scope!=null?"VALID":"UNCONFIGURED");
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
