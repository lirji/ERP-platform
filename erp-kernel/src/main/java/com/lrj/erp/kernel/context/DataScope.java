package com.lrj.erp.kernel.context;

import java.util.List;
import java.util.Set;

/**
 * 数据范围。`SPECIFIED_ORG` / `SPECIFIED_COMPANY` 需要携带具体 id 列表。
 *
 * @param type      范围类型
 * @param orgPaths  SPECIFIED_ORG 时的组织路径列表；其他类型为空
 * @param companyIds SPECIFIED_COMPANY 时的公司列表；其他类型为空
 */
public record DataScope(DataScopeType type, List<String> orgPaths, Set<Long> companyIds, boolean denied) {

    /** 保持既有调用构造器兼容；默认不是拒绝摘要。 */
    public DataScope(DataScopeType type,List<String> orgPaths,Set<Long> companyIds){this(type,orgPaths,companyIds,false);}

    /** 旧入口无法表达角色动作并集时显式拒绝，保留/me枚举摘要。 */
    public static DataScope denied(DataScopeType type){return new DataScope(type,List.of(),Set.of(),true);}

    public DataScope {
        orgPaths = orgPaths == null ? List.of() : List.copyOf(orgPaths);
        companyIds = companyIds == null ? Set.of() : Set.copyOf(companyIds);

        // 配置非法要在构造时就失败：带着空列表的 SPECIFIED_ORG 一旦进入查询层，
        // 会被拼成 `org_path IN ()` 或被忽略成"无限制"，后者是静默越权。
        if (!denied && type == DataScopeType.SPECIFIED_ORG && orgPaths.isEmpty()) {
            throw new IllegalArgumentException("SPECIFIED_ORG 必须指定至少一个组织路径");
        }
        if (!denied && type == DataScopeType.SPECIFIED_COMPANY && companyIds.isEmpty()) {
            throw new IllegalArgumentException("SPECIFIED_COMPANY 必须指定至少一个公司");
        }
    }

    public static DataScope of(DataScopeType type) {
        return new DataScope(type, List.of(), Set.of());
    }

    public static DataScope specifiedOrg(List<String> orgPaths) {
        return new DataScope(DataScopeType.SPECIFIED_ORG, orgPaths, Set.of());
    }

    public static DataScope specifiedCompany(Set<Long> companyIds) {
        return new DataScope(DataScopeType.SPECIFIED_COMPANY, List.of(), companyIds);
    }
}
