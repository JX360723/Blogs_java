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
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * 认证 Service 实现类
 * <p>
 * 登录校验顺序遵循契约 6.2.1「后端落地要求」：
 * 查用户（deleted=0）→ bcrypt 比对 → 禁用返回 403 → 成功签发 token。
 * 用户名不存在与密码错误返回同一文案，避免账号枚举；日志不输出密码与 token。
 * <p>
 * 二次核准（2026-10-30）新增三处加固，详见 docs/AUTH_REVIEW_AND_PUBLIC_API.md：
 * <ol>
 *     <li>用户不存在时也执行一次 bcrypt 比对，消除「无用户快、有用户慢」的时序差异；</li>
 *     <li>用户名 trim 后重新校验长度，避免 {@code "  a  "} 这类绕过 @Size 的输入进入查询；</li>
 *     <li>role 为空时兜底为 ADMIN，避免手工插入的管理员拿到空角色导致管理端永久 403。</li>
 * </ol>
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

    /**
     * 用户名长度不符合契约时的提示，与 LoginRequest 上 @Size 的 message 保持一致
     */
    private static final String USERNAME_LENGTH_MESSAGE = "用户名长度必须在3-50位之间";

    /**
     * 契约 6.2.1：用户名去除首尾空格后长度下限
     */
    private static final int USERNAME_MIN_LENGTH = 3;

    /**
     * 契约 6.2.1：用户名长度上限
     */
    private static final int USERNAME_MAX_LENGTH = 50;

    private final AdminUserService adminUserService;

    private final PasswordEncoder passwordEncoder;

    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 占位密码哈希，仅用于「用户不存在」分支消耗与真实校验等量的 bcrypt 计算时间，
     * 不对应任何账号密码，启动时随机生成，不写入数据库也不写日志
     */
    private String dummyPasswordHash;

    @PostConstruct
    void initDummyPasswordHash() {
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Override
    public LoginResponse login(LoginRequest loginRequest) {
        String username = normalizeUsername(loginRequest.getUsername());
        AdminUser adminUser = adminUserService.getByUsername(username);
        if (adminUser == null) {
            // 用户不存在时仍做一次 bcrypt 比对，使失败响应的耗时与「密码错误」一致，
            // 避免攻击者用响应时间差判断账号是否存在
            passwordEncoder.matches(loginRequest.getPassword(), dummyPasswordHash);
            log.warn("登录失败，用户名: {}", username);
            throw new BusinessException(ResultCode.UNAUTHORIZED, LOGIN_FAILED_MESSAGE);
        }
        if (!passwordEncoder.matches(loginRequest.getPassword(), adminUser.getPassword())) {
            log.warn("登录失败，用户名: {}", username);
            throw new BusinessException(ResultCode.UNAUTHORIZED, LOGIN_FAILED_MESSAGE);
        }
        // 密码正确后才判断禁用状态：错误密码 + 禁用账号仍返回 401，不额外暴露账号状态
        if (!AdminUser.STATUS_ENABLED.equals(adminUser.getStatus())) {
            log.warn("登录被拒绝，账号已禁用，用户名: {}", username);
            throw new BusinessException(ResultCode.FORBIDDEN, ACCOUNT_DISABLED_MESSAGE);
        }
        String role = resolveRole(adminUser);
        JwtTokenProvider.IssuedToken issuedToken = jwtTokenProvider.issueToken(
                adminUser.getId(), adminUser.getUsername(), role);
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

    /**
     * 去除用户名首尾空格并校验长度
     * <p>
     * {@code @Size} 校验的是原始字符串，{@code "  a  "} 能通过 3-50 位校验但实际用户名只有 1 位，
     * 这里按契约「去除首尾空格后非空」的语义再做一次长度校验，非法输入直接 400。
     *
     * @param rawUsername 请求体中的原始用户名
     * @return 去除首尾空格后的用户名
     */
    private String normalizeUsername(String rawUsername) {
        String username = rawUsername == null ? "" : rawUsername.trim();
        if (username.length() < USERNAME_MIN_LENGTH || username.length() > USERNAME_MAX_LENGTH) {
            throw new BusinessException(ResultCode.BAD_REQUEST, USERNAME_LENGTH_MESSAGE);
        }
        return username;
    }

    /**
     * 解析角色标识，空值兜底为 ADMIN
     * <p>
     * MVP 只有管理员一种角色，手工插入数据遗漏 role 时若签发空角色，
     * 会导致该账号永远无法访问 /api/v1/admin/**，这里统一兜底。
     *
     * @param adminUser 管理员实体
     * @return 非空角色标识
     */
    private String resolveRole(AdminUser adminUser) {
        return StringUtils.hasText(adminUser.getRole()) ? adminUser.getRole() : AdminUser.ROLE_ADMIN;
    }
}
