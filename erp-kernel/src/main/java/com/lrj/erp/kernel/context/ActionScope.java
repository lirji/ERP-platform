package com.lrj.erp.kernel.context;
import java.util.List;
/** 一个明确动作的角色范围并集；绝不与其他权限角色的范围交叉配对。 */
public record ActionScope(long tenantId,long userId,String orgPath,List<Grant> grants) {
    public ActionScope { grants=List.copyOf(grants); }
    /** 组织/公司配置已由IAM按真实有效组织解析；空指定集合不会生成grant。 */
    public record Grant(long roleId,String type,List<String> orgPaths,List<Long> companyIds) {
        public Grant { orgPaths=List.copyOf(orgPaths);companyIds=List.copyOf(companyIds); }
    }
}
