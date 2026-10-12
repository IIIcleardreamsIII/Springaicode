package com.example.aicode.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import com.mybatisflex.core.keygen.KeyGenerators;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 应用实体类。
 * <p>
 * 对应数据库中的 app 表，用于存储用户通过 AI 生成的应用信息，包括应用名称、
 * 封面、初始化提示词、代码生成类型、部署信息、优先级及所属用户等字段。
 * 采用 MyBatis-Flex 注解完成 ORM 映射，主键通过雪花算法生成，
 * 并使用逻辑删除字段（isDelete）实现数据软删除。
 * <p>
 * 该类实现了 {@link Serializable} 以支持序列化（例如存入缓存）。
 *
 * @author 程序员yaj
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("app")
public class App implements Serializable {

    /**
     * 序列化版本号，用于保证序列化与反序列化的版本一致性
     */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键 id，基于雪花算法生成，保证全局唯一
     */
    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    /**
     * 应用名称
     */
    @Column("appName")
    private String appName;

    /**
     * 应用封面 URL
     */
    @Column("cover")
    private String cover;

    /**
     * 应用初始化的 prompt（创建应用时的用户提示词）
     */
    @Column("initPrompt")
    private String initPrompt;

    /**
     * 代码生成类型：html / multi_file（对应 {@link com.example.aicode.ai.model.enums.CodeGenTypeEnum} 的 value）
     */
    @Column("codeGenType")
    private String codeGenType;

    /**
     * 部署标识（应用部署后的唯一标识，可用于访问部署后的站点）
     */
    @Column("deployKey")
    private String deployKey;

    /**
     * 部署时间
     */
    @Column("deployedTime")
    private LocalDateTime deployedTime;

    /**
     * 下载次数（用户下载源码进行二次开发的累计次数）
     */
    @Column("downloadCount")
    private Integer downloadCount;

    /**
     * 优先级：99 表示精选应用（对应 {@link com.example.aicode.constant.AppConstant#GOOD_APP_PRIORITY}）
     */
    @Column("priority")
    private Integer priority;

    /**
     * 创建用户 id（关联 user 表主键）
     */
    @Column("userId")
    private Long userId;

    /**
     * 编辑时间
     */
    @Column("editTime")
    private LocalDateTime editTime;

    /**
     * 创建时间
     */
    @Column("createTime")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @Column("updateTime")
    private LocalDateTime updateTime;

    /**
     * 逻辑删除标识：0-未删除，1-已删除（由 MyBatis-Flex 自动处理）
     */
    @Column(value = "isDelete", isLogicDelete = true)
    private Integer isDelete;

}
