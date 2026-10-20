package com.xdl.blogsadmin.controller;

import com.xdl.blogscommon.jwt.JwtPrincipal;
import com.xdl.blogscommon.result.Result;
import com.xdl.blogscore.service.AuthService;
import com.xdl.model.dto.LoginRequest;
import com.xdl.model.vo.AdminUserInfo;
import com.xdl.model.vo.LoginResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口
 * <p>
 * 对应 docs/API_CONTRACT.md 6.2.1 POST /api/v1/auth/login（已冻结）
 * 与 6.2.2 GET /api/v1/auth/me（已定稿），返回统一响应包络 Result。
 *
 * @author xdl
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 管理员登录，成功后返回 JWT 与管理员信息
     *
     * @param loginRequest 登录请求体，字段校验见契约 6.2.1
     * @return token、tokenType、expiresIn 与 userInfo
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        return Result.success(authService.login(loginRequest));
    }

    /**
     * 获取当前登录管理员信息，供前端刷新页面后用本地 token 恢复登录态
     *
     * @param principal 由 JWT 过滤器写入的登录主体
     * @return 管理员信息（不含密码）
     */
    @GetMapping("/me")
    public Result<AdminUserInfo> currentAdmin(@AuthenticationPrincipal JwtPrincipal principal) {
        return Result.success(authService.getLoginAdminInfo(principal.id()));
    }
}
