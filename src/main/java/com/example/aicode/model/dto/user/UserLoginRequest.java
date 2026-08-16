package com.example.aicode.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户登录请求 DTO。
 * <p>
 * 用于接收用户登录时提交的账号与密码，服务端校验通过后颁发登录凭证。
 *
 * @author <a>程序员yaj</a>
 */
@Data
public class UserLoginRequest implements Serializable {

    /**
     * 序列化版本号，用于保证序列化与反序列化的版本一致性
     */
    private static final long serialVersionUID = 3191241716373120793L;

    /**
     * 登录账号
     */
    private String userAccount;

    /**
     * 登录密码（明文传输，由服务端加密比对）
     */
    private String userPassword;
}

