package com.xdl.bologssecurity.config;

import com.xdl.bologssecurity.filter.JwtAuthenticationFilter;
import com.xdl.bologssecurity.handler.RestSecurityExceptionHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
     * 公开只读接口：契约 6.1
     */
    private static final String[] PUBLIC_GET_ENDPOINTS = {
            "/api/v1/articles/**", "/api/v1/categories/**", "/api/v1/tags/**"
    };

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
