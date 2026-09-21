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
 * 强制「建表必须写表注释与每一个字段注释」（全局开发规范 §一）。
 *
 * <p>这条规范如果只靠人工 review，迟早会在某次赶工的迁移里失守，而注释缺失在数据量上来之后
 * 几乎不会有人回头补。把它变成测试，是让规范在 CI 里自己站住的唯一办法。
 *
 * <p>扫描全部模块的 {@code src/main/resources/db/migration/*.sql}，对每个 {@code CREATE TABLE}：
 * 要求存在 {@code COMMENT ON TABLE}，且每个字段都有 {@code COMMENT ON COLUMN}。
 */
@DisplayName("迁移脚本注释规范")
class MigrationCommentConventionTest {

    private static final Pattern CREATE_TABLE = Pattern.compile(
            "(?is)CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?([a-z_][a-z0-9_]*)\\s*\\((.*?)\\n\\s*\\);",
            Pattern.DOTALL);

    @Test
    @DisplayName("每个建表语句都必须有表注释和全部字段注释")
    void 建表必须写注释() throws IOException {
        Path root = repoRoot();
        Result r = scanMigrations(root);

        // 防空跑：如果一个迁移文件都没扫到，这条规则就是假绿，必须失败
        assertTrue(r.scannedFiles() > 0,
                "没有扫描到任何迁移脚本——规则空跑等于没有规则");
        assertTrue(r.violations().isEmpty(),
                "迁移脚本违反开发规范 §一（建表必须写表/字段注释）：\n  "
                        + String.join("\n  ", r.violations()));
    }

    /** 扫描结果：违规清单 + 实际扫到的文件数（后者用于证明规则没有空跑）。 */
    record Result(List<String> violations, int scannedFiles) { }

    /** 扫描给定仓库根下全部模块的迁移脚本。包级可见，供负向证明复用。 */
    static Result scanMigrations(Path root) throws IOException {
        List<String> violations = new ArrayList<>();
        List<Path> sqlFiles = new ArrayList<>();

        try (Stream<Path> modules = Files.list(root)) {
            for (Path module : modules.filter(Files::isDirectory).toList()) {
                Path mig = module.resolve("src/main/resources/db/migration");
                if (!Files.isDirectory(mig)) {
                    continue;
                }
                try (Stream<Path> s = Files.walk(mig)) {
                    sqlFiles.addAll(s.filter(p -> p.toString().endsWith(".sql")).toList());
                }
            }
        }

        for (Path sqlFile : sqlFiles) {
            String sql = Files.readString(sqlFile, StandardCharsets.UTF_8);
            String rel = root.relativize(sqlFile).toString();

            Matcher t = CREATE_TABLE.matcher(sql);
            while (t.find()) {
                String table = t.group(1);
                String body = t.group(2);

                if (!Pattern.compile("(?is)COMMENT\\s+ON\\s+TABLE\\s+" + Pattern.quote(table) + "\\s+IS")
                        .matcher(sql).find()) {
                    violations.add("%s：表 %s 缺少 COMMENT ON TABLE".formatted(rel, table));
                }

                for (String column : columnsOf(body)) {
                    if (!Pattern.compile("(?is)COMMENT\\s+ON\\s+COLUMN\\s+" + Pattern.quote(table)
                            + "\\." + Pattern.quote(column) + "\\s+IS").matcher(sql).find()) {
                        violations.add("%s：字段 %s.%s 缺少 COMMENT ON COLUMN".formatted(rel, table, column));
                    }
                }
            }
        }

        return new Result(violations, sqlFiles.size());
    }

    /** 从建表语句体中提取字段名，跳过表级约束行（PRIMARY KEY/UNIQUE/CONSTRAINT/CHECK/FOREIGN KEY）。 */
    static List<String> columnsOf(String body) {
        List<String> cols = new ArrayList<>();
        int depth = 0;
        StringBuilder cur = new StringBuilder();
        List<String> parts = new ArrayList<>();
        for (char c : body.toCharArray()) {
            if (c == '(') depth++;
            if (c == ')') depth--;
            if (c == ',' && depth == 0) {
                parts.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        parts.add(cur.toString());

        for (String raw : parts) {
            String line = raw.strip();
            // 去掉注释行
            line = line.replaceAll("(?m)^\\s*--.*$", "").strip();
            if (line.isEmpty()) continue;
            String upper = line.toUpperCase(Locale.ROOT);
            if (upper.startsWith("PRIMARY KEY") || upper.startsWith("UNIQUE")
                    || upper.startsWith("CONSTRAINT") || upper.startsWith("CHECK")
                    || upper.startsWith("FOREIGN KEY") || upper.startsWith("EXCLUDE")) {
                continue;
            }
            String name = line.split("\\s+")[0].toLowerCase(Locale.ROOT);
            if (name.matches("[a-z_][a-z0-9_]*")) {
                cols.add(name);
            }
        }
        return cols;
    }

    private static Path repoRoot() {
        Path p = Paths.get("").toAbsolutePath();
        while (p != null && !Files.exists(p.resolve("erp-kernel/pom.xml"))) {
            p = p.getParent();
        }
        if (p == null) throw new IllegalStateException("未能定位仓库根");
        return p;
    }
}
