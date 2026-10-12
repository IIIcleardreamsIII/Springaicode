package com.example.aicode.model.dto.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 对话历史封装视图对象（VO：View Object）。
 *
 * <p>该对象用于向前端返回"对话历史消息"视图，由实体 {@code ChatHistory} 经过字段裁剪后得到。
 * 与直接返回实体相比，隐藏了内部实现字段（如逻辑删除标识），仅暴露前端展示所需的字段。</p>
 *
 * @author 程序员yaj
 */
@Data
public class ChatHistoryVO implements Serializable {

    /**
     * 消息 id（同时可作为"向前加载历史记录"的游标 cursorId）
     */
    private Long id;

    /**
     * 消息内容
     */
    private String message;

    /**
     * 消息类型：user / ai
     */
    private String messageType;

    /**
     * 所属应用 id
     */
    private Long appId;

    /**
     * 创建用户 id
     */
    private Long userId;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    @Serial
    private static final long serialVersionUID = 1L;
}
