package com.xdl.blogsadmin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * 博客系统启动类
 * <p>
 * 多模块工程的业务 Bean 分布在 com.xdl.blogscommon、com.xdl.bologssecurity、
 * com.xdl.blogscore 等包中，均不在启动类所在包下，因此显式扩大组件扫描范围。
 * 排除内存用户自动配置，管理员账号统一以 admin_user 表为准，避免生成随机密码账号。
 * <p>
 * 库模块（blogs-common / blogs-security）里残留了 Spring Initializr 生成的
 * {@code @SpringBootApplication} 启动类，全包扫描会把它们一并扫进来，造成自动配置重复执行；
 * 更严重的是 {@code @SpringBootTest} 会搜到多个 {@code @SpringBootConfiguration}，
 * 直接抛 {@code Found multiple @SpringBootConfiguration annotated classes}，测试全部起不来。
 * <p>
 * 这里用显式的 {@code @ComponentScan} + 正则排除 {@code *Application} 类来规避，零删除。
 * 彻底方案是删掉这两个冗余启动类，待负责人授权后执行（见 docs/KNOWN_ISSUES.md P0-2）。
 *
 * @author xdl
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ComponentScan(
        basePackages = "com.xdl",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.xdl\\.blogscommon\\.BlogsCommonApplication"
                        + "|com\\.xdl\\.bologssecurity\\.BologsSecurityApplication"
        )
)
public class BlogsApplication {

    public static void main(String[] args) {
        SpringApplication.run(BlogsApplication.class, args);
    }
}
