package com.example.aicode.aop;

import com.example.aicode.annotation.AuthCheck;
import com.example.aicode.common.BusinessException;
import com.example.aicode.common.ErrorCode;
import com.example.aicode.model.entity.User;
import com.example.aicode.model.enums.UserRoleEnum;
import com.example.aicode.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 权限校验拦截切面。
 *
 * <p>该切面基于 Spring AOP 实现，通过环绕通知（{@link Around}）拦截所有标注了
 * {@link AuthCheck} 注解的 Controller 方法，在目标方法执行前后进行统一的权限校验。</p>
 *
 * <p>校验流程：
 * <ol>
 *   <li>从注解中读取要求的角色标识（{@code mustRole}）；</li>
 *   <li>从当前 HTTP 请求上下文中获取登录用户信息；</li>
 *   <li>若注解未指定角色，则直接放行（仅需登录即可）；</li>
 *   <li>若指定了角色，则比对当前用户角色，不满足时抛出
 *       {@link BusinessException}（无权限异常）。</li>
 * </ol>
 * </p>
 *
 * @author AI Code
 * @version 1.0
 * @see AuthCheck
 */
// ============================================================
// Spring AOP 关键术语（请先读这里）
// ------------------------------------------------------------
// @Aspect        ：声明"这是一个切面类"（把横切逻辑如权限校验集中到一处）
// @Component     ：让 Spring 扫描并注册为 Bean（切面本身也要是 Bean 才生效）
// @Around(...)   ：环绕通知，"包住"目标方法执行前后都能插逻辑；
//                 表达式 @annotation(authCheck) 表示"拦截所有带 @AuthCheck 注解的方法"
// ProceedingJoinPoint：AOP 提供给你的"目标方法句柄"，只有调用它的 proceed()
//                 才会真正执行被拦截的原方法（不调用就相当于拦截掉了）
// RequestContextHolder：在非 Controller 层也能拿到当前请求的 HttpServletRequest
// ============================================================

@Aspect  // 声明切面
@Component // 注册为 Spring Bean
public class AuthInterceptor {

    /** 注入用户服务，用于读取当前登录用户及其角色 */
    @Resource
    private UserService userService;

    /**
     * 环绕拦截方法：对所有 @AuthCheck 标注的方法做统一权限校验。
     * <p>
     * 在目标方法执行前先校验角色；通过才 proceed() 放行，不通过则抛无权限异常。
     *
     * @param joinPoint AOP 切入点（目标方法句柄），proceed() 才能继续执行原方法
     * @param authCheck 目标方法上的 @AuthCheck 注解实例（含 mustRole 值）
     * @return 目标方法的返回值
     * @throws Throwable 无权限或目标方法抛出的异常
     */
    @Around("@annotation(authCheck)") // 切点：拦截"带 @AuthCheck 注解"的方法；authCheck 参数与之同名绑定
    public Object doInterceptor(ProceedingJoinPoint joinPoint, AuthCheck authCheck) throws Throwable {
        // 读取注解上声明的"要求角色"（如 "admin"），为空表示只要登录即可
        String mustRole = authCheck.mustRole();
        // 在非 Controller 处获取当前请求：Spring 把每个请求的 Request 绑定到线程上下文
        RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();
        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
        // 调用户服务取出当前登录用户（未登录会抛 NOT_LOGIN_ERROR）
        User loginUser = userService.getLoginUser(request);
        // 将注解中的角色字符串转换为角色枚举
        UserRoleEnum mustRoleEnum = UserRoleEnum.getEnumByValue(mustRole);
        // 不需要权限，放行
        if (mustRoleEnum == null) {
            return joinPoint.proceed();
        }
        // 以下为：必须有该权限才通过
        // 获取当前用户具有的权限
        UserRoleEnum userRoleEnum = UserRoleEnum.getEnumByValue(loginUser.getUserRole());
        // 没有权限，拒绝
        if (userRoleEnum == null) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 要求必须有管理员权限，但用户没有管理员权限，拒绝
        if (UserRoleEnum.ADMIN.equals(mustRoleEnum) && !UserRoleEnum.ADMIN.equals(userRoleEnum)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 通过权限校验，放行
        return joinPoint.proceed();
    }
}

