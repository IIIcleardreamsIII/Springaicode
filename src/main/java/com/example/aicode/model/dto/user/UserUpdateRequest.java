package com.example.aicode.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户更新请求 DTO。
 * <p>
 * 用于接收用户信息更新时提交的参数，包含待更新记录的主键 id 及可修改的字段
 * （昵称、头像、简介、角色）。注意：该 DTO 不包含账号与密码字段，
 * 账号一般不可修改，密码需通过专门接口修改。
 *
 * @author <a>程序员yaj</a>
 */
@Data
public class UserUpdateRequest implements Serializable {

    /**
     * 待更新用户的主键 id
     */
    private Long id;

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
     * 序列化版本号，用于保证序列化与反序列化的版本一致性
     */
    private static final long serialVersionUID = 1L;
}

