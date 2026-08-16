package com.example.aicode.parser;

import com.example.aicode.ai.model.HtmlCodeResult;
import com.example.aicode.ai.model.MultiFileCodeResult;

// ============================================================
// 泛型接口科普（请先读这里）
// ------------------------------------------------------------
// public interface CodeParser<T> 中的 <T> 是 "泛型（Generic）"，
// T 是一个"类型占位符"。实现类在 implements 时把 T 替换成具体类型，
// 例如 CodeParser<HtmlCodeResult>，这样 parseCode() 的返回值就固定为 HtmlCodeResult，
// 编译期就能保证类型安全，不需要强制转型，也不会出现 ClassCastException。
// ============================================================

/**
 * 代码解析器策略接口（策略模式中的 "策略" 抽象）
 * <p>
 * 定义统一的解析入口，各种具体解析器（HTML 解析器、多文件解析器）实现本接口，
 * 对外暴露统一的 parseCode(String) 方法，调用方无需关心内部是用正则还是别的方式提取。
 *
 * @param <T> 解析后返回的结果类型（如 HtmlCodeResult / MultiFileCodeResult）
 * @author 程序员yaj
 */
public interface CodeParser<T> {

    /**
     * 解析代码内容，将 AI 返回的文本转为结构化结果对象。
     *
     * @param codeContent AI 返回的原始代码文本（通常包含 Markdown 代码块标记）
     * @return 解析后的结果对象（泛型 T）
     */
    T parseCode(String codeContent);
}

