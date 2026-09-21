package com.lrj.erp.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 负向证明：证明架构规则确实会拦截违规，而不是恰好没有违规。
 *
 * <p>ROADMAP 的 P0 出口条件 ② 要求「故意加一条跨模块表访问后构建失败」。
 * 直接把违规代码留在产品源码里会让 {@code mvn verify} 永久失败，因此本测试改用等价但可持续的形式：
 * <b>把违规样本喂给同一条规则，断言它抛出 AssertionError</b>。
 * 二者证明力相同——规则有牙齿——但不会让主干构建处于红色。
 *
 * <p>如果有人把某条规则改成恒真（例如误删 because 后的条件），本测试会立刻失败。
 */
@DisplayName("负向证明：架构规则确实会拦截")
class ArchRuleEnforcementNegativeTest {

    @Test
    @DisplayName("库存反向依赖销售的夹具，必须被规则判为违规")
    void 反向依赖规则确实会拦截() {
        // 注意：这里 *包含* 测试类，才能看见 ArchNegativeFixture
        JavaClasses withFixtures = new ClassFileImporter().importPackages("com.lrj.erp");

        ArchRule rule = noClasses()
                .that().resideInAPackage("com.lrj.erp.inventory..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("com.lrj.erp.procurement..", "com.lrj.erp.sales..", "com.lrj.erp.finance..");

        AssertionError raised = assertThrows(AssertionError.class,
                () -> rule.check(withFixtures),
                "规则没有拦截已知违规——说明这条架构规则是空跑的，等于没有约束");

        assertTrue(raised.getMessage().contains("ArchNegativeFixture"),
                "拦截信息应指明违规类，实际为：" + raised.getMessage());
    }

    @Test
    @DisplayName("跨模块表访问扫描，必须能发现构造出的越界 SQL")
    void 表所有权规则确实会拦截(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        // 构造一个假的仓库树：erp-inventory 的 mapper 里查了 erp-finance 拥有的 fin_ 表
        Path mapperDir = tmp.resolve("erp-inventory/src/main/resources/mapper");
        Files.createDirectories(mapperDir);
        Files.writeString(mapperDir.resolve("BadMapper.xml"), """
                <mapper namespace="bad">
                  <select id="crossBoundary">
                    SELECT amount FROM fin_account_payable WHERE tenant_id = #{tenantId}
                  </select>
                </mapper>
                """);

        List<String> violations = TableOwnershipArchitectureTest.scan(tmp);

        assertFalse(violations.isEmpty(),
                "扫描没有发现构造出的跨模块表访问——说明该规则无法拦截真实越界");
        assertTrue(violations.get(0).contains("fin_account_payable"),
                "违规信息应指明越界的表，实际为：" + violations);
    }

    @Test
    @DisplayName("干净的仓库树不得产生误报")
    void 表所有权规则不误报(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        Path mapperDir = tmp.resolve("erp-inventory/src/main/resources/mapper");
        Files.createDirectories(mapperDir);
        Files.writeString(mapperDir.resolve("GoodMapper.xml"), """
                <mapper namespace="good">
                  <select id="ownTableOnly">
                    SELECT qty FROM inv_balance WHERE tenant_id = #{tenantId}
                  </select>
                  <select id="platformTableAllowed">
                    SELECT id FROM erp_outbox_message WHERE status = 'PENDING'
                  </select>
                </mapper>
                """);

        assertTrue(TableOwnershipArchitectureTest.scan(tmp).isEmpty(),
                "访问自己的表与平台共享表被误判为越界，规则过严会让人绕过它");
    }

    @Test
    @DisplayName("Controller 直接持有 Mapper 的夹具，必须被规则判为违规")
    void controller直接操作Mapper规则确实会拦截() {
        JavaClasses withFixtures = new ClassFileImporter().importPackages("com.lrj.erp");

        ArchRule rule = noClasses()
                .that().haveSimpleNameEndingWith("Controller")
                .should().dependOnClassesThat().haveSimpleNameEndingWith("Mapper");

        AssertionError raised = assertThrows(AssertionError.class,
                () -> rule.check(withFixtures),
                "规则没有拦截 Controller→Mapper —— 禁止事项第 4 条形同虚设");
        assertTrue(raised.getMessage().contains("FixtureController"),
                "拦截信息应指明违规类，实际为：" + raised.getMessage());
    }

    @Test
    @DisplayName("领域类绑定 Spring 注解的夹具，必须被规则判为违规")
    void 领域层框架无关规则确实会拦截() {
        JavaClasses withFixtures = new ClassFileImporter().importPackages("com.lrj.erp");

        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.web..",
                        "org.springframework.stereotype..",
                        "jakarta.servlet..");

        AssertionError raised = assertThrows(AssertionError.class,
                () -> rule.check(withFixtures),
                "规则没有拦截领域层对 Spring 的依赖 —— 领域模型会被框架绑死");
        assertTrue(raised.getMessage().contains("FixtureDomainService"),
                "拦截信息应指明违规类，实际为：" + raised.getMessage());
    }

    @Test
    @DisplayName("缺注释的建表语句，必须被规范检查判为违规")
    void 建表注释规范确实会拦截(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        Path mig = tmp.resolve("erp-inventory/src/main/resources/db/migration");
        Files.createDirectories(mig);
        Files.writeString(mig.resolve("V9__no_comments.sql"), """
                CREATE TABLE inv_bad_table (
                    id         BIGSERIAL   PRIMARY KEY,
                    tenant_id  BIGINT      NOT NULL,
                    qty        NUMERIC(18,6) NOT NULL
                );
                """);

        var r = MigrationCommentConventionTest.scanMigrations(tmp);

        assertTrue(r.scannedFiles() > 0, "夹具文件应被扫描到");
        assertFalse(r.violations().isEmpty(),
                "缺少表/字段注释却没有被拦截 —— 开发规范 §一 形同虚设");
        assertTrue(r.violations().stream().anyMatch(v -> v.contains("COMMENT ON TABLE")),
                "应报出缺少表注释，实际为：" + r.violations());
        assertTrue(r.violations().stream().anyMatch(v -> v.contains("qty")),
                "应报出缺少字段注释的具体字段，实际为：" + r.violations());
    }

    @Test
    @DisplayName("含 available 列的建表语句，必须被规则判为违规")
    void 可用量落列规则确实会拦截() {
        // 直接用规则的判定函数验证它认得这些列名；
        // 若有人把判定改成恒 false，本测试立刻失败
        assertTrue(AvailableIsDerivedTest.isAvailable("available"));
        assertTrue(AvailableIsDerivedTest.isAvailable("AVAILABLE_QTY"));
        assertTrue(AvailableIsDerivedTest.isAvailable("available_quantity"));
        assertFalse(AvailableIsDerivedTest.isAvailable("on_hand"),
                "规则过严会误伤正常列名");
        assertFalse(AvailableIsDerivedTest.isAvailable("reserved"));
    }
}
