package com.xdl.blogscore.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置
 * <p>
 * 多模块工程下 Mapper 接口位于 com.xdl.blogscore.mapper，
 * 不在启动类所在包内，必须显式声明扫描路径。
 *
 * @author xdl
 */
@Configuration
@MapperScan("com.xdl.blogscore.mapper")
public class MybatisPlusConfig {
}
