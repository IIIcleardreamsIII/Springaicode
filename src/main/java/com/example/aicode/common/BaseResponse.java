package com.example.aicode.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 通用响应基类，Controller 层统一返回格式。
 *
 * <p>该类是整个系统对外接口的统一返回包装对象，所有 Controller 方法
 * 均应返回 {@link BaseResponse} 或其子类，以保证响应结构的一致性。
 *
 * <p>响应结构包含三个核心字段：
 * <ul>
 *   <li>{@link #code}：状态码，0 表示成功，其他值为错误码</li>
 *   <li>{@link #data}：响应数据，泛型类型由调用方指定</li>
 *   <li>{@link #message}：提示信息，用于向前端展示</li>
 * </ul>
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
 * @author AI Code
 * @see ErrorCode
 * @see ResultUtils
 */
@Data
@Schema(description = "通用响应")
public class BaseResponse<T> implements Serializable {

    /**
     * 序列化版本号，用于保证序列化与反序列化的版本兼容性。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 状态码：0 表示成功，其他值为对应的错误码。
     */
    @Schema(description = "状态码：0 成功，其他为错误码", example = "0")
    private int code;

    /**
     * 响应数据，泛型类型由调用方在构造时指定，成功时承载业务数据。
     */
    @Schema(description = "响应数据")
    private T data;

    /**
     * 提示信息，通常用于向前端展示操作结果或错误原因。
     */
    @Schema(description = "提示信息", example = "ok")
    private String message;

    /**
     * 完整构造方法，显式指定所有字段。
     *
     * @param code    状态码，0 为成功，其他为错误码
     * @param data    响应数据，可为 null
     * @param message 提示信息
     */
    public BaseResponse(int code, T data, String message) {
        this.code = code;       // 设置状态码
        this.data = data;       // 设置响应数据
        this.message = message; // 设置提示信息
    }

    /**
     * 无消息构造方法，消息默认为空字符串。
     *
     * @param code 状态码，0 为成功，其他为错误码
     * @param data 响应数据，可为 null
     */
    public BaseResponse(int code, T data) {
        this(code, data, ""); // 复用完整构造，消息置空
    }

    /**
     * 通过 {@link ErrorCode} 枚举构造失败响应。
     *
     * <p>状态码和提示信息取自枚举，响应数据置为 null。
     *
     * @param errorCode 错误码枚举，从中获取状态码和默认消息
     */
    public BaseResponse(ErrorCode errorCode) {
        this(errorCode.getCode(), null, errorCode.getMessage()); // data 置空
    }
}