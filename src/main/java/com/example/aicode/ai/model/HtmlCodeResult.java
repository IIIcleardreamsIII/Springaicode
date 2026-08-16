package com.example.aicode.ai.model;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

// ============================================================
// 本类说明（重要，请先读这里）
// ------------------------------------------------------------
// 这是一个 "AI 结构化输出" 的结果承载类（DTO / POJO）。
//
// 在传统开发中，我们调用 AI 接口拿回来的通常是一段纯文本字符串；
// 而本项目使用了 LangChain4j 的 "结构化输出（Structured Output）" 能力：
//   我们可以告诉 AI "请按我给定的 Java 类的字段结构来返回 JSON"，
//   框架会自动把 AI 返回的 JSON 反序列化成这个 Java 对象。
//
// 因此本类的每一个字段，都会直接对应 AI 返回 JSON 中的一个 key。
// 字段上的 @Description 注解会被 LangChain4j 读取，作为 prompt（提示词）
// 的一部分发给 AI，告诉 AI 这个字段应该填什么内容。
// ============================================================

/**
 * HTML 单文件代码生成结果模型
 * <p>
 * 当调用 AI 生成"纯语义 HTML"模式时，AI 会把生成结果以 JSON 形式返回，
 * 框架将其自动映射为本对象。包含两个字段：HTML 代码本身、以及对代码的文字描述。
 *
 * @author yupi
 */
@Description("生成 HTML 代码文件的结果") // LangChain4j 注解：描述整个类的用途，会拼接进发给 AI 的系统提示词
@Data // Lombok 注解：编译期自动生成 getter/setter/toString/equals/hashCode，无需手写
public class HtmlCodeResult {

    /**
     * 生成的 HTML 代码内容（纯文本字符串，可能包含完整的 HTML 文档）
     */
    @Description("HTML代码") // 告诉 AI：这个字段请填入 HTML 代码
    private String htmlCode;

    /**
     * AI 对本次生成代码的一句文字描述（例如"这是一个企业官网首页"）
     */
    @Description("生成代码的描述") // 告诉 AI：这个字段请填入对代码的简短描述
    private String description;
}


