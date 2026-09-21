package com.lrj.erp.app;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ERP 启动类。
 *
 * <p>扫描根设为 {@code com.lrj.erp}，使各业务模块的 Bean 能被发现；
 * 但模块之间的依赖方向由 ArchUnit 强制（见 {@code ModuleDependencyArchitectureTest}），
 * 而不是靠"能不能扫到"来约束——组件扫描是装配机制，不是边界机制。
 *
 * <p>{@code @MapperScan} 放在这里而不是各模块内：MyBatis 的自动配置只从
 * {@code @SpringBootApplication} 所在包开始扫描，因此各模块的 Mapper 接口不会被发现。
 * 按 {@code @Mapper} 注解扫描而不是按包名约定（如 {@code **.repository}）：
 * 包名约定会在有人把 Mapper 放到别的包时静默失效，而失效表现为"Bean 不存在"，
 * 排查成本远高于多扫几个包。
 * 装配是 erp-app 的职责（它本来就是组装模块），让每个业务模块自带一份扫描配置
 * 反而会把"如何被装配"这件事散落到各处。
 */
@SpringBootApplication(scanBasePackages = "com.lrj.erp")
@MapperScan(basePackages = "com.lrj.erp", annotationClass = org.apache.ibatis.annotations.Mapper.class)
public class ErpApplication {

    public static void main(String[] args) {
        SpringApplication.run(ErpApplication.class, args);
    }
}
