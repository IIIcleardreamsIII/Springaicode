package com.example.aicode.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限校验注解。
 *
 * <p>该注解用于在 Controller 层的方法上声明访问该方法所需的角色权限。
 * 配合 {@link com.example.aicode.aop.AuthInterceptor} 切面使用，
 * 切面会在目标方法执行前拦截，并校验当前登录用户是否具备注解中指定的角色。</p>
 *
 * <p>使用示例：
 * <pre>{@code
 * @AuthCheck(mustRole = "admin")
 * public BaseResponse<User> getUserById(long id) {
 *     // 仅管理员角色可访问
 * }
 * }</pre>
 *
 * <p>注解元信息说明：
 * <ul>
 *   <li>{@link Target} 指定该注解只能作用于方法（{@link ElementType#METHOD}）上；</li>
 *   <li>{@link Retention} 指定为运行时保留（{@link RetentionPolicy#RUNTIME}），
 *       以便 AOP 切面在运行时通过反射读取注解信息。</li>
 * </ul>
 * </p>
 *
 * @author AI Code
 * @version 1.0
 * @see com.example.aicode.aop.AuthInterceptor
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthCheck {

    /**
     * 访问目标方法所必须具备的角色标识。
     *
     * <p>角色值应与 {@link com.example.aicode.model.enums.UserRoleEnum} 中定义的角色值保持一致。
     * 默认为空字符串，表示不限制角色（即任意已登录用户均可访问）；
     * 当指定为 {@code "admin"} 时，表示仅允许管理员角色访问。</p>
     *
     * @return 角色标识字符串，默认为空字符串
     */
    String mustRole() default "";
}
