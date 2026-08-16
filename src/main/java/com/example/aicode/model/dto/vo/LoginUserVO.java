package com.example.aicode.model.dto.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 登录用户视图对象（VO）。
 * <p>
 * 用于在用户登录成功后返回给前端的用户信息，脱敏后不包含密码等敏感字段，
 * 通常与登录凭证（如 Token / Session）一起下发，供前端展示与鉴权使用。
 *
 * @author <a>程序员yaj</a>
 */
@Data
public class LoginUserVO implements Serializable {

    /**
     * 用户 id
     */
    private Long id;

    /**
     * 登录账号
     */
    private String userAccount;

    /**
     * 用户昵称
     */
    private String userName;

    /**
     * 用户头像 URL
     */
    private String userAvatar;

    /**
     * 用户简介
     */
    private String userProfile;

    /**
     * 用户角色：user（普通用户）/ admin（管理员）
     */
    private String userRole;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 序列化版本号，用于保证序列化与反序列化的版本一致性
     */
    private static final long serialVersionUID = 1L;
}

