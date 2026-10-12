package com.example.aicode.model.dto.app;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * AI 智能选择代码生成类型请求体（DTO）
 * <p>
 * 封装"AI 智能选择方案"接口（{@code POST /app/suggest/type}）接收的请求参数：
 * 用户对应用/页面的自然语言描述（prompt），后端交给 AI 路由判断应使用
 * html / multi_file / project 中的哪一种代码生成方案。
 *
 * @author 程序员yaj
 */
@Data
public class AppSuggestTypeRequest implements Serializable {

    /**
     * 用户对应用或页面的自然语言描述（必填）
     */
    private String prompt;

    @Serial
    private static final long serialVersionUID = 1L;
}
