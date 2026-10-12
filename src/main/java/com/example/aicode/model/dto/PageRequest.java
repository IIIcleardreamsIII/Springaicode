package com.example.aicode.model.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 分页请求基类。
 * <p>
 * 封装通用的分页查询参数（页码、每页大小、排序字段与排序顺序），
 * 可被各类查询请求 DTO 继承复用，统一分页参数的传递与校验规范。
 *
 * @author <a>程序员yaj</a>
 */
@Data
public class PageRequest implements Serializable {

    /**
     * 序列化版本号，用于保证序列化与反序列化的版本一致性
     */
    private static final long serialVersionUID = 1L;

    /**
     * 当前页号（从 1 开始，默认为 1）
     */
    private long pageNum = 1;

    /**
     * 每页大小（默认为 10）
     */
    private long pageSize = 10;

    /**
     * 排序字段（对应实体属性名）
     */
    private String sortField;

    /**
     * 排序顺序：ascend（升序）/ descend（降序）
     */
    private String sortOrder;

}
