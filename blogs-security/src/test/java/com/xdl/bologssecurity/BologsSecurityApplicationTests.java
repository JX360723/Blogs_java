package com.xdl.bologssecurity;

import com.xdl.blogscommon.result.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 安全状态码语义测试
 * <p>
 * 原为空壳 contextLoads，安全模块没有可启动的上下文。
 * 这里锁定认证 / 授权相关状态码的取值，它们同时被 SecurityConfig、
 * RestSecurityExceptionHandler 和前端 401 跳登录逻辑依赖，改错会导致登录态失效。
 *
 * @author xdl
 */
@DisplayName("安全状态码")
class BologsSecurityApplicationTests {

    @Test
    @DisplayName("未认证 = 401，权限不足 = 403，两者必须区分")
    void authStatusCodes() {
        assertEquals(401, ResultCode.UNAUTHORIZED.getCode());
        assertEquals(403, ResultCode.FORBIDDEN.getCode());
    }

    @Test
    @DisplayName("401 文案不提示「用户不存在」，避免账号枚举")
    void unauthorizedMessageDoesNotLeakAccountExistence() {
        String message = ResultCode.UNAUTHORIZED.getMessage();
        assertEquals("未登录或登录已过期", message);
    }
}
