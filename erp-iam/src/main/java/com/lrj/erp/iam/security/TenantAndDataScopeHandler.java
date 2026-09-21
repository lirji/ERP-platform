package com.lrj.erp.iam.security;

import com.baomidou.mybatisplus.extension.plugins.handler.MultiDataPermissionHandler;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.lrj.erp.kernel.context.AccessContext;
import com.lrj.erp.kernel.context.AccessContextHolder;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.*;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 租户与数据权限的 SQL 下推。
 *
 * <p><b>为什么必须下推到 SQL 而不是在内存过滤</b>：内存过滤在分页下必然出错——
 * 先分页后过滤会少数据，先查全量再过滤会把整表拉进内存。数据权限只有变成
 * {@code WHERE} 条件才既正确又可用。P1 出口条件要求断言<b>生成的 SQL</b> 中
 * 确实出现前缀条件，而不是只断言返回的行数。
 */
@Component
public class TenantAndDataScopeHandler implements TenantLineHandler, MultiDataPermissionHandler {

    /**
     * 不参与租户<b>自动注入</b>的表。豁免不等于不做租户隔离——下列各表都另有保证。
     *
     * <ul>
     *   <li>{@code iam_tenant}：租户表本身，没有 tenant_id 列；</li>
     *   <li>{@code flyway_schema_history}：Flyway 元数据，与业务无关；</li>
     *   <li>{@code num_sequence} / {@code num_rule}：<b>tenant_id 是它们主键的一部分，
     *       且只能经 NumberGenerator 访问，而该接口把 tenantId 作为显式入参</b>，
     *       租户隔离已由调用契约保证。此外它们的取号语句是
     *       {@code INSERT ... ON CONFLICT DO UPDATE ... RETURNING}，
     *       MP 的租户拦截器无法改写这种形态（抛 UnsupportedOperationException）——
     *       但即便能改写，自动注入在这里也是多余的。</li>
     * </ul>
     *
     * <p>豁免必须逐个写明理由。一张表一旦静默进入这个集合，它的租户隔离就没有任何机制保证了。
     */
    private static final Set<String> TENANT_EXEMPT = Set.of(
            "iam_tenant", "flyway_schema_history",
            "num_sequence", "num_rule");

    /** 参与数据权限过滤的表必须显式登记——允许列表，而不是排除列表。
     *  用排除列表时，新建的业务表会默认不受数据权限保护，这是危险的默认值。 */
    private static final Set<String> DATA_SCOPE_TABLES = Set.of(
            "pur_order", "sal_order", "inv_stock_document", "fin_account_payable",
            "fin_account_receivable", "doc_audit_log");

    // ---------------------------------------------------------------- 租户

    @Override
    public Expression getTenantId() {
        return new LongValue(AccessContextHolder.require().tenantId());
    }

    @Override
    public String getTenantIdColumn() {
        return "tenant_id";
    }

    @Override
    public boolean ignoreTable(String tableName) {
        // 无上下文时（如启动期的 Flyway、定时任务）不注入租户条件：
        // require() 会抛异常，而在这些场景下抛异常会让应用起不来。
        // 真正的保护在于：任何走 HTTP 的业务查询一定有上下文。
        if (AccessContextHolder.find().isEmpty()) {
            return true;
        }
        return TENANT_EXEMPT.contains(normalize(tableName));
    }

    // ------------------------------------------------------------ 数据权限

    @Override
    public Expression getSqlSegment(Table table, Expression where, String mappedStatementId) {
        if (AccessContextHolder.find().isEmpty()) {
            return null;
        }
        String name = normalize(table.getName());
        if (!DATA_SCOPE_TABLES.contains(name)) {
            return null;
        }

        AccessContext ctx = AccessContextHolder.require();
        String alias = table.getAlias() != null ? table.getAlias().getName() : table.getName();

        return switch (ctx.dataScope().type()) {
            // ALL 仍然只在本租户内——租户条件由 TenantLineHandler 另行注入
            case ALL -> null;
            case SELF -> eq(alias, "created_by", new LongValue(ctx.userId()));
            case DEPT -> eq(alias, "org_path", new StringValue(ctx.orgPath()));
            // 前缀匹配：orgPath 首尾带斜杠，所以 '/1/23/' || '%' 不会匹配到 '/1/234/'
            case DEPT_AND_BELOW -> like(alias, "org_path", ctx.orgPath() + "%");
            case SPECIFIED_ORG -> anyPrefix(alias, ctx.dataScope().orgPaths());
            case SPECIFIED_COMPANY -> in(alias, "company_id",
                    ctx.dataScope().companyIds().stream()
                            .map(id -> (Expression) new LongValue(id)).toList());
        };
    }

    // ------------------------------------------------------------ 表达式构造

    private static Expression eq(String alias, String column, Expression value) {
        return new EqualsTo(new Column(alias + "." + column), value);
    }

    private static Expression like(String alias, String column, String pattern) {
        LikeExpression like = new LikeExpression();
        like.setLeftExpression(new Column(alias + "." + column));
        like.setRightExpression(new StringValue(pattern));
        return like;
    }

    private static Expression in(String alias, String column, List<Expression> values) {
        InExpression in = new InExpression();
        in.setLeftExpression(new Column(alias + "." + column));
        in.setRightExpression(new ParenthesedExpressionList<>(values));
        return in;
    }

    /** 指定多个组织时，任一前缀命中即可。 */
    private static Expression anyPrefix(String alias, List<String> orgPaths) {
        List<Expression> parts = new ArrayList<>();
        for (String path : orgPaths) {
            parts.add(like(alias, "org_path", path + "%"));
        }
        Expression result = parts.get(0);
        for (int i = 1; i < parts.size(); i++) {
            result = new OrExpression(result, parts.get(i));
        }
        // 必须加括号：OR 链若不包裹，会与外层已有的 AND 条件结合成
        // `A AND p1 OR p2`，等价于 `(A AND p1) OR p2` —— 那是越权。
        Parenthesis wrapped = new Parenthesis();
        wrapped.setExpression(result);
        return wrapped;
    }

    private static String normalize(String tableName) {
        return tableName == null ? "" : tableName.replace("\"", "").toLowerCase();
    }
}
