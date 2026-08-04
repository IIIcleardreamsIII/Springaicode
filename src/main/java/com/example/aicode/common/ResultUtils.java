package com.example.ai2.common;

import com.example.ai2.common.BaseResponse;
import com.example.ai2.common.ErrorCode;

/**
 * 通用响应工具类
 */
public class ResultUtils {

    /**
     * 成功响应（带数据）
     */
    public static <T> BaseResponse<T> success(T data) {
        return new BaseResponse<>(0, data, "ok");
    }

    /**
     * 成功响应（无数据）
     */
    public static <T> BaseResponse<T> success() {
        return new BaseResponse<>(0, null, "ok");
    }

    /**
     * 错误响应（自定义状态码和消息）
     */
    public static <T> BaseResponse<T> error(int code, String message) {
        return new BaseResponse<>(code, null, message);
    }

    /**
     * 错误响应（使用 ErrorCode 枚举）
     */
    public static <T> BaseResponse<T> error(ErrorCode errorCode) {
        return new BaseResponse<>(errorCode);
    }

    /**
     * 错误响应（使用 ErrorCode + 自定义消息）
     */
    public static <T> BaseResponse<T> error(ErrorCode errorCode, String message) {
        return new BaseResponse<>(errorCode.getCode(), null, message);
    }
}