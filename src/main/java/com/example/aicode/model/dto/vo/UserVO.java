package com.example.aicode.model.dto.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户视图对象（VO）。
 * <p>
 * 用于向前端展示用户信息时的统一返回结构，对实体进行了脱敏处理，
 * 不包含密码、逻辑删除标识等敏感/内部字段，适用于列表查询与详情展示场景。
 *
 * @author <a>程序员yaj</a>
 */
@Data
public class UserVO implements Serializable {

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
     * 序列化版本号，用于保证序列化与反序列化的版本一致性
     */
    private static final long serialVersionUID = 1L;
}

