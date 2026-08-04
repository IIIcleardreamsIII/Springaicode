package com.example.ai2.common;

import lombok.Data;

import java.io.Serializable;

/**
 * 通用响应基类，Controller 层统一返回格式
 *
 * <p>使用示例：
 * <pre>{@code
 * // 成功
 * return new BaseResponse<>(0, data, "ok");
 * // 失败
 * return new BaseResponse<>(ErrorCode.PARAMS_ERROR);
 * }</pre>
 *
 * @param <T> 响应数据类型
 */
@Data
public class BaseResponse<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态码：0 成功，其他为错误码 */
    private int code;

    /** 响应数据 */
    private T data;

    /** 提示信息 */
    private String message;

    /**
     * 完整构造
     *
     * @param code    状态码
     * @param data    响应数据
     * @param message 提示信息
     */
    public BaseResponse(int code, T data, String message) {
        this.code = code;
        this.data = data;
        this.message = message;
    }

    /**
     * 无消息构造（消息默认为空字符串）
     */
    public BaseResponse(int code, T data) {
        this(code, data, "");
    }

    /**
     * 通过 ErrorCode 枚举构造
     */
    public BaseResponse(ErrorCode errorCode) {
        this(errorCode.getCode(), null, errorCode.getMessage());
    }
}