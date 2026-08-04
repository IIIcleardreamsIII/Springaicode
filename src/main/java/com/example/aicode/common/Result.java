package com.example.ai2.common;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一响应对象（用于全局异常处理器返回）
 *
 * <p>与 BaseResponse 的区别：
 * <ul>
 *   <li>BaseResponse：Controller 正常返回时使用</li>
 *   <li>Result：全局异常处理器统一返回时使用</li>
 * </ul>
 *
 * @param <T> 响应数据类型
 */
@Data
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态码：200 成功，401 未登录，403 无权限，500 错误 */
    private int code;

    /** 提示信息 */
    private String message;

    /** 返回数据 */
    private T data;

    // ========== 构造方法 ==========

    public Result() {}

    public Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    // ========== 快捷静态方法 ==========

    /**
     * 成功
     */
    public static <T> Result<T> success() {
        return new Result<>(200, "success", null);
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(200, "success", data);
    }

    public static <T> Result<T> success(String message, T data) {
        return new Result<>(200, message, data);
    }

    /**
     * 失败
     */
    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }

    public static <T> Result<T> error(String message) {
        return new Result<>(500, message, null);
    }

    /**
     * 未登录
     */
    public static <T> Result<T> notLogin() {
        return new Result<>(401, "未登录", null);
    }

    /**
     * 无权限
     */
    public static <T> Result<T> noAuth() {
        return new Result<>(403, "无权限", null);
    }
}