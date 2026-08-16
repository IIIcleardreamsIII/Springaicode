package com.example.aicode.common;



/**
 * 断言工具类
 *
 * <p>提供简洁的断言方法，条件成立时抛出 BusinessException。
 * 用于 Controller 层参数校验。
 *
 * <p>使用示例：
 * <pre>{@code
 * ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
 * ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR, "ID 无效");
 * }</pre>
 *
 * @author AI Code
 * @see BusinessException
 * @see ErrorCode
 */
public class ThrowUtils {

    /**
     * 条件成立时抛出业务异常（使用 ErrorCode 中的默认消息）。
     *
     * @param condition 条件表达式，为 true 时抛出异常
     * @param errorCode 错误码枚举
     * @throws BusinessException 当 condition 为 true 时抛出
     */
    public static void throwIf(boolean condition, ErrorCode errorCode) {
        if (condition) {
            // 条件成立，抛出携带默认消息的业务异常
            throw new BusinessException(errorCode);
        }
    }

    /**
     * 条件成立时抛出业务异常（自定义消息）
     *
     * @param condition   条件表达式，为 true 时抛出异常
     * @param errorCode   错误码枚举
     * @param description 自定义错误描述
     * @throws BusinessException 当 condition 为 true 时抛出
     */
    public static void throwIf(boolean condition, ErrorCode errorCode, String description) {
        if (condition) {
            // 条件成立，抛出携带自定义描述的业务异常
            throw new BusinessException(errorCode, description);
        }
    }
}