package com.lrj.erp.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P3 出口条件：{@code available} <b>不得</b>有对应数据库字段。
 *
 * <p>可用量是 {@code on_hand - reserved - locked} 的函数。一旦落成列，
 * 就必须在每一条写路径上同步维护它；漏掉任何一处都会让可用量与事实不符，
 * 而这种不一致不会自愈，也极难被发现——等到超卖发生时，已经晚了。
 *
 * <p>本测试扫描全部迁移脚本，确保没有人"顺手"加上这一列。
 */
@DisplayName("可用量必须是算出来的")
class AvailableIsDerivedTest {

    /** 匹配建表体内的列定义行（列名在行首）。 */
    private static final Pattern CREATE_TABLE = Pattern.compile(
            "(?is)CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?([a-z_][a-z0-9_]*)\\s*\\((.*?)\\n\\s*\\);");

    private static final Pattern ADD_COLUMN = Pattern.compile(
            "(?is)ALTER\\s+TABLE\\s+([a-z_][a-z0-9_]*)\\s+ADD\\s+COLUMN\\s+([a-z_][a-z0-9_]*)");

    @Test
    @DisplayName("任何库存表都不得存在 available 列")
    void 库存表不得有available列() throws IOException {
        Path root = repoRoot();
        List<String> violations = new ArrayList<>();
        int scanned = 0;

        for (Path sql : migrations(root)) {
            scanned++;
            String text = Files.readString(sql, StandardCharsets.UTF_8);
            String rel = root.relativize(sql).toString();

            Matcher t = CREATE_TABLE.matcher(text);
            while (t.find()) {
                String table = t.group(1);
                for (String column : MigrationCommentConventionTest.columnsOf(t.group(2))) {
                    if (isAvailable(column)) {
                        violations.add("%s：表 %s 定义了 %s 列".formatted(rel, table, column));
                    }
                }
            }

            Matcher a = ADD_COLUMN.matcher(text);
            while (a.find()) {
                if (isAvailable(a.group(2))) {
                    violations.add("%s：表 %s 新增了 %s 列".formatted(rel, a.group(1), a.group(2)));
                }
            }
        }

        // 反空跑：一个迁移都没扫到时，下面的断言在空集合上也成立
        assertTrue(scanned > 0, "没有扫描到任何迁移脚本——规则空跑等于没有规则");
        assertTrue(violations.isEmpty(),
                "可用量必须由 on_hand - reserved - locked 算出，不得落列：\n  "
                        + String.join("\n  ", violations));
    }

    static boolean isAvailable(String column) {
        String c = column.toLowerCase();
        return c.equals("available") || c.equals("available_qty") || c.equals("avail_qty")
                || c.equals("usable_qty") || c.equals("available_quantity");
    }

    private static List<Path> migrations(Path root) throws IOException {
        List<Path> out = new ArrayList<>();
        try (Stream<Path> modules = Files.list(root)) {
            for (Path module : modules.filter(Files::isDirectory).toList()) {
                Path mig = module.resolve("src/main/resources/db/migration");
                if (!Files.isDirectory(mig)) continue;
                try (Stream<Path> s = Files.walk(mig)) {
                    out.addAll(s.filter(p -> p.toString().endsWith(".sql")).toList());
                }
            }
        }
        return out;
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
