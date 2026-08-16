package com.example.aicode.core;

import com.example.aicode.ai.model.HtmlCodeResult;
import com.example.aicode.ai.model.MultiFileCodeResult;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 代码解析器（旧版，纯静态方法风格）
 * <p>
 * 与 parser 包下的"策略 + 执行器"设计不同，本类是一组静态方法，
 * 同样用正则从 AI 返回的 Markdown 文本中抠出代码块，由 facade 流式路径调用。
 * <p>
 * 两套解析实现（本类 + parser 包）逻辑等价，根因是项目演进中新增了更规范的设计，
 * 旧的静态方法仍被 facade 的同步/流式分支使用。理解时以 parser 包的面向对象版本为主即可。
 *
 * @author yupi
 */
public class CodeParser {

    /** 匹配 Markdown 中 ```html 代码块的正则（CASE_INSENSITIVE 忽略大小写） */
    private static final Pattern HTML_CODE_PATTERN = Pattern.compile("```html\\s*\\n([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    /** 匹配 Markdown 中 ```css 代码块的正则 */
    private static final Pattern CSS_CODE_PATTERN = Pattern.compile("```css\\s*\\n([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    /** 匹配 Markdown 中 ```js / ```javascript 代码块的正则；(?:...) 为非捕获组 */
    private static final Pattern JS_CODE_PATTERN = Pattern.compile("```(?:js|javascript)\\s*\\n([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    /**
     * 解析 HTML 单文件代码。
     * 若匹配到 ```html 块则取块内内容，否则把整段文本当 HTML（兼容 AI 不套代码块的情况）。
     *
     * @param codeContent AI 生成的原始代码内容
     * @return 解析后的 {@link HtmlCodeResult} 对象
     */
    public static HtmlCodeResult parseHtmlCode(String codeContent) {
        HtmlCodeResult result = new HtmlCodeResult();
        String htmlCode = extractHtmlCode(codeContent);
        if (htmlCode != null && !htmlCode.trim().isEmpty()) {
            result.setHtmlCode(htmlCode.trim());
        } else {
            // trim() 去除首尾空白，避免文件首尾多余换行
            result.setHtmlCode(codeContent.trim());
        }
        return result;
    }

    /**
     * 解析多文件代码（HTML + CSS + JS），分别匹配三个代码块并写入结果对象。
     *
     * @param codeContent AI 生成的原始代码内容
     * @return 解析后的 {@link MultiFileCodeResult} 对象
     */
    public static MultiFileCodeResult parseMultiFileCode(String codeContent) {
        MultiFileCodeResult result = new MultiFileCodeResult();
        String htmlCode = extractCodeByPattern(codeContent, HTML_CODE_PATTERN);
        String cssCode = extractCodeByPattern(codeContent, CSS_CODE_PATTERN);
        String jsCode = extractCodeByPattern(codeContent, JS_CODE_PATTERN);
        if (htmlCode != null && !htmlCode.trim().isEmpty()) {
            result.setHtmlCode(htmlCode.trim());
        }
        if (cssCode != null && !cssCode.trim().isEmpty()) {
            result.setCssCode(cssCode.trim());
        }
        if (jsCode != null && !jsCode.trim().isEmpty()) {
            result.setJsCode(jsCode.trim());
        }
        return result;
    }

    /**
     * 从内容中提取 HTML 代码块（第一个捕获组）。
     *
     * @param content 原始 Markdown 内容
     * @return 提取的 HTML 代码，未找到则返回 null
     */
    private static String extractHtmlCode(String content) {
        Matcher matcher = HTML_CODE_PATTERN.matcher(content); // 把正则绑定到输入串
        if (matcher.find()) { // 查找首个匹配
            return matcher.group(1); // 取第 1 个捕获组（``` 之间的内容）
        }
        return null;
    }

    /**
     * 通用提取：按指定正则模式取第一个捕获组内容。
     *
     * @param content 原始 Markdown 内容
     * @param pattern 已编译的正则
     * @return 提取的代码内容，未找到则返回 null
     */
    private static String extractCodeByPattern(String content, Pattern pattern) {
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}

