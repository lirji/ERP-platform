package com.lrj.erp.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Data Ownership 的机器强制：任何模块的 SQL 不得触碰其他模块拥有的表前缀。
 *
 * <p>为什么不能用 ArchUnit：跨模块表访问发生在 Mapper XML 的 SQL 文本里，不在字节码中，
 * ArchUnit 看不见。这条规则如果只写在文档里，就是提示词禁止事项第 20 条
 * （「跨模块直接访问表后声称未来容易拆」）——所以它必须作为可执行测试存在。
 *
 * <p>表前缀与归属来自 MODULE_SERVICE_MAP.md 第 1 节，是 Logical Service Boundary 的数据面证据。
 */
@DisplayName("表所有权（跨模块表访问）")
class TableOwnershipArchitectureTest {

    /** 表前缀 -> 拥有该前缀的模块目录名。 */
    static final Map<String, String> OWNER = Map.of(
            "num_", "erp-numbering",
            "iam_", "erp-iam",
            "md_",  "erp-masterdata",
            "apr_", "erp-approval",
            "doc_", "erp-document",
            "inv_", "erp-inventory",
            "pur_", "erp-procurement",
            "sal_", "erp-sales",
            "fin_", "erp-finance",
            "rpt_", "erp-reporting");

    /**
     * erp_ 前缀是平台级共享表（如 Outbox），不属于任何业务模块，允许各模块访问。
     * 这里只做词法级识别，足以拦截"把别人的表写进自己 SQL"这类真实越界；
     * 它不是 SQL 解析器，也不需要是。
     */
    private static final Pattern TABLE_REF = Pattern.compile(
            "(?i)\\b(?:from|join|into|update|delete\\s+from)\\s+([a-z_][a-z0-9_]*)");

    @Test
    @DisplayName("Mapper XML 中不得出现其他模块的表前缀")
    void mapper中不得跨模块访问表() throws IOException {
        List<String> violations = scan(repoRoot());
        assertTrue(violations.isEmpty(),
                "检测到跨模块表访问，违反 Data Ownership：\n  " + String.join("\n  ", violations));
    }

    /**
     * 扫描给定仓库根下所有模块的 Mapper XML，返回越界清单。
     * 提取为包级可见方法，供负向证明测试（ArchRuleEnforcementNegativeTest）用临时目录验证本规则确实会拦截。
     */
    static List<String> scan(Path root) throws IOException {
        List<String> violations = new ArrayList<>();
        for (Map.Entry<String, String> module : OWNER.entrySet()) {
            Path mapperDir = root.resolve(module.getValue()).resolve("src/main/resources/mapper");
            if (!Files.isDirectory(mapperDir)) {
                continue;
            }
            try (Stream<Path> xmls = Files.walk(mapperDir)) {
                for (Path xml : xmls.filter(p -> p.toString().endsWith(".xml")).toList()) {
                    String sql = Files.readString(xml, StandardCharsets.UTF_8);
                    Matcher m = TABLE_REF.matcher(sql);
                    while (m.find()) {
                        String table = m.group(1).toLowerCase(Locale.ROOT);
                        for (Map.Entry<String, String> owner : OWNER.entrySet()) {
                            if (table.startsWith(owner.getKey()) && !owner.getValue().equals(module.getValue())) {
                                violations.add("%s 引用了 %s 拥有的表 %s（%s）".formatted(
                                        module.getValue(), owner.getValue(), table, root.relativize(xml)));
                            }
                        }
                    }
                }
            }
        }
        return violations;
    }

    /** 从测试工作目录（erp-app/）向上找到包含聚合 POM 的仓库根。 */
    private static Path repoRoot() {
        Path p = Paths.get("").toAbsolutePath();
        while (p != null && !Files.exists(p.resolve("erp-kernel/pom.xml"))) {
            p = p.getParent();
        }
        if (p == null) {
            throw new IllegalStateException("未能定位仓库根：找不到 erp-kernel/pom.xml");
        }
        return p;
    }
}
