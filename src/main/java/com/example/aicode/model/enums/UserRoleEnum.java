package com.example.aicode.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

/**
 * 用户角色枚举。
 * <p>
 * 用于定义系统中用户的角色类型，统一管理角色文本描述与数据库存储值之间的映射关系，
 * 避免在业务代码中出现硬编码的角色字符串，提升可维护性与类型安全性。
 *
 * @author <a>程序员yaj</a>
 */
@Getter
public enum UserRoleEnum {

    /** 普通用户 */
    USER("用户", "user"),
    /** 管理员 */
    ADMIN("管理员", "admin");

    /** 角色的中文文本描述 */
    private final String text;

    /** 角色在数据库中存储的值 */
    private final String value;

    /**
     * 枚举构造方法。
     *
     * @param text  角色的中文文本描述
     * @param value 角色在数据库中存储的值
     */
    UserRoleEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据数据库存储的 value 值获取对应的枚举实例。
     * <p>
     * 当传入的值为空或在所有枚举常量中均未匹配时，返回 {@code null}。
     *
     * @param value 数据库中存储的角色值
     * @return 匹配到的枚举实例；若未匹配则返回 {@code null}
     */
    public static UserRoleEnum getEnumByValue(String value) {
        // 入参为空直接返回 null，避免空指针异常
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        // 遍历所有枚举常量，逐一比对 value
        for (UserRoleEnum anEnum : UserRoleEnum.values()) {
            if (anEnum.value.equals(value)) {
                return anEnum;
            }
        }
        // 未找到匹配项
        return null;
    }
}

