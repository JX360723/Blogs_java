package com.xdl.blogsadmin.unit;

import com.xdl.blogscommon.exception.BusinessException;
import com.xdl.blogscommon.jwt.JwtTokenProvider;
import com.xdl.blogscore.service.AdminUserService;
import com.xdl.blogscore.service.impl.AuthServiceImpl;
import com.xdl.model.dto.LoginRequest;
import com.xdl.model.entity.AdminUser;
import com.xdl.model.vo.LoginResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 认证 Service 单元测试
 * <p>
 * 纯 Mockito，不启动 Spring 上下文、不连数据库，可在任何环境（含 CI）直接跑。
 * 覆盖契约 6.2.1 的登录校验顺序：查用户 → bcrypt 比对 → 禁用判断 → 签发 token。
 *
 * @author xdl
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("认证 Service")
class AuthServiceImplTest {

    private static final String RAW_PASSWORD = "admin123456";

    private static final String BCRYPT_HASH = "$2a$10$abcdefghijklmnopqrstuv";

    @Mock
    private AdminUserService adminUserService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AuthServiceImpl authService;

    private static LoginRequest request(String username, String password) {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername(username);
        loginRequest.setPassword(password);
        return loginRequest;
    }

    private static AdminUser adminUser(Integer status, String role) {
        return AdminUser.builder()
                .id(1L)
                .username("admin")
                .password(BCRYPT_HASH)
                .nickname("系统管理员")
                .role(role)
                .status(status)
                .build();
    }

    @Test
    @DisplayName("登录成功：返回 token、Bearer 类型、有效期与管理员信息")
    void loginSuccess() {
        when(adminUserService.getByUsername("admin"))
                .thenReturn(adminUser(AdminUser.STATUS_ENABLED, AdminUser.ROLE_ADMIN));
        when(passwordEncoder.matches(RAW_PASSWORD, BCRYPT_HASH)).thenReturn(true);
        when(jwtTokenProvider.issueToken(1L, "admin", AdminUser.ROLE_ADMIN))
                .thenReturn(new JwtTokenProvider.IssuedToken("jwt-token-value", 86400L));

        LoginResponse response = authService.login(request("admin", RAW_PASSWORD));

        assertEquals("jwt-token-value", response.getToken());
        assertEquals(LoginResponse.TOKEN_TYPE_BEARER, response.getTokenType());
        assertEquals(Long.valueOf(86400L), response.getExpiresIn());
        assertEquals("admin", response.getUserInfo().getUsername());
        assertEquals("系统管理员", response.getUserInfo().getNickname());
    }

    @Test
    @DisplayName("用户不存在：返回 401，且仍执行一次 bcrypt 比对以消除时序差异")
    void loginUserNotFound() {
        when(adminUserService.getByUsername("ghost")).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.login(request("ghost", RAW_PASSWORD)));

        assertEquals(401, exception.getCode());
        assertEquals("用户名或密码错误", exception.getMessage());
        // 时序防护：用户不存在时也必须消耗一次 bcrypt 计算，否则可通过响应耗时枚举账号
        verify(passwordEncoder).matches(eq(RAW_PASSWORD), any());
        verify(jwtTokenProvider, never()).issueToken(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("密码错误：返回 401，文案与用户不存在完全一致（防账号枚举）")
    void loginWrongPassword() {
        when(adminUserService.getByUsername("admin"))
                .thenReturn(adminUser(AdminUser.STATUS_ENABLED, AdminUser.ROLE_ADMIN));
        when(passwordEncoder.matches(RAW_PASSWORD, BCRYPT_HASH)).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.login(request("admin", RAW_PASSWORD)));

        assertEquals(401, exception.getCode());
        assertEquals("用户名或密码错误", exception.getMessage());
        verify(jwtTokenProvider, never()).issueToken(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("账号禁用：密码正确后返回 403")
    void loginDisabledAccount() {
        when(adminUserService.getByUsername("admin"))
                .thenReturn(adminUser(AdminUser.STATUS_DISABLED, AdminUser.ROLE_ADMIN));
        when(passwordEncoder.matches(RAW_PASSWORD, BCRYPT_HASH)).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.login(request("admin", RAW_PASSWORD)));

        assertEquals(403, exception.getCode());
        assertEquals("账号已被禁用", exception.getMessage());
    }

    @Test
    @DisplayName("用户名 trim 后长度不足：返回 400，不进查询")
    void loginUsernameTooShortAfterTrim() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.login(request("  ab  ", RAW_PASSWORD)));

        assertEquals(400, exception.getCode());
        assertEquals("用户名长度必须在3-50位之间", exception.getMessage());
        verify(adminUserService, never()).getByUsername(anyString());
    }

    @Test
    @DisplayName("role 为空：签发 token 时兜底为 ADMIN，避免管理端永久 403")
    void loginRoleFallbackToAdmin() {
        when(adminUserService.getByUsername("admin"))
                .thenReturn(adminUser(AdminUser.STATUS_ENABLED, null));
        when(passwordEncoder.matches(RAW_PASSWORD, BCRYPT_HASH)).thenReturn(true);
        when(jwtTokenProvider.issueToken(1L, "admin", AdminUser.ROLE_ADMIN))
                .thenReturn(new JwtTokenProvider.IssuedToken("jwt-token-value", 86400L));

        LoginResponse response = authService.login(request("admin", RAW_PASSWORD));

        assertEquals("jwt-token-value", response.getToken());
        verify(jwtTokenProvider).issueToken(1L, "admin", AdminUser.ROLE_ADMIN);
    }
}
