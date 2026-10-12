package com.example.aicode.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

/**
 * 对话消息类型枚举。
 * <p>
 * 用于定义对话历史中消息的发送方类型，统一管理类型文本描述与数据库存储值之间的映射关系，
 * 避免在业务代码中出现硬编码的消息类型字符串，提升可维护性与类型安全性。
 * <p>
 * 说明：当 AI 回复失败时，为"确保对话的完整性"，错误信息会以 AI 类型的消息（{@link #AI}）落库，
 * 消息内容即为错误描述，因此无需额外引入 error 类型。
 *
 * @author 程序员yaj
 */
@Getter
public enum MessageTypeEnum {

    /** 用户消息 */
    USER("用户消息", "user"),
    /** AI 消息（含 AI 正常回复，以及回复失败时的错误信息） */
    AI("AI 消息", "ai");

    /** 消息类型的中文文本描述 */
    private final String text;

    /** 消息类型在数据库中存储的值 */
    private final String value;

    /**
     * 枚举构造方法。
     *
     * @param text  消息类型的中文文本描述
     * @param value 消息类型在数据库中存储的值
     */
    MessageTypeEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据数据库存储的 value 值获取对应的枚举实例。
     * <p>
     * 当传入的值为空或在所有枚举常量中均未匹配时，返回 {@code null}。
     *
     * @param value 数据库中存储的消息类型值
     * @return 匹配到的枚举实例；若未匹配则返回 {@code null}
     */
    public static MessageTypeEnum getEnumByValue(String value) {
        // 入参为空直接返回 null，避免空指针异常
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        // 遍历所有枚举常量，逐一比对 value
        for (MessageTypeEnum anEnum : MessageTypeEnum.values()) {
            if (anEnum.value.equals(value)) {
                return anEnum;
            }
        }
        // 未找到匹配项
        return null;
    }
}
