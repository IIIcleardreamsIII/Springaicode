package com.example.aicode.model.dto.app;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 应用编辑请求体（DTO：Data Transfer Object）。
 *
 * <p>该对象用于封装"用户编辑自己的应用"接口（{@code POST /app/edit}）接收的请求参数，
 * 由 Spring 将 HTTP 请求体（JSON）反序列化得到。
 * 目前仅支持修改应用名称，后续可扩展更多可编辑字段。</p>
 *
 * <p>与 {@code AppUpdateRequest}（管理员更新）区分：
 * 本类面向普通用户，仅允许修改名称，且只允许操作本人创建的应用。</p>
 *
 * @author 程序员yaj
 */
@Data
public class AppEditRequest implements Serializable {

    /**
     * 应用 id（用于定位要编辑的应用）
     */
    private Long id;

    /**
     * 应用名称
     */
    private String appName;

    @Serial
    private static final long serialVersionUID = 1L;
}
