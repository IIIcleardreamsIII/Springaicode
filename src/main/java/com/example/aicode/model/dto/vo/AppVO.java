package com.example.aicode.model.dto.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 应用封装视图对象（VO：View Object）。
 *
 * <p>该对象用于向前端返回"应用信息"视图，通常由实体 {@code App} 经过数据脱敏、
 * 字段裁剪或补充展示所需字段后得到。与直接返回实体相比，可以隐藏内部实现
 * （如逻辑删除字段），并额外关联创建者信息（{@link UserVO}）。</p>
 *
 * @author 程序员yaj
 */
@Data
public class AppVO implements Serializable {

    /**
     * 应用 id
     */
    private Long id;

    /**
     * 应用名称
     */
    private String appName;

    /**
     * 应用封面 URL
     */
    private String cover;

    /**
     * 应用初始化的 prompt
     */
    private String initPrompt;

    /**
     * 代码生成类型：html / multi_file
     */
    private String codeGenType;

    /**
     * 部署标识
     */
    private String deployKey;

    /**
     * 部署时间
     */
    private LocalDateTime deployedTime;

    /**
     * 下载次数
     */
    private Integer downloadCount;

    /**
     * 优先级
     */
    private Integer priority;

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

    /**
     * 创建用户信息（脱敏后的创建者视图对象）
     */
    private UserVO user;

    @Serial
    private static final long serialVersionUID = 1L;
}
