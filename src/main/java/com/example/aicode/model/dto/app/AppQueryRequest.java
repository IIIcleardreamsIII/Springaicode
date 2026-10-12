package com.example.aicode.model.dto.app;

import com.example.aicode.model.dto.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;

/**
 * 应用查询请求体（DTO：Data Transfer Object）。
 *
 * <p>该对象继承 {@link PageRequest}（携带分页参数 pageNum / pageSize / 排序字段），
 * 并扩展了应用相关的查询条件字段。用于"分页查询应用"类接口，
 * 由 Spring 将 HTTP 请求体（JSON）反序列化得到。</p>
 *
 * <p>支持根据除时间外的任何字段查询；空字段（null）的查询条件会被自动忽略，
 * 因此调用方只需传递需要的筛选条件即可。</p>
 *
 * @author 程序员yaj
 */
@EqualsAndHashCode(callSuper = true) // 让 equals/hashCode 包含父类（分页）字段，保证 Lombok 生成的比较方法完整
@Data
public class AppQueryRequest extends PageRequest implements Serializable {

    /**
     * 应用 id（精确匹配）
     */
    private Long id;

    /**
     * 应用名称（模糊匹配）
     */
    private String appName;

    /**
     * 应用封面（模糊匹配）
     */
    private String cover;

    /**
     * 应用初始化的 prompt（模糊匹配）
     */
    private String initPrompt;

    /**
     * 代码生成类型：html / multi_file（精确匹配）
     */
    private String codeGenType;

    /**
     * 部署标识（精确匹配）
     */
    private String deployKey;

    /**
     * 优先级（精确匹配，用于精选列表查询）
     */
    private Integer priority;

    /**
     * 创建用户 id（精确匹配，用于"我的应用"列表查询）
     */
    private Long userId;

    @Serial
    private static final long serialVersionUID = 1L;
}
