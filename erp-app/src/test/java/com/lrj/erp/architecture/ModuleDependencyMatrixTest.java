package com.lrj.erp.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 强制 MODULE_SERVICE_MAP §1 的<b>完整</b>依赖矩阵。
 *
 * <p>此前只有若干点状规则（无环、库存不反向依赖、采销不互依赖），
 * 覆盖不到"某模块依赖了矩阵未授权的另一个模块"这一类。
 * P4 实现时就真的发生了：采购一度直接依赖 erp-document，而矩阵只允许
 * 它依赖 kernel/iam/masterdata/numbering/inventory/approval，
 * erp-document 的入向依赖只有 app。编译通过、点状规则也全绿，
 * 架构却已经偏离——这正是本规则存在的理由。
 */
@DisplayName("模块依赖矩阵")
class ModuleDependencyMatrixTest {

    private static final JavaClasses ERP = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.lrj.erp");

    /** 模块包名 -> 允许依赖的模块包名（MODULE_SERVICE_MAP §1「出向依赖」列）。 */
    private static final Map<String, Set<String>> ALLOWED = Map.ofEntries(
            Map.entry("kernel",      Set.of()),
            Map.entry("numbering",   Set.of("kernel")),
            Map.entry("iam",         Set.of("kernel", "numbering")),
            Map.entry("approval",    Set.of("kernel", "iam")),
            Map.entry("masterdata",  Set.of("kernel", "iam", "numbering")),
            Map.entry("document",    Set.of("kernel", "iam")),
            Map.entry("reporting",   Set.of("kernel", "iam")),
            Map.entry("inventory",   Set.of("kernel", "iam", "masterdata", "numbering", "approval")),
            Map.entry("procurement", Set.of("kernel", "iam", "masterdata", "numbering", "inventory", "approval")),
            Map.entry("sales",       Set.of("kernel", "iam", "masterdata", "numbering", "inventory", "approval")),
            Map.entry("finance",     Set.of("kernel", "iam", "masterdata", "numbering", "approval")));

    private static final List<String> ALL_MODULES = List.copyOf(ALLOWED.keySet());

    @Test
    @DisplayName("每个模块只能依赖矩阵中授权的模块")
    void 依赖矩阵() {
        ALLOWED.forEach((module, allowed) -> {
            List<String> forbidden = ALL_MODULES.stream()
                    .filter(m -> !m.equals(module) && !allowed.contains(m))
                    .map(m -> "com.lrj.erp." + m + "..")
                    .toList();
            if (forbidden.isEmpty()) {
                return;
            }
            noClasses()
                    .that().resideInAPackage("com.lrj.erp." + module + "..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(forbidden.toArray(String[]::new))
                    .because("MODULE_SERVICE_MAP §1 只授权 %s 依赖 %s".formatted(module, allowed))
                    .allowEmptyShould(true)
                    .check(ERP);
        });
    }
}
