package com.example.aicode.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户添加请求 DTO。
 * <p>
 * 用于管理员创建用户时接收前端提交的参数，包含账号、昵称、头像、简介及角色等信息。
 * 注意：该 DTO 不包含密码字段，密码通常由服务端默认生成或单独设置。
 *
 * @author <a>程序员yaj</a>
 */
@Data
public class UserAddRequest implements Serializable {

    /**
     * 用户昵称
     */
    private String userName;

    /**
     * 登录账号（唯一）
     */
    private String userAccount;

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
     * 序列化版本号，用于保证序列化与反序列化的版本一致性
     */
    private static final long serialVersionUID = 1L;
}

