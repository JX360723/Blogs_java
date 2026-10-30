package com.xdl.blogscore.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置
 * <p>
 * 多模块工程下 Mapper 接口位于 com.xdl.blogscore.mapper，
 * 不在启动类所在包内，必须显式声明扫描路径。
 * <p>
 * 分页插件为必需项：未注册 {@link PaginationInnerInterceptor} 时 {@code page()} 不会拼 LIMIT，
 * 会一次性返回全表数据且 {@code total} 恒为 0，前端分页直接失效。
 * 注意 MyBatis-Plus 3.5.9 起 JSqlParser 支持已从 extension 拆出，
 * 需在 blogs-core 的 pom 中额外引入 {@code mybatis-plus-jsqlparser}，否则分页执行时报类缺失。
 *
 * @author xdl
 */
@Configuration
@MapperScan("com.xdl.blogscore.mapper")
public class MybatisPlusConfig {

    /**
     * 单页最大条数，与 docs/BACKEND_DESIGN_DB_AND_API.md 4.3 分页规范一致；
     * 超出时由插件截断，避免被构造超大 pageSize 拉全表
     */
    private static final long MAX_PAGE_SIZE = 100L;

    /**
     * 分页插件
     * <p>
     * 数据库为 MySQL 8.0（application-dev.yml），方言固定为 MYSQL。
     *
     * @return MyBatis-Plus 拦截器链
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor paginationInterceptor = new PaginationInnerInterceptor(DbType.MYSQL);
        paginationInterceptor.setMaxLimit(MAX_PAGE_SIZE);
        interceptor.addInnerInterceptor(paginationInterceptor);
        return interceptor;
    }
}
