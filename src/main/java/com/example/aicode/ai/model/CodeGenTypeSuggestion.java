package com.example.aicode.ai.model;

import lombok.Data;

/**
 * AI 智能选择代码生成方案的结构化输出结果
 * <p>
 * LangChain4j 会要求 AI 以 JSON 返回 {"type": "html"} 形式的对象，
 * 再反序列化为本类。type 的取值与 {@link com.example.aicode.ai.model.enums.CodeGenTypeEnum#getEnumByValue(String)}
 * 约定一致：html / multi_file / project。
 *
 * @author yupi
 */
@Data
public class CodeGenTypeSuggestion {

    /**
     * 建议的代码生成类型标识：html / multi_file / project
     */
    private String type;
}
