package com.example.aicode.parser;

import com.example.aicode.ai.model.MultiFileCodeResult;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 多文件代码解析器（实现 {@link CodeParser}，泛型 T = MultiFileCodeResult）
 * <p>
 * 同时从 AI 返回的 Markdown 文本中提取 HTML、CSS、JavaScript 三个代码块，
 * 分别写入结果的对应字段。各代码块互相独立，缺失则对应字段留空。
 *
 * @author 程序员yaj
 */
public class MultiFileCodeParser implements CodeParser<MultiFileCodeResult> {

    /** 匹配 ```html 代码块 */
    private static final Pattern HTML_CODE_PATTERN = Pattern.compile("```html\\s*\\n([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    /** 匹配 ```css 代码块 */
    private static final Pattern CSS_CODE_PATTERN = Pattern.compile("```css\\s*\\n([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    /** 匹配 ```js 或 ```javascript 代码块。其中 (?:js|javascript) 的 ?: 表示"非捕获组"
     * （只做分组匹配，不单独提取，节省性能，因为这里不需要单独拿 js 这个标签名） */
    private static final Pattern JS_CODE_PATTERN = Pattern.compile("```(?:js|javascript)\\s*\\n([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    /**
     * 解析多文件代码文本。
     *
     * @param codeContent AI 返回的原始文本（应含 html/css/js 三个代码块）
     * @return 提取出三块代码的 {@link MultiFileCodeResult} 对象
     */
    @Override
    public MultiFileCodeResult parseCode(String codeContent) {
        MultiFileCodeResult result = new MultiFileCodeResult();
        // 分别用各自的正则提取三类代码（互不干扰）
        String htmlCode = extractCodeByPattern(codeContent, HTML_CODE_PATTERN);
        String cssCode = extractCodeByPattern(codeContent, CSS_CODE_PATTERN);
        String jsCode = extractCodeByPattern(codeContent, JS_CODE_PATTERN);
        // 仅当提取结果非空时才写入，避免把 null 写进对象
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
     * 通用方法：根据传入的正则模式从内容中提取第一个捕获组内容。
     * <p>
     * 这样 HTML/CSS/JS 三套提取逻辑复用同一段匹配代码，避免重复。
     *
     * @param content 原始 Markdown 文本
     * @param pattern 预编译好的正则模式
     * @return 匹配到的代码内容；未匹配到返回 null
     */
    private String extractCodeByPattern(String content, Pattern pattern) {
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}

