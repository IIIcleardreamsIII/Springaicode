package com.example.ai2.common;

import lombok.Getter;

/**
 * 自定义业务异常
 */
@Getter
public class BusinessException extends RuntimeException {

    /**
     * 错误码
     */
    private final int code;

    /**
     * 错误信息
     */
    private final String description;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.description = errorCode.getMessage();
    }

    public BusinessException(ErrorCode errorCode, String description) {
        super(description);
        this.code = errorCode.getCode();
        this.description = description;
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
        this.description = message;
    }
}