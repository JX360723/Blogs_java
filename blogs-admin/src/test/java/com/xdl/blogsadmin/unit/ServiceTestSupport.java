package com.xdl.blogsadmin.unit;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.xdl.model.entity.Article;
import com.xdl.model.entity.Category;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Service 层单元测试基类
 * <p>
 * 这类测试刻意不启动 Spring 容器（秒级完成、不需要数据库），代价是两个问题必须手工补齐，
 * 否则测试会因为框架初始化缺失而全线飘红，看起来像业务代码写错了：
 * <ol>
 *     <li><b>{@code baseMapper} 为空</b>：{@code ServiceImpl} 把 {@code baseMapper} 声明在父类，
 *     而 {@code @InjectMocks} 在有 {@code final} 字段时会走构造器注入，父类字段不会被赋值。
 *     于是 {@code getById} / {@code save} / {@code page} 这些继承来的方法一调就抛
 *     {@code MybatisPlusException: baseMapper can not be null}。</li>
 *     <li><b>lambda 缓存为空</b>：{@code Wrappers.lambdaQuery(Article.class)} 需要 MyBatis-Plus
 *     的实体元数据缓存，而该缓存只在 Spring 启动或 Mapper 注册时填充。没有容器时直接抛
 *     {@code can not find lambda cache for this entity}。</li>
 * </ol>
 *
 * @author xdl
 */
abstract class ServiceTestSupport {

    /**
     * 注册实体元数据，让 lambda 条件构造器能解析字段
     * <p>
     * {@code @BeforeAll} 只在本类执行一次，子类共享同一份缓存。
     */
    @BeforeAll
    static void initTableInfoCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Article.class);
        TableInfoHelper.initTableInfo(assistant, Category.class);
    }

    /**
     * 把 Mock 的 Mapper 注入到 {@code ServiceImpl} 的父类字段
     *
     * @param service 被测 Service 实例
     * @param mapper  Mock 的 Mapper
     */
    protected static void injectBaseMapper(Object service, Object mapper) {
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
    }
}
