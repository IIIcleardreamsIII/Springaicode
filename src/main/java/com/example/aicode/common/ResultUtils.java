package com.example.aicode.common;

/**
 * 通用响应工具类
 *
 * <p>封装 {@link BaseResponse} 的构造逻辑，提供简洁的静态方法快速构建成功/失败响应。
 * Controller 层应优先使用此类返回结果，避免直接实例化 BaseResponse。
 *
 * <p>使用示例：
 * <pre>{@code
 * return ResultUtils.success(data);
 * return ResultUtils.error(ErrorCode.PARAMS_ERROR);
 * }</pre>
 *
 * @author AI Code
 * @see BaseResponse
 * @see ErrorCode
 */
public class ResultUtils {

    /**
     * 构建成功响应（带数据）。
     *
     * @param data 响应数据
     * @param <T>  响应数据类型
     * @return 成功响应对象，状态码为 0，消息为 "ok"
     */
    public static <T> BaseResponse<T> success(T data) {
        return new BaseResponse<>(0, data, "ok"); // 状态码 0 表示成功
    }

    /**
     * 构建成功响应（无数据）。
     *
     * @param <T> 响应数据类型
     * @return 成功响应对象，状态码为 0，数据为 null
     */
    public static <T> BaseResponse<T> success() {
        return new BaseResponse<>(0, null, "ok"); // 状态码 0，无数据
    }

    /**
     * 构建错误响应（自定义状态码和消息）。
     *
     * @param code    错误状态码
     * @param message 错误提示信息
     * @param <T>     响应数据类型
     * @return 错误响应对象，数据为 null
     */
    public static <T> BaseResponse<T> error(int code, String message) {
        return new BaseResponse<>(code, null, message); // 数据置空，携带错误码与消息
    }

    /**
     * 构建错误响应（使用 ErrorCode 枚举）。
     *
     * <p>状态码和消息均取自枚举的默认值。
     *
     * @param errorCode 错误码枚举
     * @param <T>       响应数据类型
     * @return 错误响应对象，数据为 null
     */
    public static <T> BaseResponse<T> error(ErrorCode errorCode) {
        return new BaseResponse<>(errorCode); // 复用 ErrorCode 构造
    }

    /**
     * 构建错误响应（使用 ErrorCode + 自定义消息）。
     *
     * <p>状态码取自枚举，消息使用传入的自定义文本。
     *
     * @param errorCode 错误码枚举，提供状态码
     * @param message   自定义错误提示信息
     * @param <T>       响应数据类型
     * @return 错误响应对象，数据为 null
     */
    public static <T> BaseResponse<T> error(ErrorCode errorCode, String message) {
        return new BaseResponse<>(errorCode.getCode(), null, message); // 状态码取自枚举，消息自定义
    }
}