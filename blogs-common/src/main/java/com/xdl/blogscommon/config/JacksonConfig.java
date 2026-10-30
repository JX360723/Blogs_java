package com.xdl.blogscommon.config;

import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 全局 Jackson 时间格式配置
 * <p>
 * 对应 docs/BACKEND_DESIGN_DB_AND_API.md 第五节：契约示例中的时间是
 * {@code 2026-10-01 10:00:00}（空格分隔），而 Spring Boot 3 默认把 {@link LocalDateTime}
 * 序列化为 ISO-8601 带 T 的 {@code 2026-10-01T10:00:00}。
 * 前后端展示与解析都按空格格式，因此这里统一序列化/反序列化格式。
 * <p>
 * 注意 {@code spring.jackson.date-format} 只作用于 {@link java.util.Date}，对 {@code java.time}
 * 类型无效，必须自定义 serializer。
 *
 * @author xdl
 */
@Configuration
public class JacksonConfig {

    /**
     * 统一日期时间格式：yyyy-MM-dd HH:mm:ss
     */
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 统一日期格式：yyyy-MM-dd
     */
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 注册 java.time 类型的序列化器与反序列化器
     * <p>
     * 只覆盖 java.time 类型，不影响 Long/Integer 等数值与字符串字段，
     * 因此响应包络里的 timestamp（毫秒数）仍保持数值类型，前端可直接 new Date(ts)。
     *
     * @return Jackson 定制器
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer javaTimeCustomizer() {
        return builder -> builder
                .serializerByType(LocalDateTime.class, new LocalDateTimeSerializer(DATE_TIME_FORMATTER))
                .deserializerByType(LocalDateTime.class, new LocalDateTimeDeserializer(DATE_TIME_FORMATTER))
                .serializerByType(LocalDate.class, new LocalDateSerializer(DATE_FORMATTER))
                .deserializerByType(LocalDate.class, new LocalDateDeserializer(DATE_FORMATTER));
    }
}
