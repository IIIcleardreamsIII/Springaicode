package com.example.aicode.common;

import lombok.Getter;

/**
 * 自定义业务异常
 *
 * <p>用于在业务逻辑中抛出带有错误码的异常，由全局异常处理器统一捕获并转换为标准响应。
 * 继承 {@link RuntimeException}，属于非受检异常，无需在方法签名中声明。
 *
 * <p>使用示例：
 * <pre>{@code
 * throw new BusinessException(ErrorCode.PARAMS_ERROR);
 * throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户名不能为空");
 * throw new BusinessException(40000, "自定义错误");
 * }</pre>
 *
 * @author AI Code
 * @see ErrorCode
 * @see GlobalExceptionHandler
 */
@Getter
public class BusinessException extends RuntimeException {

    /**
     * 错误码，对应 {@link ErrorCode#getCode()} 的值，用于标识具体的错误类型。
     */
    private final int code;

    /**
     * 错误描述信息，用于展示具体的错误原因，便于问题定位与前端提示。
     */
    private final String description;

    /**
     * 根据错误码枚举构造业务异常。
     *
     * <p>错误码和描述信息均取自枚举的默认值。
     *
     * @param errorCode 错误码枚举
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage()); // 父类 message 设为枚举默认消息
        this.code = errorCode.getCode();
        this.description = errorCode.getMessage();
    }

    /**
     * 根据错误码枚举和自定义描述构造业务异常。
     *
     * <p>状态码取自枚举，描述信息使用传入的自定义文本，便于精确定位错误原因。
     *
     * @param errorCode   错误码枚举，提供状态码
     * @param description 自定义错误描述
     */
    public BusinessException(ErrorCode errorCode, String description) {
        super(description); // 父类 message 设为自定义描述
        this.code = errorCode.getCode();
        this.description = description;
    }

    /**
     * 根据状态码和消息构造业务异常（不依赖枚举，灵活度高）
     *
     * @param code    错误状态码
     * @param message 错误描述信息
     */
    public BusinessException(int code, String message) {
        // 直接使用传入的状态码和消息
        super(message);
        this.code = code;
        this.description = message;
    }
}