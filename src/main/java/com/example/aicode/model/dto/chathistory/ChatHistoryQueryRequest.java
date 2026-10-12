package com.example.aicode.model.dto.chathistory;

import com.example.aicode.model.dto.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;

/**
 * 对话历史查询请求体（DTO：Data Transfer Object）。
 *
 * <p>该对象继承 {@link PageRequest}（携带分页参数 pageNum / pageSize / 排序字段），
 * 并扩展了对话历史相关的查询条件字段，用于"管理员分页查询对话历史"类接口。
 * 空字段（null）的查询条件会被 MyBatis-Flex 自动忽略，调用方只需传递需要的筛选条件。</p>
 *
 * @author 程序员yaj
 */
@EqualsAndHashCode(callSuper = true) // 让 equals/hashCode 包含父类（分页）字段
@Data
public class ChatHistoryQueryRequest extends PageRequest implements Serializable {

    /**
     * 所属应用 id（精确匹配，用于按应用过滤对话历史）
     */
    private Long appId;

    /**
     * 创建用户 id（精确匹配，用于按用户过滤对话历史）
     */
    private Long userId;

    /**
     * 消息类型：user / ai（精确匹配）
     */
    private String messageType;

    @Serial
    private static final long serialVersionUID = 1L;
}
