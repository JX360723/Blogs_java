package com.xdl.blogscommon.exception;

import com.xdl.blogscommon.result.Result;
import com.xdl.blogscommon.result.ResultCode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Objects;

/**
 * 全局异常处理器
 * <p>
 * 统一把异常转换为 docs/API_CONTRACT.md 第 2、4 节约定的响应包络，
 * 保证 HTTP 状态码与业务 code 一致；对外不泄露堆栈与内部细节。
 *
 * @author xdl
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常：登录失败、账号禁用等由 Service 层主动抛出的场景
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException exception) {
        log.warn("业务异常: code={}, message={}", exception.getCode(), exception.getMessage());
        return ResponseEntity.status(resolveStatus(exception.getCode()))
                .body(Result.error(exception.getCode(), exception.getMessage()));
    }

    /**
     * 请求体参数校验失败（@Valid @RequestBody），返回首个字段的具体错误信息
     */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<Result<Void>> handleBindException(BindException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(ResultCode.BAD_REQUEST.getMessage());
        log.warn("参数校验失败: {}", message);
        return buildResponse(ResultCode.BAD_REQUEST, message);
    }

    /**
     * 单参数约束校验失败（@Validated 修饰的查询参数）
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolationException(ConstraintViolationException exception) {
        String message = exception.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(ResultCode.BAD_REQUEST.getMessage());
        log.warn("参数约束校验失败: {}", message);
        return buildResponse(ResultCode.BAD_REQUEST, message);
    }

    /**
     * 缺少必填查询参数
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<Void>> handleMissingParameterException(MissingServletRequestParameterException exception) {
        String message = "缺少必要参数: " + exception.getParameterName();
        log.warn("缺少必要参数: {}", exception.getParameterName());
        return buildResponse(ResultCode.BAD_REQUEST, message);
    }

    /**
     * 请求体无法解析（非法 JSON 等），不回显原始报文，避免泄露输入内容
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleHttpMessageNotReadableException(HttpMessageNotReadableException exception) {
        log.warn("请求体解析失败: {}", exception.getClass().getSimpleName());
        return buildResponse(ResultCode.BAD_REQUEST, "请求体格式错误，无法解析");
    }

    /**
     * 资源不存在：路径未匹配任何处理器或静态资源，统一返回 404，
     * 避免被兜底处理器误判为 500
     */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<Result<Void>> handleNotFoundException(Exception exception) {
        log.warn("资源不存在: {}", exception.getClass().getSimpleName());
        return buildResponse(ResultCode.NOT_FOUND, ResultCode.NOT_FOUND.getMessage());
    }

    /**
     * 请求方法不支持：契约第 4 节未定义 405，归入 400 参数类错误
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotSupportedException(HttpRequestMethodNotSupportedException exception) {
        String message = "请求方法不支持: " + exception.getMethod();
        log.warn("请求方法不支持: {}", exception.getMethod());
        return buildResponse(ResultCode.BAD_REQUEST, message);
    }

    /**
     * 兜底处理：未捕获异常统一返回 500，堆栈只写日志不外泄
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception exception) {
        log.error("未捕获异常", exception);
        return buildResponse(ResultCode.INTERNAL_ERROR, ResultCode.INTERNAL_ERROR.getMessage());
    }

    private ResponseEntity<Result<Void>> buildResponse(ResultCode resultCode, String message) {
        return ResponseEntity.status(resultCode.getCode())
                .body(Result.error(resultCode.getCode(), message));
    }

    private HttpStatusCode resolveStatus(int code) {
        if (code >= 100 && code <= 599) {
            return HttpStatusCode.valueOf(code);
        }
        return HttpStatusCode.valueOf(ResultCode.INTERNAL_ERROR.getCode());
    }
}
