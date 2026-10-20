package com.xdl.blogscommon.jwt;

/**
 * 从 JWT claims 解析出的登录主体
 * <p>
 * claims 约定：sub=管理员 id、username=登录名、role=角色。
 *
 * @param id       管理员 id
 * @param username 登录用户名
 * @param role     角色标识，如 ADMIN
 * @author xdl
 */
public record JwtPrincipal(Long id, String username, String role) {
}
