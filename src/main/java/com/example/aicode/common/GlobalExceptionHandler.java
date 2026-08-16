package com.example.aicode.common;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// ============================================================
// @RestControllerAdvice 与 @ExceptionHandler 科普（请先读这里）
// ------------------------------------------------------------
// 传统做法：每个 Controller 方法里写一大堆 try-catch，既丑又重复。
// Spring 的方案：写一个"全局异常处理器"，用 @RestControllerAdvice 标记，
// 再用 @ExceptionHandler(某异常类.class) 标注方法，声明"遇到这类异常就由我处理"。
// 匹配规则是"就近原则"：异常类型越具体越优先，例如 BusinessException 优先于 Exception。
// 处理函数返回的 Result 会被直接序列化成 HTTP 响应体（因为带了 @RestControllerAdvice）。
// ============================================================

/**
 * 全局异常处理器
 * <p>
 * 基于 Spring 的 {@link RestControllerAdvice} 实现，集中捕获 Controller 层抛出的异常，
 * 统一转换成标准的 {@link Result} 响应，避免在每个接口里重复 try-catch。
 *
 * @author AI Code
 * @see BusinessException
 * @see Result
 */
@Hidden // 不纳入 Swagger 文档
@RestControllerAdvice // = @ControllerAdvice + @ResponseBody，专门处理 @RestController 的异常
public class GlobalExceptionHandler {

    /**
     * 处理 {@link BusinessException}（我们主动抛出的业务异常，如参数错误、无权限）。
     *
     * @param e 业务异常对象（内含自定义 code 与 message）
     * @return 统一错误响应，状态码/消息取自异常
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        // 用异常里携带的 code 和 message 构造标准响应
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 兜底处理所有未被上述具体处理器捕获的异常（如空指针、IO 异常等）。
     * 注意：把原始异常信息直接返回给前端不利于安全，生产环境应考虑只返回通用文案。
     *
     * @param e 任意异常对象
     * @return 500 错误统一响应
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        return Result.error(500, "系统错误：" + e.getMessage());
    }
}