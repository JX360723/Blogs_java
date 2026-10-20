package com.xdl.blogscore.service;

import com.xdl.model.dto.LoginRequest;
import com.xdl.model.vo.AdminUserInfo;
import com.xdl.model.vo.LoginResponse;

/**
 * 认证 Service 接口
 * <p>
 * 对应契约 6.2.1 管理员登录与 6.2.2 当前管理员信息。
 *
 * @author xdl
 */
public interface AuthService {

    /**
     * 管理员登录：校验用户名密码后签发 JWT
     *
     * @param loginRequest 登录请求体
     * @return token 与管理员信息
     */
    LoginResponse login(LoginRequest loginRequest);

    /**
     * 获取当前登录管理员信息，用于前端刷新页面后恢复登录态
     *
     * @param adminId 来自 JWT sub claim 的管理员 id
     * @return 管理员信息（不含密码）
     */
    AdminUserInfo getLoginAdminInfo(Long adminId);
}
