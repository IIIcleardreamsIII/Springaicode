package com.example.aicode.core;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.example.aicode.ai.model.HtmlCodeResult;
import com.example.aicode.ai.model.MultiFileCodeResult;
import com.example.aicode.ai.model.ProjectCodeResult;
import com.example.aicode.ai.model.enums.CodeGenTypeEnum;
import com.example.aicode.common.BusinessException;
import com.example.aicode.common.ErrorCode;
import com.example.aicode.constant.AppConstant;
import com.example.aicode.parser.CodeParserExecutor;
import com.example.aicode.saver.CodeFileSaverExecutor;
import com.example.aicode.service.AiCodeGeneratorService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;

// ============================================================
// 外观模式（Facade）+ 响应式编程（Reactor Flux）科普（请先读这里）
// ------------------------------------------------------------
// ① 外观模式：把"生成→解析→保存"一整套复杂流程封装到一个类里，
//    调用方（controller）只需调一个方法，不用关心内部谁先谁后。
//
// ② Flux<String>（来自 Reactor 响应式库）：
//    表示一个"0 到 N 个元素的异步流"。AI 流式生成时，每产生一小段文本，
//    就往流里 push 一个 String。常用算子：
//      - doOnNext(x)    ：每来一个元素时执行副作用（这里用于拼接收集）
//      - doOnComplete() ：流结束时执行一次（这里用于解析+落盘）
//    注意：Flux 是"惰性"的——只有被订阅（subscribe）后才会真正开始执行，
//    在 controller 里通过 .subscribe(...) 触发。
// ============================================================

/**
 * AI 代码生成外观门面类（核心编排层）
 * <p>
 * 作为对外统一入口，组合 AI 代码生成、解析、保存等功能，
 * 支持同步与流式两种模式，根据 {@link CodeGenTypeEnum} 分发到对应的处理流程。
 *
 * @author yupi
 */
@Slf4j // 自动生成日志变量 log
@Service // 注册为 Spring 单例 Bean，供 controller 注入
public class AiCodeGeneratorFacade {

    /** AI 代码生成服务（LangChain4j 代理对象），门面通过它调用大模型 */
    @Resource
    private AiCodeGeneratorService aiCodeGeneratorService;

    // ===================== 同步分支（当前 controller 未直接调用，保留作参考） =====================

    /**
     * 生成 HTML 模式代码并保存（同步）。
     *
     * @param userMessage 提示词
     * @return 保存目录
     */
    private File generateAndSaveHtmlCode(String userMessage) {
        HtmlCodeResult result = aiCodeGeneratorService.generateHtmlCode(AiCodeGeneratorService.DEFAULT_MEMORY_ID, userMessage);
        return CodeFileSaver.saveHtmlCodeResult(result);
    }

    /**
     * 生成多文件模式代码并保存（同步）。
     *
     * @param userMessage 提示词
     * @return 保存目录
     */
    private File generateAndSaveMultiFileCode(String userMessage) {
        MultiFileCodeResult result = aiCodeGeneratorService.generateMultiFileCode(AiCodeGeneratorService.DEFAULT_MEMORY_ID, userMessage);
        return CodeFileSaver.saveMultiFileCodeResult(result);
    }

    // ===================== 流式分支（实际被 controller 流式接口调用） =====================

    /**
     * 生成 HTML 模式代码并保存（流式）。
     * <p>
     * 关键思路：先拿到 AI 的 Flux 流，用 doOnNext 把每段拼到 StringBuilder，
     * 用 doOnComplete 在"全部生成完毕"后再做解析+落盘——从而流式展示与最终落盘两不误。
     *
     * @param userMessage 提示词
     * @return 透传的流（每段文本原样返回给前端）
     */
    private Flux<String> generateAndSaveHtmlCodeStream(String userMessage) {
        // 调用 AI 流式接口，返回逐段文本流
        Flux<String> result = aiCodeGeneratorService.generateHtmlCodeStream(AiCodeGeneratorService.DEFAULT_MEMORY_ID, userMessage);
        StringBuilder codeBuilder = new StringBuilder();
        return result
                .doOnNext(chunk -> {
                    // 每收到一段，追加到缓冲（不直接落盘，避免写到半成品）
                    codeBuilder.append(chunk);
                })
                .doOnComplete(() -> {
                    // 流结束：把缓冲的完整文本交给解析器，再落盘
                    try {
                        String completeHtmlCode = codeBuilder.toString();
                        HtmlCodeResult htmlCodeResult = CodeParser.parseHtmlCode(completeHtmlCode);
                        File savedDir = CodeFileSaver.saveHtmlCodeResult(htmlCodeResult);
                        log.info("保存成功，路径为：" + savedDir.getAbsolutePath());
                    } catch (Exception e) {
                        // 落盘失败只记录日志，不影响前端已收到的流式内容
                        log.error("保存失败: {}", e.getMessage());
                    }
                });
    }

    /**
     * 生成多文件模式代码并保存（流式），逻辑同 {@link #generateAndSaveHtmlCodeStream}。
     *
     * @param userMessage 提示词
     * @return 透传的流
     */
    private Flux<String> generateAndSaveMultiFileCodeStream(String userMessage) {
        Flux<String> result = aiCodeGeneratorService.generateMultiFileCodeStream(AiCodeGeneratorService.DEFAULT_MEMORY_ID, userMessage);
        StringBuilder codeBuilder = new StringBuilder();
        return result
                .doOnNext(chunk -> codeBuilder.append(chunk))
                .doOnComplete(() -> {
                    try {
                        String completeMultiFileCode = codeBuilder.toString();
                        MultiFileCodeResult multiFileResult = CodeParser.parseMultiFileCode(completeMultiFileCode);
                        File savedDir = CodeFileSaver.saveMultiFileCodeResult(multiFileResult);
                        log.info("保存成功，路径为：" + savedDir.getAbsolutePath());
                    } catch (Exception e) {
                        log.error("保存失败: {}", e.getMessage());
                    }
                });
    }

    // ===================== 通用流式处理（被上面两个方法复用不到，被统一入口调用） =====================

    /**
     * 通用流式代码处理方法（核心）。
     * <p>
     * 与上面两个方法不同，本方法使用"更规范"的 parser/saver 执行器，
     * 并在落盘后额外保存 prompt.txt 供历史展示。controller 的流式接口实际走这里。
     *
     * @param codeStream  来自 AI 服务的代码字符流
     * @param codeGenType 代码生成类型（决定用哪个解析器/保存器）
     * @param userMessage 用户提示词，落盘为 prompt.txt
     * @return 透传的原始代码流，供前端实时展示
     */
    private Flux<String> processCodeStream(Flux<String> codeStream, CodeGenTypeEnum codeGenType, String userMessage) {
        StringBuilder codeBuilder = new StringBuilder();
        return codeStream.doOnNext(chunk -> {
            codeBuilder.append(chunk); // 实时收集
        }).doOnComplete(() -> {
            try {
                String completeCode = codeBuilder.toString();
                // 用解析执行器按类型解析（策略模式分发）
                Object parsedResult = CodeParserExecutor.executeParser(completeCode, codeGenType);
                // 用保存执行器按类型落盘（模板方法模式分发）
                File savedDir = CodeFileSaverExecutor.executeSaver(parsedResult, codeGenType);
                // 把提示词也存一份
                if (userMessage != null && !userMessage.isBlank()) {
                    cn.hutool.core.io.FileUtil.writeUtf8String(userMessage,
                            savedDir.getAbsolutePath() + File.separator + "prompt.txt");
                }
                log.info("保存成功，路径为：" + savedDir.getAbsolutePath());
            } catch (Exception e) {
                log.error("保存失败: {}", e.getMessage());
            }
        });
    }

    /**
     * 通用流式代码处理方法（应用集成版）。
     * <p>
     * 与三参版本的差异：
     * ① 落盘目录命名为 {@code codeGenType_appId}，使网站文件与应用记录关联；
     * ② 不再保存 prompt.txt——提示词已作为应用属性（initPrompt）入库，无需冗余存储。
     *
     * @param codeStream  来自 AI 服务的代码字符流
     * @param codeGenType 代码生成类型（决定用哪个解析器/保存器）
     * @param appId       应用 id（用于构建目录名）
     * @return 透传的原始代码流，供前端实时展示
     */
    private Flux<String> processCodeStream(Flux<String> codeStream, CodeGenTypeEnum codeGenType, Long appId) {
        StringBuilder codeBuilder = new StringBuilder();
        return codeStream.doOnNext(chunk -> {
            codeBuilder.append(chunk); // 实时收集
        }).doOnComplete(() -> {
            try {
                String completeCode = codeBuilder.toString();
                // 用解析执行器按类型解析（策略模式分发）
                Object parsedResult = CodeParserExecutor.executeParser(completeCode, codeGenType);
                // 用保存执行器按类型落盘，目录名关联 appId
                File savedDir = CodeFileSaverExecutor.executeSaver(parsedResult, codeGenType, appId);
                log.info("保存成功，路径为：" + savedDir.getAbsolutePath());
                // 工程化项目类型：生成完成后立即构建出 dist，供预览通道访问。
                // 构建失败仅记录日志，不阻断流式返回（前端会展示"构建失败"提示）。
                if (codeGenType == CodeGenTypeEnum.PROJECT) {
                    buildProjectForPreview(savedDir);
                }
            } catch (Exception e) {
                log.error("保存失败: {}", e.getMessage());
            }
        });
    }

    /**
     * 构建工程化项目用于预览（在生成落盘后调用）。
     * <p>
     * 构建产物写入 {@code {projectDir}/dist}，预览通道会读取该 dist 目录。
     * 构建是耗时操作（首次 npm install 可能几十秒），此处异步执行避免阻塞
     * SSE 流式响应；构建结果由前端轮询 dist/index.html 是否存在来判断。
     *
     * @param projectDir 工程项目根目录（已落盘源码）
     */
    private void buildProjectForPreview(File projectDir) {
        try {
            ProjectBuilder.build(projectDir);
            log.info("工程化项目构建完成，预览产物已生成：{}", projectDir.getAbsolutePath());
        } catch (Exception e) {
            log.error("工程化项目构建失败（预览不可用，但不影响部署）：{}", e.getMessage());
        }
    }

    /**
     * 统一入口：根据类型生成并保存代码（同步）。
     * <p>
     * 流程：判空 → switch 分发 → 调 AI 同步生成 → 执行器落盘。
     *
     * @param userMessage     提示词
     * @param codeGenTypeEnum 代码生成类型枚举
     * @return 保存代码的目标目录
     * @throws BusinessException 类型为空或不支持时抛出
     */
    public File generateAndSaveCode(String userMessage, CodeGenTypeEnum codeGenTypeEnum) {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型为空"); // 业务异常，由全局异常处理器转 HTTP 响应
        }
        return switch (codeGenTypeEnum) {
            case HTML -> {
                HtmlCodeResult result = aiCodeGeneratorService.generateHtmlCode(AiCodeGeneratorService.DEFAULT_MEMORY_ID, userMessage);
                yield CodeFileSaverExecutor.executeSaver(result, CodeGenTypeEnum.HTML);
            }
            case MULTI_FILE -> {
                MultiFileCodeResult result = aiCodeGeneratorService.generateMultiFileCode(AiCodeGeneratorService.DEFAULT_MEMORY_ID, userMessage);
                yield CodeFileSaverExecutor.executeSaver(result, CodeGenTypeEnum.MULTI_FILE);
            }
            case PROJECT -> {
                ProjectCodeResult result = aiCodeGeneratorService.generateProjectCode(AiCodeGeneratorService.DEFAULT_MEMORY_ID, userMessage);
                yield CodeFileSaverExecutor.executeSaver(result, CodeGenTypeEnum.PROJECT);
            }
            default -> {
                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }

    /**
     * 统一入口：根据类型生成并保存代码（流式）。
     * <p>
     * 流程：判空 → switch 分发 → 调 AI 流式生成 → 交给 processCodeStream 统一收集/解析/落盘。
     * 这是 controller /generate/stream 最终调用的方法。
     *
     * @param userMessage     提示词
     * @param codeGenTypeEnum 代码生成类型枚举
     * @return 流式返回的代码字符串，供前端实时展示生成进度
     * @throws BusinessException 类型为空或不支持时抛出
     */
    public Flux<String> generateAndSaveCodeStream(String userMessage, CodeGenTypeEnum codeGenTypeEnum) {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型为空");
        }
        return switch (codeGenTypeEnum) {
            case HTML -> {
                Flux<String> codeStream = aiCodeGeneratorService.generateHtmlCodeStream(AiCodeGeneratorService.DEFAULT_MEMORY_ID, userMessage);
                yield processCodeStream(codeStream, CodeGenTypeEnum.HTML, userMessage);
            }
            case MULTI_FILE -> {
                Flux<String> codeStream = aiCodeGeneratorService.generateMultiFileCodeStream(AiCodeGeneratorService.DEFAULT_MEMORY_ID, userMessage);
                yield processCodeStream(codeStream, CodeGenTypeEnum.MULTI_FILE, userMessage);
            }
            case PROJECT -> {
                Flux<String> codeStream = aiCodeGeneratorService.generateProjectCodeStream(AiCodeGeneratorService.DEFAULT_MEMORY_ID, userMessage);
                yield processCodeStream(codeStream, CodeGenTypeEnum.PROJECT, userMessage);
            }
            default -> {
                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }

    /**
     * 统一入口（应用集成版）：根据类型生成并保存代码（流式）。
     * <p>
     * 供"应用对话生成代码"流程调用，与两参版本流程一致，
     * 但落盘目录命名为 {@code codeGenType_appId}，
     * 生成结果与指定应用绑定（AppController#chatToGenCode 使用本方法）。
     *
     * @param userMessage     提示词（通常为应用的 initPrompt）
     * @param codeGenTypeEnum 代码生成类型枚举
     * @param appId           应用 id（用于构建目录名）
     * @return 流式返回的代码字符串，供前端实时展示生成进度
     * @throws BusinessException 类型为空或不支持时抛出
     */
    public Flux<String> generateAndSaveCodeStream(String userMessage, CodeGenTypeEnum codeGenTypeEnum, Long appId) {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型为空");
        }
        // 后续对话修改：若应用已存在生成的代码，则把现有代码文件内容作为上下文拼进提示词，
        // 让 AI 在原有代码基础上做最小化修改，而不是推倒重来、重新生成一套全新代码。
        String effectivePrompt = buildModifyPrompt(userMessage, codeGenTypeEnum, appId);
        return switch (codeGenTypeEnum) {
            case HTML -> {
                Flux<String> codeStream = aiCodeGeneratorService.generateHtmlCodeStream(appId, effectivePrompt);
                yield processCodeStream(codeStream, CodeGenTypeEnum.HTML, appId);
            }
            case MULTI_FILE -> {
                Flux<String> codeStream = aiCodeGeneratorService.generateMultiFileCodeStream(appId, effectivePrompt);
                yield processCodeStream(codeStream, CodeGenTypeEnum.MULTI_FILE, appId);
            }
            case PROJECT -> {
                Flux<String> codeStream = aiCodeGeneratorService.generateProjectCodeStream(appId, effectivePrompt);
                yield processCodeStream(codeStream, CodeGenTypeEnum.PROJECT, appId);
            }
            default -> {
                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }

    /**
     * 构建本轮实际使用的提示词（支持"基于原代码修改"）。
     * <p>
     * 若该应用已生成过代码（对应输出目录存在），则读取现有代码文件，
     * 拼装成"现有代码 + 用户新需求"的修改指令，让 AI 基于原代码修改；
     * 首次生成（目录不存在或没读到有效代码）时原样返回 userMessage。
     *
     * @param userMessage     本轮用户消息
     * @param codeGenTypeEnum 代码生成类型
     * @param appId           应用 id
     * @return 实际发送给 AI 的提示词
     */
    private String buildModifyPrompt(String userMessage, CodeGenTypeEnum codeGenTypeEnum, Long appId) {
        // 应用专属输出目录：{CODE_OUTPUT_ROOT_DIR}/{type}_{appId}
        String dirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator
                + StrUtil.format("{}_{}", codeGenTypeEnum.getValue(), appId);
        File codeDir = new File(dirPath);
        // 首次生成：目录不存在，直接用原提示词
        if (!codeDir.exists() || !codeDir.isDirectory()) {
            return userMessage;
        }
        // 读取已有代码文件内容（HTML 模式仅 index.html；多文件模式额外读取 css/js；
        // 工程化项目模式递归读取整个工程目录下的所有文件）
        StringBuilder context = new StringBuilder();
        appendFileContent(context, codeDir, "index.html");
        if (codeGenTypeEnum == CodeGenTypeEnum.MULTI_FILE) {
            appendFileContent(context, codeDir, "style.css");
            appendFileContent(context, codeDir, "script.js");
        } else if (codeGenTypeEnum == CodeGenTypeEnum.PROJECT) {
            appendProjectFiles(context, codeDir);
        }
        // 目录存在但没读到有效代码，退化为全新生成
        if (context.length() == 0) {
            return userMessage;
        }
        // 拼装"基于原代码修改"的提示词
        return StrUtil.format(
                "以下是该应用当前已生成的完整代码，请严格基于这些原代码进行修改，不要重新生成一套全新的代码：\n\n" +
                        "{}\n" +
                        "用户的新需求：{}\n\n" +
                        "请只针对上述需求对现有代码做最小化修改，保持其余功能、结构与样式不变，并按系统要求输出修改后的完整代码。",
                context, userMessage);
    }

    /**
     * 读取目录下指定文件内容并追加到上下文（带代码块标记，便于 AI 识别语言）。
     *
     * @param context  上下文字符串构建器
     * @param codeDir  代码目录
     * @param filename 文件名（如 index.html）
     */
    private void appendFileContent(StringBuilder context, File codeDir, String filename) {
        File file = new File(codeDir, filename);
        if (!file.exists() || !file.isFile()) {
            return;
        }
        try {
            String content = FileUtil.readUtf8String(file);
            if (StrUtil.isNotBlank(content)) {
                context.append("【文件：").append(filename).append("】\n")
                        .append("```").append(languageOf(filename)).append('\n')
                        .append(content).append('\n')
                        .append("```\n\n");
            }
        } catch (Exception e) {
            log.warn("读取已有代码文件失败：{}", file.getAbsolutePath(), e);
        }
    }

    /**
     * 根据文件名返回代码块语言标识。
     *
     * @param filename 文件名
     * @return 语言标识（html/css/js/ts/vue/json 等）
     */
    private String languageOf(String filename) {
        if (filename.endsWith(".html")) {
            return "html";
        }
        if (filename.endsWith(".css")) {
            return "css";
        }
        if (filename.endsWith(".js")) {
            return "js";
        }
        if (filename.endsWith(".ts")) {
            return "ts";
        }
        if (filename.endsWith(".vue")) {
            return "vue";
        }
        if (filename.endsWith(".json")) {
            return "json";
        }
        return "";
    }

    /**
     * 递归读取工程化项目目录下的所有文件，按相对路径拼入上下文。
     * <p>
     * 跳过 node_modules、dist、.git 等无需参与修改的目录，避免上下文过长。
     *
     * @param context 上下文字符串构建器
     * @param codeDir 工程项目根目录
     */
    private void appendProjectFiles(StringBuilder context, File codeDir) {
        File[] files = codeDir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            String name = file.getName();
            // 跳过常见的大型/生成目录
            if (file.isDirectory()) {
                if ("node_modules".equals(name) || "dist".equals(name) || ".git".equals(name) || "target".equals(name)) {
                    continue;
                }
                appendProjectFiles(context, file);
            } else if (file.isFile()) {
                // 用相对工程根目录的路径作为文件名标识
                String relative = codeDir.toPath().relativize(file.toPath()).toString()
                        .replace('\\', '/');
                appendFileContent(context, codeDir, relative);
            }
        }
    }

}

