package com.example.aicode.ai.model;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

// ============================================================
// 本类说明（重要，请先读这里）
// ------------------------------------------------------------
// 与 HtmlCodeResult 同理，本类也是 LangChain4j "结构化输出" 的结果承载类。
// 区别在于本类对应 "多文件模式"：AI 不只会返回一段 HTML，
// 而是把 HTML / CSS / JS 拆成三个独立字段分别返回。
//
// 后端拿到本对象后，会把这三个字段分别写入 index.html / style.css / script.js
// 三个独立文件（参见 saver 模块）。
// ============================================================

/**
 * 多文件代码生成结果模型（HTML + CSS + JS）
 * <p>
 * 用于接收 AI 生成的多文件前端代码，包含 HTML、CSS、JavaScript 三个独立文件的内容及描述。
 *
 * @author yupi
 */
@Description("生成多个代码文件的结果") // LangChain4j 注解：描述整个类的用途，会拼接进发给 AI 的系统提示词
@Data // Lombok 注解：自动生成 getter/setter 等
public class MultiFileCodeResult {

    /**
     * 生成的 HTML 结构代码内容（页面骨架）
     */
    @Description("HTML代码") // 告诉 AI：此字段填 HTML
    private String htmlCode;

    /**
     * 生成的 CSS 样式代码内容（页面外观）
     */
    @Description("CSS代码") // 告诉 AI：此字段填 CSS
    private String cssCode;

    /**
     * 生成的 JavaScript 逻辑代码内容（页面交互）
     */
    @Description("JS代码") // 告诉 AI：此字段填 JavaScript
    private String jsCode;

    /**
     * AI 对生成代码整体的描述说明
     */
    @Description("生成代码的描述") // 告诉 AI：此字段填整体描述
    private String description;
}


