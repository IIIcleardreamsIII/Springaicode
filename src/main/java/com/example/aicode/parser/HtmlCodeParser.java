package com.example.aicode.parser;

import com.example.aicode.ai.model.HtmlCodeResult;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ============================================================
// 正则表达式（Regex）科普（请先读这里）
// ------------------------------------------------------------
// 正则用于在字符串中"按模式"查找/提取文本。本项目用正则从 AI 返回的
// Markdown 文本里抠出 ```html ... ``` 这样的代码块。
//
// 本类模式串含义拆解：
//   "```html"     → 匹配字面量 ```html（代码块开始标记）
//   "\\s*"        → 零个或多个空白字符（含换行），容忍标记后的空格/换行
//   "\\n"         → 一个换行符
//   "([\\s\\S]*?)"→ 第 1 个捕获组 ()：匹配"任意字符（含换行）"，*? 表示"非贪婪"
//                   即尽可能少地匹配，遇到第一个 ``` 就停止，避免跨代码块
//   "```"         → 匹配代码块结束标记
//   Pattern.CASE_INSENSITIVE → 忽略大小写（HTML 也可写作 html）
// ============================================================

/**
 * HTML 单文件代码解析器（实现 {@link CodeParser} 接口，泛型 T = HtmlCodeResult）
 * <p>
 * 负责从 AI 返回的 Markdown 文本中提取 ```html 代码块，封装为 {@link HtmlCodeResult}。
 *
 * @author 程序员yaj
 */
public class HtmlCodeParser implements CodeParser<HtmlCodeResult> {

    /** 编译好的正则模式，提取 ```html 代码块内容。声明为 static final 可复用、提升性能 */
    private static final Pattern HTML_CODE_PATTERN = Pattern.compile("```html\\s*\\n([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    /**
     * 解析 HTML 代码文本。
     *
     * @param codeContent AI 返回的原始文本（可能含 ```html 代码块，也可能直接是 HTML）
     * @return 提取出 htmlCode 的 {@link HtmlCodeResult} 对象
     */
    @Override
    public HtmlCodeResult parseCode(String codeContent) {
        HtmlCodeResult result = new HtmlCodeResult();
        // 尝试按正则提取 HTML 代码块
        String htmlCode = extractHtmlCode(codeContent);
        if (htmlCode != null && !htmlCode.trim().isEmpty()) {
            result.setHtmlCode(htmlCode.trim());
        } else {
            // 兼容处理：AI 偶尔不套代码块，此时把整段内容直接当 HTML
            result.setHtmlCode(codeContent.trim());
        }
        return result;
    }

    /**
     * 使用正则从内容中提取 HTML 代码块内容。
     *
     * @param content 原始 Markdown 文本
     * @return 提取到的代码（含代码块内部内容）；未匹配到返回 null
     */
    private String extractHtmlCode(String content) {
        // Matcher：把模式应用到输入字符串上，用于执行匹配
        Matcher matcher = HTML_CODE_PATTERN.matcher(content);
        if (matcher.find()) {
            // find()：尝试查找下一个匹配；匹配成功后
            // group(1)：取第 1 个捕获组（即 ``` 之间的内容）
            return matcher.group(1);
        }
        return null;
    }
}

