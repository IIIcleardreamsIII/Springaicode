package com.example.aicode.parser;

import com.example.aicode.ai.model.enums.CodeGenTypeEnum;
import com.example.aicode.common.BusinessException;
import com.example.aicode.common.ErrorCode;

/**
 * 代码解析执行器（策略模式中的 "上下文 / 调度器"）
 * <p>
 * 充当调用方与具体解析器之间的中间层：调用方只需传入 codeContent 和 codeGenType，
 * 本类根据类型选用预创建好的解析器并调用其 parseCode，返回结果对象。
 * 这样 controller / facade 等上层代码就不需要 if-else 判断该用哪个解析器了，
 * 将来新增类型也只改这里即可（符合"开闭原则"）。
 * <p>
 * 两个具体解析器被声明为 {@code static final} 字段，在类加载时各创建一次并复用，
 * 避免每次调用都 new 对象（解析器内部只有正则，无状态，线程安全，适合复用）。
 *
 * @author 程序员yaj
 */
public class CodeParserExecutor {

    /** 单文件 HTML 解析器单例（类加载时初始化一次） */
    private static final HtmlCodeParser htmlCodeParser = new HtmlCodeParser();

    /** 多文件解析器单例（类加载时初始化一次） */
    private static final MultiFileCodeParser multiFileCodeParser = new MultiFileCodeParser();

    /** 前端工程化项目解析器单例（类加载时初始化一次） */
    private static final ProjectCodeParser projectCodeParser = new ProjectCodeParser();

    /**
     * 根据代码生成类型，分派到对应的解析器执行解析。
     * <p>
     * 使用 Java 14+ 的 switch 表达式（箭头语法，直接返回表达式值）。
     *
     * @param codeContent  AI 返回的原始代码文本
     * @param codeGenType  代码生成类型枚举（HTML / MULTI_FILE）
     * @return 解析结果对象（HtmlCodeResult 或 MultiFileCodeResult）
     * @throws BusinessException 当类型为空或不支持时抛出
     */
    public static Object executeParser(String codeContent, CodeGenTypeEnum codeGenType) {
        if (codeGenType == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "代码生成类型为空");
        }
        return switch (codeGenType) {
            case HTML -> htmlCodeParser.parseCode(codeContent);
            case MULTI_FILE -> multiFileCodeParser.parseCode(codeContent);
            case PROJECT -> projectCodeParser.parseCode(codeContent);
            default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型: " + codeGenType);
        };
    }
}