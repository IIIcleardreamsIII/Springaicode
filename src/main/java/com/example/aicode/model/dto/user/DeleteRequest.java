package com.example.aicode.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 删除请求 DTO。
 * <p>
 * 用于接收前端传来的删除操作参数，通常只包含待删除记录的主键 id。
 *
 * @author <a>程序员yaj</a>
 */
@Data
public class DeleteRequest implements Serializable {

    /**
     * 序列化版本号，用于保证序列化与反序列化的版本一致性
     */
    private static final long serialVersionUID = 1L;

    /**
     * 待删除记录的主键 id
     */
    private Long id;

}
