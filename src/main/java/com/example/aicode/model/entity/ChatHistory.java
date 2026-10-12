package com.example.aicode.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.keygen.KeyGenerators;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 对话历史实体类。
 * <p>
 * 对应数据库中的 chat_history 表，用于持久化某个应用（appId）下的对话消息，
 * 记录消息内容、消息类型（user/ai）、所属应用与创建用户，以及时间信息。
 * 采用 MyBatis-Flex 注解完成 ORM 映射，主键通过雪花算法生成，
 * 并使用逻辑删除字段（isDelete）实现数据软删除。
 * <p>
 * 设计要点：每个应用（appId）的对话历史相互独立，通过 appId 字段完成应用级数据隔离；
 * 删除应用时，业务层会联动逻辑删除该应用下的全部对话历史，避免数据冗余。
 *
 * @author 程序员yaj
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("chat_history")
public class ChatHistory implements Serializable {

    /**
     * 序列化版本号，用于保证序列化与反序列化的版本一致性
     */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键 id，基于雪花算法生成，保证全局唯一。
     * <p>该字段单调递增，可同时作为"向前加载历史记录"的游标（cursorId）。</p>
     */
    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    /**
     * 消息内容
     */
    @Column("message")
    private String message;

    /**
     * 消息类型：user / ai（对应 {@link com.example.aicode.model.enums.MessageTypeEnum} 的 value）
     */
    @Column("messageType")
    private String messageType;

    /**
     * 所属应用 id（关联 app 表主键，用于应用级数据隔离）
     */
    @Column("appId")
    private Long appId;

    /**
     * 创建用户 id（关联 user 表主键）
     */
    @Column("userId")
    private Long userId;

    /**
     * 创建时间（插入时由数据库/框架填充）
     */
    @Column("createTime")
    private LocalDateTime createTime;

    /**
     * 更新时间（每次更新自动刷新）
     */
    @Column("updateTime")
    private LocalDateTime updateTime;

    /**
     * 逻辑删除标识：0-未删除，1-已删除（由 MyBatis-Flex 自动处理）
     */
    @Column(value = "isDelete", isLogicDelete = true)
    private Integer isDelete;

}
