package com.lrj.erp.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ERP 启动类。
 *
 * <p>扫描根设为 {@code com.lrj.erp}，使各业务模块的 Bean 能被发现；
 * 但模块之间的依赖方向由 ArchUnit 强制（见 {@code ModuleDependencyArchitectureTest}），
 * 而不是靠"能不能扫到"来约束——组件扫描是装配机制，不是边界机制。
 */
@SpringBootApplication(scanBasePackages = "com.lrj.erp")
public class ErpApplication {

    public static void main(String[] args) {
        SpringApplication.run(ErpApplication.class, args);
    }
}
