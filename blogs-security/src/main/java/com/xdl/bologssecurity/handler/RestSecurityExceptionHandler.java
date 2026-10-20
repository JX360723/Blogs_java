package com.xdl.bologssecurity.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xdl.blogscommon.result.Result;
import com.xdl.blogscommon.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 安全异常响应处理器
 * <p>
 * 过滤器链内的认证/授权失败不会进入 GlobalExceptionHandler，
 * 这里直接写出统一响应包络：未认证 401、权限不足 403，
 * 且不回显 token 或异常细节，避免泄露凭证信息。
 *
 * @author xdl
 */
@Component
@RequiredArgsConstructor
public class RestSecurityExceptionHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        writeError(response, ResultCode.UNAUTHORIZED);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        writeError(response, ResultCode.FORBIDDEN);
    }

    private void writeError(HttpServletResponse response, ResultCode resultCode) throws IOException {
        response.setStatus(resultCode.getCode());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(Result.error(resultCode)));
    }
}
