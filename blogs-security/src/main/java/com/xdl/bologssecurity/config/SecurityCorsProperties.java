package com.xdl.bologssecurity.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 跨域配置项
 * <p>
 * 前端本地开发默认通过 Vite proxy 转发，无需跨域；
 * 若前端直连后端端口，可通过 blogs.cors.allowed-origins 显式放开来源。
 *
 * @author xdl
 */
@Data
@Component
@ConfigurationProperties(prefix = "blogs.cors")
public class SecurityCorsProperties {

    /**
     * 允许的前端来源列表
     */
    private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:5173"));
}
