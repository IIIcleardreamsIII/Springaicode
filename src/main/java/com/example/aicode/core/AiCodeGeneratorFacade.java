package com.example.aicode.core;

import com.example.aicode.ai.model.HtmlCodeResult;
import com.example.aicode.ai.model.MultiFileCodeResult;
import com.example.aicode.ai.model.enums.CodeGenTypeEnum;
import com.example.aicode.common.BusinessException;
import com.example.aicode.common.ErrorCode;
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
        HtmlCodeResult result = aiCodeGeneratorService.generateHtmlCode(userMessage);
        return CodeFileSaver.saveHtmlCodeResult(result);
    }

    /**
     * 生成多文件模式代码并保存（同步）。
     *
     * @param userMessage 提示词
     * @return 保存目录
     */
    private File generateAndSaveMultiFileCode(String userMessage) {
        MultiFileCodeResult result = aiCodeGeneratorService.generateMultiFileCode(userMessage);
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
        Flux<String> result = aiCodeGeneratorService.generateHtmlCodeStream(userMessage);
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
        Flux<String> result = aiCodeGeneratorService.generateMultiFileCodeStream(userMessage);
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
            } catch (Exception e) {
                log.error("保存失败: {}", e.getMessage());
            }
        });
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
                HtmlCodeResult result = aiCodeGeneratorService.generateHtmlCode(userMessage);
                yield CodeFileSaverExecutor.executeSaver(result, CodeGenTypeEnum.HTML);
            }
            case MULTI_FILE -> {
                MultiFileCodeResult result = aiCodeGeneratorService.generateMultiFileCode(userMessage);
                yield CodeFileSaverExecutor.executeSaver(result, CodeGenTypeEnum.MULTI_FILE);
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
                Flux<String> codeStream = aiCodeGeneratorService.generateHtmlCodeStream(userMessage);
                yield processCodeStream(codeStream, CodeGenTypeEnum.HTML, userMessage);
            }
            case MULTI_FILE -> {
                Flux<String> codeStream = aiCodeGeneratorService.generateMultiFileCodeStream(userMessage);
                yield processCodeStream(codeStream, CodeGenTypeEnum.MULTI_FILE, userMessage);
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
        return switch (codeGenTypeEnum) {
            case HTML -> {
                Flux<String> codeStream = aiCodeGeneratorService.generateHtmlCodeStream(userMessage);
                yield processCodeStream(codeStream, CodeGenTypeEnum.HTML, appId);
            }
            case MULTI_FILE -> {
                Flux<String> codeStream = aiCodeGeneratorService.generateMultiFileCodeStream(userMessage);
                yield processCodeStream(codeStream, CodeGenTypeEnum.MULTI_FILE, appId);
            }
            default -> {
                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }

}

