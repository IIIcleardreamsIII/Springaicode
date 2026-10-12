package com.example.aicode.model.dto.code;

import lombok.Data;

import java.io.Serializable;

/**
 * 代码生成请求 DTO
 *
 * @author AI Code
 */
@Data
public class CodeGenerateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户提示词（用户想要生成什么代码）
     */
    private String userMessage;

    /**
     * 生成类型：html / multi_file / project（对应 CodeGenTypeEnum 的 value）
     */
    private String codeGenType;

    /**
     * 应用名称（可选）。若不传，则取用户提示词的前 20 个字作为名称。
     * 用于把独立生成器产出的代码登记为"我的应用"，使其出现在应用列表里。
     */
    private String appName;

}
