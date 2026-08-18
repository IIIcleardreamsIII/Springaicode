package com.example.aicode.service;

import com.example.aicode.ai.model.HtmlCodeResult;
import com.example.aicode.ai.model.MultiFileCodeResult;
import com.example.aicode.ai.model.ProjectCodeResult;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
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
//
// 【对话记忆（Chat Memory）】关键概念（本类核心）：
// 方法参数上标注 @MemoryId 的字段，LangChain4j 会把它当作"记忆隔离 id"：
//   - 每次调用前，框架用 memoryId 从 ChatMemoryProvider 取出该会话的历史消息；
//   - 调用结束后，把本轮"用户消息 + AI 回复"自动追加回记忆；
//   - 不同 memoryId 的记忆相互独立（例如不同应用之间的对话互不干扰）。
// 记忆的持久化由 ChatMemoryProvider + RedisChatMemoryStore 完成（见工厂类），
// 历史消息被序列化存进 Redis，即使服务重启，记忆依然存在。
// ============================================================

/**
 * AI 代码生成服务接口（LangChain4j AI Service 声明式接口）
 * <p>
 * 定义与 AI 模型交互的四种代码生成方法，支持两种维度组合：
 * ① 模式：单文件 HTML（aaaa.txt 提示词） / 多文件（bbbb.txt 提示词）；
 * ② 返回方式：同步结构化输出（直接拿到完整对象） / 流式输出（逐段返回字符串）。
 * <p>
 * 所有方法第一个参数均为 {@code @MemoryId Object memoryId}，用于隔离对话记忆：
 * 应用对话场景传入 appId，则同一个应用的多次对话能记住上下文；
 * 不同 memoryId 之间记忆互不干扰。无应用上下文的场景使用 {@link #DEFAULT_MEMORY_ID}。
 *
 * @author yupi
 */
public interface AiCodeGeneratorService {

    /**
     * 默认记忆 id：无应用上下文（旧版 /code 接口等）时使用，所有此类调用共享同一记忆。
     * 接口中的字段默认 public static final，可直接通过 AiCodeGeneratorService.DEFAULT_MEMORY_ID 引用。
     */
    String DEFAULT_MEMORY_ID = "default";

    /**
     * 生成 HTML 单文件代码（同步 / 结构化输出）
     * <p>
     * 返回值类型为 {@link HtmlCodeResult}，LangChain4j 会自动让 AI 以 JSON 返回
     * {htmlCode, description}，再反序列化为对象——这就是"结构化输出"。
     *
     * @param memoryId    记忆隔离 id（如 appId），用于区分不同会话的上下文
     * @param userMessage 用户提示词（例如"帮我做一个企业官网首页"）
     * @return 包含 HTML 代码和描述的 {@link HtmlCodeResult} 对象
     */
    @SystemMessage(fromResource = "prompt/aaaa.txt") // 从 resources/prompt/aaaa.txt 加载系统提示词
    @UserMessage("{{userMessage}}")
    HtmlCodeResult generateHtmlCode(@MemoryId Object memoryId, @V("userMessage") String userMessage);

    /**
     * 生成多文件代码（同步 / 结构化输出）
     *
     * @param memoryId    记忆隔离 id
     * @param userMessage 用户提示词
     * @return 包含 HTML/CSS/JS 三个字段的 {@link MultiFileCodeResult} 对象
     */
    @SystemMessage(fromResource = "prompt/bbbb.txt")
    @UserMessage("{{userMessage}}")
    MultiFileCodeResult generateMultiFileCode(@MemoryId Object memoryId, @V("userMessage") String userMessage);

    /**
     * 生成 HTML 单文件代码（流式输出）
     * <p>
     * 返回 {@link Flux}（Reactor 响应式流，来自 project-reactor）。
     * AI 每生成一小段文本就通过 onNext 推送一个 String，
     * 前端可用 SSE（Server-Sent Events）实时显示"打字机"效果。
     * 注意：流式模式下没有结构化解析，AI 原样返回 Markdown 文本。
     *
     * @param memoryId    记忆隔离 id
     * @param userMessage 用户提示词
     * @return 流式返回的代码片段字符串
     */
    @SystemMessage(fromResource = "prompt/aaaa.txt")
    @UserMessage("{{userMessage}}")
    Flux<String> generateHtmlCodeStream(@MemoryId Object memoryId, @V("userMessage") String userMessage);

    /**
     * 生成多文件代码（流式输出）
     * <p>
     * 与上面类似，但 AI 会返回包含 ```html / ```css / ```js 代码块的完整文本，
     * 后续由 parser 模块用正则提取拆分。
     *
     * @param memoryId    记忆隔离 id
     * @param userMessage 用户提示词
     * @return 流式返回的代码内容
     */
    @SystemMessage(fromResource = "prompt/bbbb.txt")
    @UserMessage("{{userMessage}}")
    Flux<String> generateMultiFileCodeStream(@MemoryId Object memoryId, @V("userMessage") String userMessage);

    /**
     * 生成前端工程化项目（同步 / 结构化输出）。
     * <p>
     * 返回值类型为 {@link ProjectCodeResult}，AI 以 JSON 返回
     * {projectName, description, fileList:[{relativePath, content}]}，
     * 后端按 fileList 逐一落盘为工程文件。
     *
     * @param memoryId    记忆隔离 id
     * @param userMessage 用户提示词
     * @return 前端工程化项目结果对象
     */
    @SystemMessage(fromResource = "prompt/cccc.txt")
    @UserMessage("{{userMessage}}")
    ProjectCodeResult generateProjectCode(@MemoryId Object memoryId, @V("userMessage") String userMessage);

    /**
     * 生成前端工程化项目（流式输出）。
     * <p>
     * 流式返回完整 Markdown 文本，包含多个按相对路径标注的代码块，
     * 后续由 {@code ProjectCodeParser} 解析出文件清单并落盘。
     *
     * @param memoryId    记忆隔离 id
     * @param userMessage 用户提示词
     * @return 流式返回的代码文本
     */
    @SystemMessage(fromResource = "prompt/cccc.txt")
    @UserMessage("{{userMessage}}")
    Flux<String> generateProjectCodeStream(@MemoryId Object memoryId, @V("userMessage") String userMessage);

    /**
     * 修复工程化项目（流式输出）。
     * <p>
     * 用于"构建失败自愈"：把编译错误与当前源码拼进 userMessage，要求 AI 修复出错文件后
     * 流式返回 Markdown 文本（保持「// 文件：相对路径」+ 代码块格式），
     * 后端用 {@code ProjectCodeParser} 解析后重新落盘并重新构建。
     * <p>
     * <b>为何返回 Flux&lt;String&gt; 而非 ProjectCodeResult：</b>
     * cccc.txt 规定 AI 输出「// 文件：路径 + 代码块」的 Markdown 格式，而非 JSON。
     * 若声明返回 POJO，LangChain4j 会强制 AI 输出 JSON，与 cccc.txt 冲突，
     * 导致修复结果反序列化失败或为空。故这里与 generateProjectCodeStream 保持一致，
     * 返回流式文本，由解析器统一解析。
     *
     * @param memoryId    记忆隔离 id
     * @param userMessage 包含编译错误与当前源码的修复提示词
     * @return 流式返回的修复后代码文本
     */
    @SystemMessage(fromResource = "prompt/cccc.txt")
    @UserMessage("{{userMessage}}")
    Flux<String> fixProjectCode(@MemoryId Object memoryId, @V("userMessage") String userMessage);

}
