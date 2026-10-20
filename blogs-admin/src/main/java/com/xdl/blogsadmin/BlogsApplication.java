package com.xdl.blogsadmin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

/**
 * 博客系统启动类
 * <p>
 * 多模块工程的业务 Bean 分布在 com.xdl.blogscommon、com.xdl.bologssecurity、
 * com.xdl.blogscore 等包中，均不在启动类所在包下，因此显式扩大组件扫描范围。
 * 排除内存用户自动配置，管理员账号统一以 admin_user 表为准，避免生成随机密码账号。
 *
 * @author xdl
 */
@SpringBootApplication(scanBasePackages = "com.xdl", exclude = UserDetailsServiceAutoConfiguration.class)
public class BlogsApplication {

    public static void main(String[] args) {
        SpringApplication.run(BlogsApplication.class, args);
    }
}
