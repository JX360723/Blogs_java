package com.xdl.bologssecurity.config;

import com.xdl.bologssecurity.filter.JwtAuthenticationFilter;
import com.xdl.bologssecurity.handler.RestSecurityExceptionHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security 配置
 * <p>
 * 无状态 JWT 认证（契约决策 C1）：不创建 session、不使用表单登录与 HTTP Basic。
 * 公开接口（契约 6.1）匿名可访问，/api/v1/admin/** 必须是管理员角色，
 * 其余接口（含 GET /api/v1/auth/me）必须携带有效 token。
 *
 * @author xdl
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /**
     * 管理员角色名，与 JWT 的 role claim 一致
     */
    private static final String ROLE_ADMIN = "ADMIN";

    /**
     * 登录接口：契约 6.2.1，公开访问
     */
    private static final String LOGIN_ENDPOINT = "/api/v1/auth/login";

    /**
     * 管理端接口：契约 6.2，服务端强制校验管理员角色
     */
    private static final String ADMIN_ENDPOINT_PATTERN = "/api/v1/admin/**";

    /**
     * 公开只读接口：契约 6.1；uploads 是上传图片的静态资源映射，
     * 不放行的话访客端所有文章封面/配图都会 401
     */
    private static final String[] PUBLIC_GET_ENDPOINTS = {
            "/api/v1/articles/**", "/api/v1/categories/**", "/api/v1/tags/**"
    };

    /**
     * 上传图片的静态资源路径：由独立过滤链处理（全放行 + 关闭 Security 的缓存头覆盖）
     */
    private static final String UPLOADS_PATTERN = "/uploads/**";

    /**
     * 基础设施端点：健康检查与容器错误转发
     */
    private static final String[] INFRA_ENDPOINTS = {"/actuator/health", "/error"};

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    private final RestSecurityExceptionHandler restSecurityExceptionHandler;

    private final SecurityCorsProperties securityCorsProperties;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(restSecurityExceptionHandler)
                        .accessDeniedHandler(restSecurityExceptionHandler))
                .authorizeHttpRequests(registry -> registry
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, LOGIN_ENDPOINT).permitAll()
                        .requestMatchers(INFRA_ENDPOINTS).permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET_ENDPOINTS).permitAll()
                        .requestMatchers(ADMIN_ENDPOINT_PATTERN).hasRole(ROLE_ADMIN)
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * 上传图片的专用过滤链（排在主链之前）
     * <p>
     * 两件事必须独立成链：
     * <ol>
     *   <li>图片是公开内容，不需要认证，也就不该走 JWT 过滤器；</li>
     *   <li><b>关闭 Security 的缓存头写入</b>——Security 默认给所有响应写
     *       {@code Cache-Control: no-cache, no-store, max-age=0, must-revalidate}，
     *       而且是在过滤链结束时才写，会覆盖 {@code WebMvcUploadConfig} 里配的
     *       365 天 immutable。图片带 UUID 文件名、内容永不变化，长缓存才能省带宽。</li>
     * </ol>
     *
     * @param http 安全构建器
     * @return 仅作用于 /uploads/** 的过滤链
     */
    @Bean
    @Order(1)
    public SecurityFilterChain uploadsSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher(UPLOADS_PATTERN)
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(registry -> registry.anyRequest().permitAll())
                .headers(headers -> headers.cacheControl(cache -> cache.disable()));
        return http.build();
    }

    /**
     * 密码编码器：契约要求 bcrypt 哈希，禁止明文与可逆加密
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(securityCorsProperties.getAllowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
