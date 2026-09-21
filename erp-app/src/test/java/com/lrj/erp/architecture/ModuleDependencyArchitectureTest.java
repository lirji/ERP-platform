package com.lrj.erp.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * 模块边界的机器强制。
 *
 * <p>提示词第三十/四十六章禁止事项与 MODULE_SERVICE_MAP.md 第 2 节的「禁止」表在此落地为可执行规则。
 * 架构测试写在文档里而不执行 = 没有架构约束（禁止事项 22），因此这些规则跑在 {@code mvn verify} 中。
 *
 * <p>放在 erp-app 的原因：只有组装模块的测试 classpath 才同时看得见全部模块的字节码。
 */
@DisplayName("模块依赖方向与边界")
class ModuleDependencyArchitectureTest {

    /** 只导入本项目字节码，排除测试类——测试可以为了构造场景跨模块引用。 */
    private static final JavaClasses ERP = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.lrj.erp");

    @Test
    @DisplayName("模块之间不得存在依赖环")
    void 模块之间不得存在依赖环() {
        slices().matching("com.lrj.erp.(*)..")
                .should().beFreeOfCycles()
                .check(ERP);
    }

    @Test
    @DisplayName("库存不得依赖采购/销售：库存不认识谁在用它")
    void 库存不得反向依赖上游业务模块() {
        ArchRule rule = com.tngtech.archunit.lang.syntax.ArchRuleDefinition
                .noClasses().that().resideInAPackage("com.lrj.erp.inventory..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("com.lrj.erp.procurement..", "com.lrj.erp.sales..", "com.lrj.erp.finance..")
                .because("库存是被调用方，过账请求由调用方传入来源单据标识；反向依赖会让库存无法独立提取");
        rule.check(ERP);
    }

    @Test
    @DisplayName("采购与销售不得互相依赖")
    void 采购与销售不得横向依赖() {
        com.tngtech.archunit.lang.syntax.ArchRuleDefinition
                .noClasses().that().resideInAPackage("com.lrj.erp.procurement..")
                .should().dependOnClassesThat().resideInAPackage("com.lrj.erp.sales..")
                .because("采购与销售是平行的业务上下文，协作应通过事件或 app 层编排")
                .check(ERP);

        com.tngtech.archunit.lang.syntax.ArchRuleDefinition
                .noClasses().that().resideInAPackage("com.lrj.erp.sales..")
                .should().dependOnClassesThat().resideInAPackage("com.lrj.erp.procurement..")
                .because("同上，方向相反")
                .check(ERP);
    }

    @Test
    @DisplayName("共享内核不得依赖任何业务模块")
    void 内核不得依赖业务模块() {
        com.tngtech.archunit.lang.syntax.ArchRuleDefinition
                .noClasses().that().resideInAPackage("com.lrj.erp.kernel..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.lrj.erp.iam..", "com.lrj.erp.masterdata..", "com.lrj.erp.inventory..",
                        "com.lrj.erp.procurement..", "com.lrj.erp.sales..", "com.lrj.erp.finance..",
                        "com.lrj.erp.approval..", "com.lrj.erp.document..", "com.lrj.erp.reporting..",
                        "com.lrj.erp.numbering..", "com.lrj.erp.app..")
                .because("共享内核被所有模块依赖；它反向依赖业务会立刻造成全仓耦合与依赖环")
                .check(ERP);
    }

    @Test
    @DisplayName("只有 erp-app 允许依赖全部模块")
    void 业务模块不得依赖组装模块() {
        com.tngtech.archunit.lang.syntax.ArchRuleDefinition
                .noClasses().that().resideOutsideOfPackage("com.lrj.erp.app..")
                .should().dependOnClassesThat().resideInAPackage("com.lrj.erp.app..")
                .because("erp-app 是组装层，被依赖即意味着业务模块绑死了启动方式，无法独立提取")
                .check(ERP);
    }

    @Test
    @DisplayName("核心域遵守六边形分层：domain 不得依赖 application/infrastructure/interfaces")
    void 核心域分层方向() {
        for (String ctx : new String[]{"inventory", "procurement", "sales", "finance"}) {
            // withOptionalLayers：P0 骨架中四层尚无类，ArchUnit 默认把"空层"判为违规。
            // 这条规则的牙齿由 ArchRuleEnforcementNegativeTest 单独证明，不依赖当前是否有业务类。
            // P1 起各层填入真实类后，本规则自动开始约束。
            layeredArchitecture().consideringOnlyDependenciesInLayers().withOptionalLayers(true)
                    .layer("domain").definedBy("com.lrj.erp." + ctx + ".domain..")
                    .layer("application").definedBy("com.lrj.erp." + ctx + ".application..")
                    .layer("infrastructure").definedBy("com.lrj.erp." + ctx + ".infrastructure..")
                    .layer("interfaces").definedBy("com.lrj.erp." + ctx + ".interfaces..")
                    // domain 是最内层：任何人可以依赖它，它不依赖任何人
                    .whereLayer("interfaces").mayNotBeAccessedByAnyLayer()
                    .whereLayer("application").mayOnlyBeAccessedByLayers("interfaces", "infrastructure")
                    .whereLayer("infrastructure").mayOnlyBeAccessedByLayers("interfaces")
                    .as("六边形分层：" + ctx)
                    .check(ERP);
        }
    }
}
