package com.xdl.blogscommon.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

import java.io.Serializable;

/**
 * 统一响应包络 Result&lt;T&gt;
 * <p>
 * 结构见 docs/API_CONTRACT.md 第 2 节：code / message / data / timestamp。
 * 除文件流与 SSE 外，所有接口均通过本类返回，Controller 不得自行包装。
 *
 * @param <T> 业务数据类型
 * @author xdl
 */
@Getter
@JsonInclude(JsonInclude.Include.ALWAYS)
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 业务/HTTP 状态码，200 表示成功
     */
    private final int code;

    /**
     * 人类可读信息，失败时为可直接展示的错误原因
     */
    private final String message;

    /**
     * 业务数据，失败时为 null
     */
    private final T data;

    /**
     * 服务端毫秒时间戳
     */
    private final long timestamp;

    private Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }

    public static <T> Result<T> success() {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), null);
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    public static <T> Result<T> success(String message, T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), message, data);
    }

    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }

    public static <T> Result<T> error(ResultCode resultCode) {
        return new Result<>(resultCode.getCode(), resultCode.getMessage(), null);
    }

    public static <T> Result<T> error(ResultCode resultCode, String message) {
        return new Result<>(resultCode.getCode(), message, null);
    }
}
