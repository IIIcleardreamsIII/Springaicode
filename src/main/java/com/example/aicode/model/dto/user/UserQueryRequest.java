package com.example.aicode.model.dto.user;

import com.example.aicode.model.dto.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 用户查询请求 DTO。
 * <p>
 * 继承自 {@link PageRequest}，在分页参数基础上增加用户相关的查询条件
 * （id、昵称、账号、简介、角色），支持多条件组合的分页查询。
 *
 * @author <a>程序员yaj</a>
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class UserQueryRequest extends PageRequest implements Serializable {

    /**
     * 用户 id（精确查询）
     */
    private Long id;

    /**
     * 用户昵称（模糊查询）
     */
    private String userName;

    /**
     * 登录账号（模糊查询）
     */
    private String userAccount;

    /**
     * 用户简介（模糊查询）
     */
    private String userProfile;

    /**
     * 用户角色：user（普通用户）/ admin（管理员）/ ban（封禁）
     */
    private String userRole;

    /**
     * 序列化版本号，用于保证序列化与反序列化的版本一致性
     */
    private static final long serialVersionUID = 1L;
}
