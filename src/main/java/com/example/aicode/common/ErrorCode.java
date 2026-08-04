package com.example.ai2.common;

import lombok.Getter;

/**
 * 统一错误码枚举
 *
 * <p>格式：模块前缀(2位) + 错误类型(2位) + 序号(1位)
 * <ul>
 *   <li>4xxxx：客户端错误（参数、权限、登录等）</li>
 *   <li>5xxxx：服务端错误（系统异常、操作失败等）</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * throw new BusinessException(ErrorCode.PARAMS_ERROR);
 * }</pre>
 */
@Getter
public enum ErrorCode {

    /** 成功 */
    SUCCESS(0, "ok"),

    /** 请求参数错误 */
    PARAMS_ERROR(40000, "请求参数错误"),

    /** 未登录 */
    NOT_LOGIN_ERROR(40100, "未登录"),

    /** 无权限 */
    NO_AUTH_ERROR(40101, "无权限"),

    /** 请求数据不存在 */
    NOT_FOUND_ERROR(40400, "请求数据不存在"),

    /** 禁止访问 */
    FORBIDDEN_ERROR(40300, "禁止访问"),

    /** 系统内部异常 */
    SYSTEM_ERROR(50000, "系统内部异常"),

    /** 操作失败 */
    OPERATION_ERROR(50001, "操作失败");

    /** 错误码 */
    private final int code;

    /** 错误信息 */
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}