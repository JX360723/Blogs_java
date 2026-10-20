package com.xdl.blogscommon.result;

/**
 * 统一业务状态码
 * <p>
 * 取值与 docs/API_CONTRACT.md 第 4 节错误码表一致，
 * 失败响应的 HTTP 状态码与业务 code 保持相同。
 *
 * @author xdl
 */
public enum ResultCode {

    /**
     * 成功
     */
    SUCCESS(200, "操作成功"),

    /**
     * 参数校验失败 / 业务校验失败
     */
    BAD_REQUEST(400, "请求参数有误或业务校验失败"),

    /**
     * 未认证：未携带 token、token 无效或已过期
     */
    UNAUTHORIZED(401, "未登录或登录已过期"),

    /**
     * 权限不足
     */
    FORBIDDEN(403, "权限不足"),

    /**
     * 资源不存在
     */
    NOT_FOUND(404, "资源不存在"),

    /**
     * 触发限流
     */
    TOO_MANY_REQUESTS(429, "请求过于频繁，请稍后再试"),

    /**
     * 服务端内部错误
     */
    INTERNAL_ERROR(500, "系统内部错误");

    private final int code;

    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
