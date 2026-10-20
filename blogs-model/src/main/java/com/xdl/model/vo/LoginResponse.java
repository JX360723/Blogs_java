package com.xdl.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 登录成功响应
 * <p>
 * 结构见 docs/API_CONTRACT.md 6.2.1：token / tokenType / expiresIn / userInfo。
 *
 * @author xdl
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * token 类型固定为 Bearer
     */
    public static final String TOKEN_TYPE_BEARER = "Bearer";

    /**
     * JWT 字符串，后续请求通过 Authorization: Bearer 携带
     */
    private String token;

    /**
     * token 类型
     */
    private String tokenType;

    /**
     * 有效期（秒），契约为 86400
     */
    private Long expiresIn;

    /**
     * 登录管理员信息
     */
    private AdminUserInfo userInfo;
}
