package com.lrj.erp.iam.service;
import java.util.*;
import org.springframework.stereotype.Component;
import com.lrj.erp.iam.service.RoleModels.Permission;
/** 普通角色可授予的固定有界目录，不包含受保护的安全管理员权限。 */
@Component
public class PermissionCatalog {
    private final List<Permission> entries;
    public PermissionCatalog(){
        var list=new ArrayList<Permission>();
        add(list,"iam","权限管理","role","角色",true);add(list,"iam","权限管理","org","组织",false);
        list.add(new Permission("iam:user:read","查看用户","权限管理"));list.add(new Permission("iam:user:assign","分配用户角色","权限管理"));
        String[][] md={{"unit","单位"},{"product","商品"},{"sku","SKU"},{"supplier","供应商"},{"warehouse","仓库"},{"category","分类"},{"settlement-method","结算方式"}};
        for(var x:md)add(list,"masterdata","主数据",x[0],x[1],!Set.of("category","settlement-method").contains(x[0]));
        add(list,"purchase","采购","order","采购单",true);add(list,"purchase","采购","receipt","收货单",true);
        list.add(new Permission("purchase:order:approve","审批采购单","采购"));
        add(list,"inventory","库存","balance","库存余额",false);add(list,"inventory","库存","transaction","库存流水",false);
        add(list,"finance","财务","payable","应付单",false);entries=List.copyOf(list);
    }
    /** 固定目录供前端显示，不能以客户端提交任意权限点扩展。 */
    public List<Permission> entries(){return entries;}
    /** 校验权限集合，未知权限及受保护权限一律拒绝。 */
    public boolean contains(String code){return entries.stream().anyMatch(p->p.code().equals(code));}
    private static void add(List<Permission> list,String module,String group,String resource,String label,boolean write){
        list.add(new Permission(module+":"+resource+":read","查看"+label,group));
        if(write)list.add(new Permission(module+":"+resource+":write","维护"+label,group));
    }
}
