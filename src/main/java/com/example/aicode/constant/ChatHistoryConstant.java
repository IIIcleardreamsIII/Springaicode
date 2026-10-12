package com.example.aicode.constant;

/**
 * 对话历史相关常量定义接口。
 *
 * <p>该接口集中维护与对话历史（ChatHistory）相关的常量值，供各业务模块统一引用，
 * 避免在代码中硬编码魔法数字。接口中定义的常量默认为 {@code public static final}，
 * 可直接通过接口名访问。</p>
 *
 * @author 程序员yaj
 */
public interface ChatHistoryConstant {

    /**
     * 默认单次加载的消息条数。
     *
     * <p>类似聊天软件的"消息加载机制"：进入应用页面时先加载最新 {@link #DEFAULT_PAGE_SIZE} 条，
     * 向前翻看时再逐批加载更早的历史记录。</p>
     */
    int DEFAULT_PAGE_SIZE = 10;

    /**
     * 单次最多允许加载的消息条数。
     *
     * <p>用于对游标分页查询的 pageSize 做上限保护，防止一次加载过多导致接口超时。</p>
     */
    int MAX_PAGE_SIZE = 20;
}
