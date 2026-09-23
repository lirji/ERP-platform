package com.lrj.erp.iam.service;

import com.lrj.erp.iam.repository.UserAuthMapper;
import com.lrj.erp.iam.security.AuthErrorCode;
import com.lrj.erp.kernel.context.*;
import com.lrj.erp.kernel.error.DomainException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * 由已验证的外部身份或显式开发身份装配 {@link AccessContext}。
 *
 * <p><b>边界</b>：本类<b>不做认证</b>。谁是调用者由 auth-platform(Casdoor/OIDC) 判定，
 * 这里只负责把"已知是谁"翻译成"他能做什么、能看哪些数据"。
 * 认证与授权分离是 SECURITY_ARCHITECTURE 的既定边界。
 */
@Service
public class AccessContextAssembler {

    private final UserAuthMapper mapper;

    public AccessContextAssembler(UserAuthMapper mapper) {
        this.mapper = mapper;
    }

    /** 开发身份入口；生产请求只能调用 assembleOidc。 */
    public AccessContext assemble(String tenantCode, String username, String traceId) {
        return assembleRecord(mapper.findUser(tenantCode, username), traceId);
    }

    /** 每次请求从数据库重读启用状态与权限，离职禁用不等待令牌过期。 */
    public AccessContext assembleOidc(String issuer, String owner, String subject, String traceId) {
        return assembleRecord(mapper.findOidcUser(issuer, owner, subject), traceId);
    }

    private AccessContext assembleRecord(UserAuthMapper.UserAuthRecord u, String traceId) {
        if (u == null) {
            // 用户不存在与凭证无效返回同一个错误：区分开会变成用户名枚举接口
            throw new DomainException(AuthErrorCode.INVALID_CREDENTIAL);
        }
        if (!u.tenantEnabled()) {
            throw new DomainException(AuthErrorCode.TENANT_DISABLED);
        }
        if (!u.userEnabled()) {
            throw new DomainException(AuthErrorCode.USER_DISABLED);
        }

        Set<String> permissions = Set.copyOf(mapper.findPermissions(u.tenantId(), u.userId()));
        DataScope scope = widestScope(mapper.findDataScopeTypes(u.tenantId(), u.userId()));

        return new AccessContext(u.tenantId(), u.userId(), u.companyId(), u.orgPath(),
                permissions, scope, u.timezone(), traceId);
    }

    /**
     * 多角色时取<b>最宽</b>的数据范围。
     *
     * <p>取最窄会产生反直觉且危险的行为：给用户加一个角色反而让他看到的数据变少，
     * 管理员会通过不断加角色来"修"，最终权限配置无人能解释。
     *
     * <p>没有任何角色时回落到 {@code SELF}（最窄），而不是 {@code ALL}——
     * 缺省必须是最小权限。
     */
    private DataScope widestScope(List<String> types) {
        DataScopeType widest = DataScopeType.SELF;
        for (String t : types) {
            DataScopeType candidate = DataScopeType.of(t);
            if (rank(candidate) > rank(widest)) {
                widest = candidate;
            }
        }
        // SPECIFIED_* 需要具体 id 列表，P1 暂不支持在角色上配置明细，
        // 命中时按 DEPT_AND_BELOW 处理并留待 P2 补充配置表
        if (widest == DataScopeType.SPECIFIED_ORG || widest == DataScopeType.SPECIFIED_COMPANY) {
            widest = DataScopeType.DEPT_AND_BELOW;
        }
        return DataScope.of(widest);
    }

    private static int rank(DataScopeType t) {
        return switch (t) {
            case SELF -> 0;
            case DEPT -> 1;
            case DEPT_AND_BELOW -> 2;
            case SPECIFIED_ORG -> 3;
            case SPECIFIED_COMPANY -> 4;
            case ALL -> 5;
        };
    }
}
