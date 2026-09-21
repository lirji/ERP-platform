package com.lrj.erp.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 工程约定的机器强制（对应 docs/design/erp-platform/conventions/ 下的八项约定）。
 *
 * <p><b>allowEmptyShould(true) 的理由</b>：P0 骨架刻意不含任何业务类，
 * 这些规则此刻没有匹配对象。ArchUnit 默认会把"没检查到任何类"判为失败——这个默认是对的，
 * 它防止规则悄悄空跑。但在 P0 让主干长期变红并不能换来任何约束力，
 * 因此改为显式允许空集，并由 {@link ArchRuleEnforcementNegativeTest} 用违规夹具
 * 逐条证明每条规则确实会拦截。规则的牙齿由负向证明担保，而不是由"当前恰好没有违规"担保。
 *
 * <p>这些规则针对的是提示词第三十章「禁止事项」中可被静态检测的条目：
 * Controller 直接操作 Mapper（第 4 条）、业务层拼 SQL（开发规范 §7）、
 * 跨 Bounded Context 访问数据库表（第 6 条）。
 */
@DisplayName("编码与分层约定")
class CodingConventionArchitectureTest {

    private static final JavaClasses ERP = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.lrj.erp");

    @Test
    @DisplayName("Controller 不得直接依赖 Mapper，必须经过应用服务")
    void controller不得直接操作Mapper() {
        noClasses()
                .that().haveSimpleNameEndingWith("Controller")
                .should().dependOnClassesThat().haveSimpleNameEndingWith("Mapper")
                .because("Controller 只处理协议与参数；绕过应用服务会让事务边界与权限校验失去统一入口（禁止事项 4）")
                .allowEmptyShould(true)
                .check(ERP);
    }

    @Test
    @DisplayName("领域层与应用层不得出现 SQL 字符串")
    void 业务层不得拼接SQL() {
        noClasses()
                .that().resideInAnyPackage("..domain..", "..application..", "..service..")
                .should().dependOnClassesThat().resideInAnyPackage("java.sql..", "javax.sql..")
                .because("SQL 集中在 Mapper/持久化层（开发规范 §7）；业务层接触 JDBC 即意味着边界已破")
                .allowEmptyShould(true)
                .check(ERP);
    }

    @Test
    @DisplayName("领域层不得依赖 Spring 容器与 Web 框架")
    void 领域层保持框架无关() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.web..",
                        "org.springframework.stereotype..",
                        "jakarta.servlet..")
                .because("领域模型承载业务规则，绑定框架会让规则无法脱离容器测试，也阻碍未来提取")
                .allowEmptyShould(true)
                .check(ERP);
    }

    @Test
    @DisplayName("持久化对象不得跨模块被引用")
    void 持久化对象不得跨模块泄漏() {
        noClasses()
                .that().resideInAPackage("com.lrj.erp.inventory..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.lrj.erp.masterdata.repository..", "com.lrj.erp.iam.repository..")
                .because("跨模块只传 DTO / 事件契约；直接引用他人持久化对象等于共享数据所有权（禁止事项 5、20）")
                .allowEmptyShould(true)
                .check(ERP);
    }
}
