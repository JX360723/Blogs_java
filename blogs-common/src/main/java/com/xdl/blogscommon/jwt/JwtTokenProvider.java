package com.xdl.blogscommon.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * JWT 签发与校验
 * <p>
 * 采用无状态 HS256（契约决策 C1），MVP 不实现刷新 token 与服务端登出。
 * 密钥缺失或过短时启动即失败，避免以弱密钥对外提供服务；
 * 日志中不输出 token 与密钥内容。
 *
 * @author xdl
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private static final String CLAIM_USERNAME = "username";

    private static final String CLAIM_ROLE = "role";

    private static final int MIN_SECRET_BYTES = 32;

    private final JwtProperties jwtProperties;

    private SecretKey secretKey;

    @PostConstruct
    void initSecretKey() {
        String secret = jwtProperties.getSecret();
        byte[] secretBytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT 密钥未配置或长度不足，请通过环境变量 JWT_SECRET 注入不少于 " + MIN_SECRET_BYTES + " 字节的密钥");
        }
        this.secretKey = Keys.hmacShaKeyFor(secretBytes);
    }

    /**
     * 为管理员签发 token
     *
     * @param adminId  管理员 id，写入 sub claim
     * @param username 登录用户名，写入 username claim
     * @param role     角色标识，写入 role claim
     * @return token 与其有效期（秒）
     */
    public IssuedToken issueToken(Long adminId, String username, String role) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(jwtProperties.getExpireSeconds());
        String token = Jwts.builder()
                .subject(String.valueOf(adminId))
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_ROLE, role)
                .issuer(jwtProperties.getIssuer())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
        return new IssuedToken(token, jwtProperties.getExpireSeconds());
    }

    /**
     * 校验签名与有效期并解析 claims
     *
     * @param token 待校验 token
     * @return 解析成功返回登录主体；token 无效、过期或签名错误统一返回 null，由安全层回 401
     */
    public JwtPrincipal parseToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return new JwtPrincipal(Long.valueOf(claims.getSubject()),
                    claims.get(CLAIM_USERNAME, String.class),
                    claims.get(CLAIM_ROLE, String.class));
        } catch (JwtException | IllegalArgumentException exception) {
            log.debug("JWT 校验未通过: {}", exception.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * 签发结果
     *
     * @param token     JWT 字符串
     * @param expiresIn 有效期（秒）
     */
    public record IssuedToken(String token, long expiresIn) {
    }
}
