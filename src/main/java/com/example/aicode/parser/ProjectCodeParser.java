package com.example.aicode.parser;

import com.example.aicode.ai.model.ProjectCodeResult;
import com.example.aicode.ai.model.ProjectCodeResult.ProjectFile;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 前端工程化项目代码解析器（实现 {@link CodeParser}，泛型 T = ProjectCodeResult）。
 * <p>
 * 负责从 AI 返回的 Markdown 文本中解析出完整的工程文件清单。
 * <p>
 * AI 返回格式约定：每个文件前用一行 {@code // 文件：相对路径} 或 {@code // path: 相对路径}
 * 注释标注该文件路径，紧接着是带语言标记的代码块。例如：
 * <pre>
 * // 文件：src/App.vue
 * ```vue
 * ... 完整代码 ...
 * ```
 * </pre>
 * 解析器遍历所有"路径标记 + 代码块"配对，组装为 {@link ProjectFile} 列表。
 * <p>
 * 语言标记与文件路径的映射规则：优先使用路径标注（本 parser 的核心约定），
 * 语言标记仅作为兜底（如仅出现 ```ts 而无路径标注时按 .ts 生成文件名）。
 *
 * @author yaj
 */
public class ProjectCodeParser implements CodeParser<ProjectCodeResult> {

    /**
     * 路径标注模式：匹配开头的注释行，如 "// 文件：src/App.vue" 或 "# src/main.ts"。
     * 组 1 捕获相对路径。
     */
    private static final Pattern PATH_MARK_PATTERN = Pattern.compile(
            "(?m)^(?://|#|<!--)\\s*(?:文件|路径|path|file)\\s*[:：]\\s*([^\\r\\n<]+?)\\s*(?:-->)?\\s*$");

    /** 代码块模式：匹配 ```lang ... ```，组 1 为语言，组 2 为内容 */
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile(
            "```([a-zA-Z0-9_+-]*)\\s*\\n([\\s\\S]*?)```");

    @Override
    public ProjectCodeResult parseCode(String codeContent) {
        ProjectCodeResult result = new ProjectCodeResult();
        if (codeContent == null || codeContent.trim().isEmpty()) {
            result.setFileList(new ArrayList<>());
            return result;
        }
                                                                                                                                                                            
        List<ProjectFile> files = new ArrayList<>();

        // 1. 提取所有代码块及其起始位置
        List<Block> blocks = extractCodeBlocks(codeContent);

        // 2. 提取所有路径标注及其位置
        List<Mark> marks = extractPathMarks(codeContent);

        // 3. 配对：每个代码块找它前面最近的路径标注
        for (Block block : blocks) {
            String relativePath = findNearestPathBefore(marks, block.start);
            // 有路径标注则用标注路径；否则用语言兜底生成文件名
            if (relativePath == null) {
                relativePath = fallbackPath(block.language);
            }
            if (relativePath == null) {
                continue;
            }
            ProjectFile file = new ProjectFile();
            file.setRelativePath(relativePath.trim());
            file.setContent(block.content);
            files.add(file);
        }

        result.setFileList(files);

        // 兜底：若解析后没有任何文件（例如 AI 未使用"// 文件："标注、也未输出标准代码块），
        // 则把整段文本作为一个完整文件落盘，避免"工程化项目文件清单不能为空"。
        // 这能覆盖修改模式下 AI 直接返回单个完整文件内容（无路径标注）的情况。
        if (files.isEmpty()) {
            ProjectFile fallback = buildWholeContentFallback(codeContent);
            if (fallback != null) {
                files.add(fallback);
            }
        }

        // 项目名兜底：无法从内容推断时留空，由保存器用目录名兜底
        result.setProjectName(extractProjectName(codeContent));
        return result;
    }

    /**
     * 整段内容兜底：当无法解析出任何带路径标注的文件时，尝试把整段内容当作一个完整文件。
     * <p>
     * 优先依据内容特征推断文件路径：
     * <ul>
     *   <li>含 {@code <template>} 且像 Vue 单文件组件 → {@code src/App.vue}</li>
     *   <li>含 {@code export default} / {@code import} 等 JS/TS 语法 → {@code src/main.ts}</li>
     *   <li>含 {@code <!DOCTYPE html>} / {@code <html>} → {@code index.html}</li>
     *   <li>含 {@code :root} / {@code body {} } 等 CSS → {@code src/style.css}</li>
     * </ul>
     * 均不满足时返回 null（交由保存器抛友好异常）。
     *
     * @param content 原始 AI 文本
     * @return 兜底文件，或 null
     */
    private ProjectFile buildWholeContentFallback(String content) {
        if (content == null || content.trim().isEmpty()) {
            return null;
        }
        // 去掉首尾可能被包裹的 ``` 围栏，避免把围栏本身存进文件
        String cleaned = stripFenceIfAny(content);
        String path;
        if (cleaned.contains("<template") || (cleaned.contains("<script") && cleaned.contains("</script>"))) {
            path = "src/App.vue";
        } else if (cleaned.contains("<!DOCTYPE html") || cleaned.trim().toLowerCase().startsWith("<html")) {
            path = "index.html";
        } else if (cleaned.contains(":root") || cleaned.contains("body {") || cleaned.contains("@media")) {
            path = "src/style.css";
        } else if (cleaned.contains("export default") || cleaned.contains("import ") || cleaned.contains("console.log")) {
            path = "src/main.ts";
        } else {
            // 仍无法确定，返回一个默认入口文件，保证不破坏保存流程
            path = "src/App.vue";
        }
        ProjectFile file = new ProjectFile();
        file.setRelativePath(path);
        file.setContent(cleaned);
        return file;
    }

    /**
     * 若整段内容被 ``` 围栏包裹，则剥离最外层围栏（仅处理最外层一对）。
     */
    private String stripFenceIfAny(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            // 去掉首行围栏（如 ```vue）
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline < 0) {
                return "";
            }
            String body = trimmed.substring(firstNewline + 1);
            // 去掉结尾围栏
            int lastFence = body.lastIndexOf("```");
            if (lastFence >= 0) {
                body = body.substring(0, lastFence);
            }
            return body.trim();
        }
        return content;
    }

    /**
     * 从内容中提取所有代码块（含语言与起始偏移）。
     */
    private List<Block> extractCodeBlocks(String content) {
        List<Block> blocks = new ArrayList<>();
        Matcher matcher = CODE_BLOCK_PATTERN.matcher(content);
        while (matcher.find()) {
            Block block = new Block();
            block.language = matcher.group(1);
            block.content = matcher.group(2);
            block.start = matcher.start();
            blocks.add(block);
        }
        return blocks;
    }

    /**
     * 从内容中提取所有路径标注（含路径文本与起始偏移）。
     */
    private List<Mark> extractPathMarks(String content) {
        List<Mark> marks = new ArrayList<>();
        Matcher matcher = PATH_MARK_PATTERN.matcher(content);
        while (matcher.find()) {
            Mark mark = new Mark();
            mark.path = matcher.group(1);
            mark.start = matcher.start();
            marks.add(mark);
        }
        return marks;
    }

    /**
     * 找到代码块之前最近的一个路径标注（不存在则返回 null）。
     */
    private String findNearestPathBefore(List<Mark> marks, int blockStart) {
        String nearest = null;
        int nearestStart = -1;
        for (Mark mark : marks) {
            if (mark.start < blockStart && mark.start > nearestStart) {
                nearest = mark.path;
                nearestStart = mark.start;
            }
        }
        return nearest;
    }

    /**
     * 语言兜底：没有路径标注时按语言生成一个默认文件名。
     */
    private String fallbackPath(String language) {
        if (language == null || language.isBlank()) {
            return null;
        }
        return switch (language.toLowerCase()) {
            case "vue" -> "src/App.vue";
            case "ts", "typescript" -> "src/main.ts";
            case "json" -> "package.json";
            case "css" -> "src/style.css";
            case "html" -> "index.html";
            default -> "src/" + language + "-unknown.txt";
        };
    }

    /**
     * 尝试从内容中提取项目名（匹配 package.json 的 name 或注释标注），失败返回 null。
     */
    private String extractProjectName(String content) {
        Matcher nameMatcher = Pattern.compile("\"name\"\\s*:\\s*\"([a-zA-Z0-9_-]+)\"").matcher(content);
        if (nameMatcher.find()) {
            return nameMatcher.group(1);
        }
        Matcher markMatcher = Pattern.compile("(?m)^(?://|#)\\s*(?:项目名|projectName)\\s*[:：]\\s*([^\\r\\n]+)\\s*$")
                .matcher(content);
        if (markMatcher.find()) {
            return markMatcher.group(1).trim();
        }
        return null;
    }

    /** 代码块结构 */
    private static class Block {
        String language;
        String content;
        int start;
    }

    /** 路径标注结构 */
    private static class Mark {
        String path;
        int start;
    }
}
