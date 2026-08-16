package com.example.aicode.constant;

/**
 * 用户相关常量定义接口。
 *
 * <p>该接口集中维护与用户登录态、角色权限相关的常量值，供各业务模块统一引用，
 * 避免在代码中硬编码魔法字符串。接口中定义的常量默认为 {@code public static final}，
 * 可直接通过接口名访问。</p>
 *
 * <p>包含的常量分类：
 * <ul>
 *   <li>登录态相关：用户在 Session 中存储登录信息的键名；</li>
 *   <li>角色权限相关：系统内置的默认角色与管理员角色标识。</li>
 * </ul>
 * </p>
 *
 * @author AI Code
 * @version 1.0
 */
public interface UserConstant {

    /**
     * 用户登录态在 Session 中存储的键名。
     *
     * <p>用户登录成功后，会将用户信息以该键存入 HttpSession，
     * 后续请求通过该键从 Session 中获取登录用户信息。</p>
     */
    String USER_LOGIN_STATE = "user_login";

    // region 权限

    /**
     * 默认用户角色标识。
     *
     * <p>新用户注册时默认分配的角色，对应普通用户权限，
     * 与 {@link com.example.aicode.model.enums.UserRoleEnum#USER} 的值一致。</p>
     */
    String DEFAULT_ROLE = "user";

    /**
     * 管理员角色标识。
     *
     * <p>拥有系统最高权限的角色，可访问管理后台及执行管理类操作，
     * 与 {@link com.example.aicode.model.enums.UserRoleEnum#ADMIN} 的值一致。</p>
     */
    String ADMIN_ROLE = "admin";

    // endregion
}

