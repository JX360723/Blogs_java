package com.xdl.blogscore.service.impl;

import com.xdl.blogscommon.exception.BusinessException;
import com.xdl.blogscommon.jwt.JwtTokenProvider;
import com.xdl.blogscommon.result.ResultCode;
import com.xdl.blogscore.service.AdminUserService;
import com.xdl.blogscore.service.AuthService;
import com.xdl.model.dto.LoginRequest;
import com.xdl.model.entity.AdminUser;
import com.xdl.model.vo.AdminUserInfo;
import com.xdl.model.vo.LoginResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 认证 Service 实现类
 * <p>
 * 登录校验顺序遵循契约 6.2.1「后端落地要求」：
 * 查用户（deleted=0）→ bcrypt 比对 → 禁用返回 403 → 成功签发 token。
 * 用户名不存在与密码错误返回同一文案，避免账号枚举；日志不输出密码与 token。
 *
 * @author xdl
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /**
     * 登录失败统一文案，防止通过响应差异枚举账号
     */
    private static final String LOGIN_FAILED_MESSAGE = "用户名或密码错误";

    /**
     * 账号禁用文案
     */
    private static final String ACCOUNT_DISABLED_MESSAGE = "账号已被禁用";

    private final AdminUserService adminUserService;

    private final PasswordEncoder passwordEncoder;

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public LoginResponse login(LoginRequest loginRequest) {
        String username = StringUtils.hasText(loginRequest.getUsername())
                ? loginRequest.getUsername().trim()
                : loginRequest.getUsername();
        AdminUser adminUser = adminUserService.getByUsername(username);
        if (adminUser == null || !passwordEncoder.matches(loginRequest.getPassword(), adminUser.getPassword())) {
            log.warn("登录失败，用户名: {}", username);
            throw new BusinessException(ResultCode.UNAUTHORIZED, LOGIN_FAILED_MESSAGE);
        }
        if (!AdminUser.STATUS_ENABLED.equals(adminUser.getStatus())) {
            log.warn("登录被拒绝，账号已禁用，用户名: {}", username);
            throw new BusinessException(ResultCode.FORBIDDEN, ACCOUNT_DISABLED_MESSAGE);
        }
        JwtTokenProvider.IssuedToken issuedToken = jwtTokenProvider.issueToken(
                adminUser.getId(), adminUser.getUsername(), adminUser.getRole());
        log.info("管理员登录成功，用户名: {}", adminUser.getUsername());
        return LoginResponse.builder()
                .token(issuedToken.token())
                .tokenType(LoginResponse.TOKEN_TYPE_BEARER)
                .expiresIn(issuedToken.expiresIn())
                .userInfo(AdminUserInfo.from(adminUser))
                .build();
    }

    @Override
    public AdminUserInfo getLoginAdminInfo(Long adminId) {
        AdminUser adminUser = adminUserService.getById(adminId);
        if (adminUser == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if (!AdminUser.STATUS_ENABLED.equals(adminUser.getStatus())) {
            throw new BusinessException(ResultCode.FORBIDDEN, ACCOUNT_DISABLED_MESSAGE);
        }
        return AdminUserInfo.from(adminUser);
    }
}
