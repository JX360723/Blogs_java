package com.xdl.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.io.Serializable;

/**
 * 管理员登录请求体
 * <p>
 * 校验规则见 docs/API_CONTRACT.md 6.2.1：用户名长度 3-50 且去除首尾空格后非空，
 * 密码长度 6-64 且不做去空格处理。
 *
 * @author xdl
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 登录用户名
     */
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50, message = "用户名长度必须在3-50位之间")
    private String username;

    /**
     * 登录密码，仅用于服务端 bcrypt 比对，不参与 toString 输出
     */
    @ToString.Exclude
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度必须在6-64位之间")
    private String password;
}
