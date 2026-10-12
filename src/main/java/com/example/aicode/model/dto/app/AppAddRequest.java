package com.example.aicode.model.dto.app;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 应用创建请求体（DTO：Data Transfer Object）。
 *
 * <p>该对象用于封装"创建应用"接口（{@code POST /app/add}）接收的请求参数，
 * 由 Spring 将 HTTP 请求体（JSON）反序列化得到。
 * 与实体类 {@code App} 分离，可以避免直接暴露数据库表结构，
 * 并允许接口字段与实体字段灵活对应。</p>
 *
 * <p>创建应用要求填写初始化提示词（initPrompt），并可选择代码生成类型
 * （codeGenType，如 html / multi_file / project）；不传类型时后端默认 HTML。
 * 应用名称由后端根据提示词自动生成。</p>
 *
 * @author 程序员yaj
 */
@Data
public class AppAddRequest implements Serializable {

    /**
     * 应用初始化的 prompt（必填），用于描述用户期望生成的应用内容
     */
    private String initPrompt;

    /**
     * 代码生成类型（可选）：html / multi_file / project，不传默认 html
     */
    private String codeGenType;

    @Serial
    private static final long serialVersionUID = 1L;
}
