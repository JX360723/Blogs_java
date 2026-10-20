package com.xdl.blogscommon.jwt;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置项
 * <p>
 * 规则见 docs/API_CONTRACT.md 6.2.1「JWT 规则（冻结）」：
 * 算法 HS256，密钥由环境变量 JWT_SECRET 注入且不少于 32 字节，有效期 24 小时。
 *
 * @author xdl
 */
@Data
@Component
@ConfigurationProperties(prefix = "blogs.jwt")
public class JwtProperties {

    /**
     * HS256 签名密钥，来自环境变量 JWT_SECRET；禁止写入代码仓库、数据库或日志
     */
    private String secret = "";

    /**
     * token 有效期（秒），契约冻结为 86400（24 小时）
     */
    private long expireSeconds = 86400L;

    /**
     * 签发者标识
     */
    private String issuer = "blogs";
}
