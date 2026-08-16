package com.example.aicode.service;

import com.example.aicode.ai.model.HtmlCodeResult;
import com.example.aicode.ai.model.MultiFileCodeResult;
import dev.langchain4j.service.SystemMessage;
import reactor.core.publisher.Flux;

// ============================================================
// LangChain4j "AI Service" 关键概念（请先读这里）
// ------------------------------------------------------------
// 传统调用大模型：你需要自己构造 HTTP 请求、拼 prompt、解析 JSON……
// LangChain4j 的 AI Service 让你"像定义普通 Java 接口一样"调用 AI：
//   1. 声明一个 interface（本类）；
//   2. 加 @SystemMessage 等注解描述角色/系统提示词；
//   3. 方法返回值可以是 POJO（结构化输出，自动 JSON 反序列化），
//      也可以是 Flux<String>（流式输出，逐段返回文本）；
//   4. Spring 启动时会自动生成一个该接口的实现类（代理对象）注入容器，
//      你只需 @Resource 注入接口即可直接调用，无需手写实现。
//
// @SystemMessage(fromResource=...) 表示：从 classpath 下的资源文件读取
// "系统提示词（System Prompt）"，即规定 AI 身份与输出规范的指令。
// 例如 aaaa.txt 规定 AI 只输出纯语义 HTML；bbbb.txt 规定 AI 输出完整前端应用。
// ============================================================

/**
 * AI 代码生成服务接口（LangChain4j AI Service 声明式接口）
 * <p>
 * 定义与 AI 模型交互的四种代码生成方法，支持两种维度组合：
 * ① 模式：单文件 HTML（aaaa.txt 提示词） / 多文件（bbbb.txt 提示词）；
 * ② 返回方式：同步结构化输出（直接拿到完整对象） / 流式输出（逐段返回字符串）。
 *
 * @author yupi
 */
public interface AiCodeGeneratorService {

    /**
     * 生成 HTML 单文件代码（同步 / 结构化输出）
     * <p>
     * 返回值类型为 {@link HtmlCodeResult}，LangChain4j 会自动让 AI 以 JSON 返回
     * {htmlCode, description}，再反序列化为对象——这就是"结构化输出"。
     *
     * @param userMessage 用户提示词（例如"帮我做一个企业官网首页"）
     * @return 包含 HTML 代码和描述的 {@link HtmlCodeResult} 对象
     */
    @SystemMessage(fromResource = "prompt/aaaa.txt") // 从 resources/prompt/aaaa.txt 加载系统提示词
    HtmlCodeResult generateHtmlCode(String userMessage);

    /**
     * 生成多文件代码（同步 / 结构化输出）
     *
     * @param userMessage 用户提示词
     * @return 包含 HTML/CSS/JS 三个字段的 {@link MultiFileCodeResult} 对象
     */
    @SystemMessage(fromResource = "prompt/bbbb.txt")
    MultiFileCodeResult generateMultiFileCode(String userMessage);

    /**
     * 生成 HTML 单文件代码（流式输出）
     * <p>
     * 返回 {@link Flux}（Reactor 响应式流，来自 project-reactor）。
     * AI 每生成一小段文本就通过 onNext 推送一个 String，
     * 前端可用 SSE（Server-Sent Events）实时显示"打字机"效果。
     * 注意：流式模式下没有结构化解析，AI 原样返回 Markdown 文本。
     *
     * @param userMessage 用户提示词
     * @return 流式返回的代码片段字符串
     */
    @SystemMessage(fromResource = "prompt/aaaa.txt")
    Flux<String> generateHtmlCodeStream(String userMessage);

    /**
     * 生成多文件代码（流式输出）
     * <p>
     * 与上面类似，但 AI 会返回包含 ```html / ```css / ```js 代码块的完整文本，
     * 后续由 parser 模块用正则提取拆分。
     *
     * @param userMessage 用户提示词
     * @return 流式返回的代码内容
     */
    @SystemMessage(fromResource = "prompt/bbbb.txt")
    Flux<String> generateMultiFileCodeStream(String userMessage);

}


