package com.lrj.erp.iam.service;
import com.lrj.erp.iam.repository.RoleScopeMapper;
import com.lrj.erp.kernel.context.*;
import com.lrj.erp.kernel.error.DomainException;
import com.lrj.erp.iam.security.AuthErrorCode;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;
/** 每次按目标动作重读角色配置；广范围只读角色不会放大窄范围写权限。 */
@Service
public class ActionScopeService {
    private final RoleScopeMapper mapper;
    public ActionScopeService(RoleScopeMapper mapper){this.mapper=mapper;}
    /** 无适用角色/无有效指定明细时产生空并集，SQL最终为FALSE。 */
    @org.springframework.transaction.annotation.Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public ActionScope forPermission(String permission){
        var ctx=AccessContextHolder.require();var roles=mapper.forPermission(ctx.tenantId(),ctx.userId(),permission);
        if(roles.size()>200)throw new DomainException(AuthErrorCode.SCOPE_TOO_LARGE);
        if(roles.isEmpty())return new ActionScope(ctx.tenantId(),ctx.userId(),ctx.orgPath(),List.of());
        var ids=roles.stream().map(RoleScopeMapper.ScopeRole::id).toList();
        var orgs=mapper.orgs(ctx.tenantId(),ids).stream().filter(RoleScopeMapper.Reference::active).collect(Collectors.groupingBy(RoleScopeMapper.Reference::roleId));
        var companies=mapper.companies(ctx.tenantId(),ids).stream().filter(RoleScopeMapper.Reference::active).collect(Collectors.groupingBy(RoleScopeMapper.Reference::roleId));
        List<ActionScope.Grant> grants=new ArrayList<>();
        for(var role:roles){
            var paths=orgs.getOrDefault(role.id(),List.of()).stream().map(RoleScopeMapper.Reference::path).toList();
            var companyIds=companies.getOrDefault(role.id(),List.of()).stream().map(RoleScopeMapper.Reference::id).toList();
            if(role.type().equals("SPECIFIED_ORG")&&paths.isEmpty() || role.type().equals("SPECIFIED_COMPANY")&&companyIds.isEmpty())continue;
            if(Arrays.stream(DataScopeType.values()).noneMatch(t->t.code().equals(role.type())))continue;
            grants.add(new ActionScope.Grant(role.id(),role.type(),paths,companyIds));
        }
        return new ActionScope(ctx.tenantId(),ctx.userId(),ctx.orgPath(),grants);
    }
}
