package com.example.aicode.common;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一响应对象（用于全局异常处理器返回）。
 *
 * <p>与 BaseResponse 的区别：
 * <ul>
 *   <li>BaseResponse：Controller 正常返回时使用，状态码 0 表示成功</li>
 *   <li>Result：全局异常处理器统一返回时使用，状态码遵循 HTTP 语义（200/401/403/500）</li>
 * </ul>
 *
 * <p>该类提供了一系列快捷静态方法，便于快速构建成功、失败、未登录、无权限等响应。
 *
 * @param <T> 响应数据类型
 * @author AI Code
 * @see BaseResponse
 * @see GlobalExceptionHandler
 */
@Data
public class Result<T> implements Serializable {

    /**
     * 序列化版本号，用于保证序列化与反序列化的版本兼容性。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 状态码：200 成功，401 未登录，403 无权限，500 错误。
     */
    private int code;

    /**
     * 提示信息，用于向前端展示操作结果或错误原因。
     */
    private String message;

    /**
     * 返回数据，泛型类型由调用方指定。
     */
    private T data;

    // ========== 构造方法 ==========

    /**
     * 默认无参构造方法，字段保持默认值。
     */
    public Result() {}

    /**
     * 全参构造方法，显式指定所有字段。
     *
     * @param code    状态码
     * @param message 提示信息
     * @param data    返回数据，可为 null
     */
    public Result(int code, String message, T data) {
        this.code = code;       // 设置状态码
        this.message = message; // 设置提示信息
        this.data = data;       // 设置返回数据
    }

    // ========== 快捷静态方法 ==========

    /**
     * 构建成功响应（无数据）。
     *
     * @param <T> 响应数据类型
     * @return 成功响应对象，状态码为 200，无返回数据
     */
    public static <T> Result<T> success() {
        return new Result<>(200, "success", null); // 状态码 200，无数据
    }

    /**
     * 构建成功响应（带数据）。
     *
     * @param data 响应数据
     * @param <T>  响应数据类型
     * @return 成功响应对象，状态码为 200，携带传入的数据
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(200, "success", data); // 状态码 200，携带数据
    }

    /**
     * 构建成功响应（自定义消息和数据）。
     *
     * @param message 自定义提示信息
     * @param data    响应数据
     * @param <T>     响应数据类型
     * @return 成功响应对象，状态码为 200，携带自定义消息和数据
     */
    public static <T> Result<T> success(String message, T data) {
        return new Result<>(200, message, data); // 状态码 200，自定义消息与数据
    }

    /**
     * 构建失败响应（自定义状态码和消息）。
     *
     * @param code    错误状态码
     * @param message 错误提示信息
     * @param <T>     响应数据类型
     * @return 失败响应对象，数据为 null
     */
    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null); // 失败响应，数据置空
    }

    /**
     * 构建失败响应（默认 500 状态码，自定义消息）。
     *
     * @param message 错误提示信息
     * @param <T>     响应数据类型
     * @return 失败响应对象，状态码为 500
     */
    public static <T> Result<T> error(String message) {
        return new Result<>(500, message, null); // 默认 500 错误
    }

    /**
     * 构建未登录响应（状态码 401）。
     *
     * @param <T> 响应数据类型
     * @return 未登录响应对象，状态码为 401
     */
    public static <T> Result<T> notLogin() {
        return new Result<>(401, "未登录", null); // 401 未登录
    }

    /**
     * 构建无权限响应（状态码 403）。
     *
     * @param <T> 响应数据类型
     * @return 无权限响应对象，状态码为 403
     */
    public static <T> Result<T> noAuth() {
        return new Result<>(403, "无权限", null); // 403 无权限
    }
}