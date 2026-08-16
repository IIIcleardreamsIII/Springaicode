package com.example.aicode.model.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户注册请求 DTO。
 * <p>
 * 用于接收用户注册时提交的账号、密码及确认密码，服务端校验账号唯一性、
 * 密码长度以及两次密码一致性后完成注册。使用 Swagger {@link Schema} 注解
 * 生成接口文档。
 *
 * @author <a>程序员yaj</a>
 */
@Data
@Schema(description = "用户注册请求")
public class UserRegisterRequest implements Serializable {

    /**
     * 序列化版本号，用于保证序列化与反序列化的版本一致性
     */
    private static final long serialVersionUID = 3191241716373120793L;

    /**
     * 用户账号（必填，需保证唯一）
     */
    @Schema(description = "用户账号", requiredMode = Schema.RequiredMode.REQUIRED, example = "yaj")
    private String userAccount;

    /**
     * 用户密码（必填，至少 8 位）
     */
    @Schema(description = "用户密码（至少8位）", requiredMode = Schema.RequiredMode.REQUIRED, example = "12345678")
    private String userPassword;

    /**
     * 确认密码（必填，需与密码一致）
     */
    @Schema(description = "确认密码（需与密码一致）", requiredMode = Schema.RequiredMode.REQUIRED, example = "12345678")
    private String checkPassword;
}

