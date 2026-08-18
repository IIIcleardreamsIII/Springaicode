package com.example.aicode.service;

import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 服务工厂：创建并装配 LangChain4j 的 AI Service 代理对象。
 * <p>
 * 核心职责：把 {@link ChatModel}（同步模型）与 {@link StreamingChatModel}（流式模型）
 * 注入到 AI Service 代理中，并配置"对话记忆"能力。
 * <p>
 * 【对话记忆装配】通过 {@code chatMemoryProvider} 实现：
 * LangChain4j 每次调用 AI 方法前，会用方法参数上的 {@code @MemoryId} 值作为 key
 * 调用本 provider，取出对应会话的历史消息；调用结束后自动把本轮对话追加回记忆。
 * 这里用 {@link MessageWindowChatMemory} 实现"滑动窗口"记忆（只保留最近 N 条），
 * 底层通过 {@link RedisChatMemoryStore} 持久化到 Redis，从而实现：
 * <ul>
 *   <li>多会话隔离：不同 memoryId（如不同 appId）的记忆互不干扰；</li>
 *   <li>持久化：服务重启后记忆依然存在。</li>
 * </ul>
 *
 * @author yupi
 */
@Configuration
public class AiCodeGeneratorServiceFactory {

    /** 同步聊天模型（LangChain4j 注入） */
    @Resource
    private ChatModel chatModel;

    /** 流式聊天模型（LangChain4j 注入，用于 SSE 打字机效果） */
    @Resource
    private StreamingChatModel streamingChatModel;

    /** Redis 记忆存储（由 RedisChatMemoryStoreConfig 提供，负责把对话消息持久化到 Redis） */
    @Resource
    private RedisChatMemoryStore redisChatMemoryStore;

    /** 单个会话滑动窗口最多保留的消息条数（约等于对话轮数的 2 倍），超出后自动驱逐最旧消息 */
    private static final int MAX_MESSAGES = 20;

    /**
     * 构建 AI Service 代理对象。
     *
     * @return 由 LangChain4j 动态生成的 {@link AiCodeGeneratorService} 实现
     */
    @Bean
    public AiCodeGeneratorService aiCodeGeneratorService() {
        return AiServices.builder(AiCodeGeneratorService.class)
                .chatModel(chatModel)
                .streamingChatModel(streamingChatModel)
                // 关键：配置"记忆提供者"。
                // memoryId 即 AI Service 方法参数上 @MemoryId 标注的值（如 appId），
                // 据此为每个会话创建独立的滑动窗口记忆，底层落到 Redis。
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .id(memoryId)                           // 记忆唯一标识（= @MemoryId 的值）
                        .chatMemoryStore(redisChatMemoryStore)  // 持久化到 Redis
                        .maxMessages(MAX_MESSAGES)              // 滑动窗口大小
                        .build())
                .build();
    }
}
