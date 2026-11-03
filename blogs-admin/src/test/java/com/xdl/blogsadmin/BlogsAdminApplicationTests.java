package com.xdl.blogsadmin;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 应用上下文装配测试
 * <p>
 * 这个测试会加载完整 Spring 上下文，需要本地 MySQL 8（blogs_db）与 Redis 可用，
 * 因此默认 {@code @Disabled}，避免 CI / 无库环境跑 {@code mvn test} 时整体失败。
 * <p>
 * 本地验证上下文能否正常装配（尤其是 SecurityConfig、MyBatis-Plus 分页插件、
 * JacksonConfig 是否都注册成功）时，去掉 {@code @Disabled} 再跑：
 * <pre>
 *   export DB_PASSWORD=你的本地密码
 *   export JWT_SECRET=至少32字节的随机字符串
 *   mvn -pl blogs-admin -am test -Dtest=BlogsAdminApplicationTests
 * </pre>
 * 注意：多模块里 blogs-common / blogs-security 各残留了一个 {@code @SpringBootApplication}，
 * 已在 BlogsApplication 里用 {@code @ComponentScan} 正则排除，否则这里会报
 * {@code Found multiple @SpringBootConfiguration annotated classes}。
 *
 * @author xdl
 */
@SpringBootTest
@Disabled("需要本地 MySQL + Redis，联调时手动去掉该注解执行")
class BlogsAdminApplicationTests {

    @Test
    void contextLoads() {
    }

}
