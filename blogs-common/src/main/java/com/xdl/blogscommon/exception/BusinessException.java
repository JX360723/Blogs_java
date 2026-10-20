package com.xdl.blogscommon.exception;

import com.xdl.blogscommon.result.ResultCode;
import lombok.Getter;

/**
 * 业务异常
 * <p>
 * 由 Service 层主动抛出，携带业务状态码，
 * 经 GlobalExceptionHandler 转换为统一响应包络。
 *
 * @author xdl
 */
@Getter
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 业务状态码，与 HTTP 状态码一致
     */
    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(ResultCode resultCode) {
        this(resultCode.getCode(), resultCode.getMessage());
    }

    public BusinessException(ResultCode resultCode, String message) {
        this(resultCode.getCode(), message);
    }
}
