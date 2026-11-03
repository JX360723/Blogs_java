package com.xdl.blogsadmin.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xdl.bologssecurity.handler.RestSecurityExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 安全异常响应测试
 * <p>
 * 过滤器链内的认证 / 授权失败不会进入 GlobalExceptionHandler，
 * 由 RestSecurityExceptionHandler 直接写出统一包络。这里验证 401 / 403 的状态码与响应体，
 * 并确认响应中不泄露 token 或异常细节。
 *
 * @author xdl
 */
@DisplayName("安全异常响应")
class RestSecurityExceptionHandlerTest {

    private RestSecurityExceptionHandler handler;

    private MockHttpServletRequest request;

    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        handler = new RestSecurityExceptionHandler(new ObjectMapper());
        request = new MockHttpServletRequest("GET", "/api/v1/admin/articles");
        response = new MockHttpServletResponse();
    }

    @Test
    @DisplayName("未认证：HTTP 401 + Result 包络 code=401")
    void unauthorized() throws Exception {
        handler.commence(request, response, new InsufficientAuthenticationException("缺少 token"));

        String body = response.getContentAsString();
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentType() != null
                        && response.getContentType().startsWith("application/json"),
                "Content-Type 应为 application/json，实际：" + response.getContentType());
        assertTrue(body.contains("\"code\":401"), "响应体应含 code=401，实际：" + body);
        assertTrue(body.contains("\"message\":\"未登录或登录已过期\""), "响应体应含统一文案，实际：" + body);
        assertTrue(body.contains("\"data\":null"), "失败响应 data 应为 null，实际：" + body);
        assertTrue(body.contains("\"timestamp\":"), "响应体应含 timestamp，实际：" + body);
        // 不允许把异常细节回显给客户端
        assertFalse(body.contains("缺少 token"), "不应回显异常 message，实际：" + body);
    }

    @Test
    @DisplayName("权限不足：HTTP 403 + Result 包络 code=403")
    void forbidden() throws Exception {
        handler.handle(request, response, new AccessDeniedException("角色不匹配"));

        String body = response.getContentAsString();
        assertEquals(403, response.getStatus());
        assertTrue(body.contains("\"code\":403"), "响应体应含 code=403，实际：" + body);
        assertTrue(body.contains("\"message\":\"权限不足\""), "响应体应含统一文案，实际：" + body);
        assertFalse(body.contains("角色不匹配"), "不应回显异常 message，实际：" + body);
    }
}
