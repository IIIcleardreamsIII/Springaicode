package com.example.aicode.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.example.aicode.ai.model.HtmlCodeResult;
import com.example.aicode.ai.model.MultiFileCodeResult;
import com.example.aicode.ai.model.ProjectCodeResult;
import com.example.aicode.ai.model.enums.CodeGenTypeEnum;
import com.example.aicode.common.BaseResponse;
import com.example.aicode.common.BusinessException;
import com.example.aicode.common.ErrorCode;
import com.example.aicode.common.ResultUtils;
import com.example.aicode.common.ThrowUtils;
import com.example.aicode.constant.AppConstant;
import com.example.aicode.core.AiCodeGeneratorFacade;
import com.example.aicode.core.CodeFileSaver;
import com.example.aicode.model.dto.app.AppAddRequest;
import com.example.aicode.model.dto.code.CodeGenerateRequest;
import com.example.aicode.model.entity.App;
import com.example.aicode.model.entity.User;
import com.example.aicode.saver.CodeFileSaverExecutor;
import com.example.aicode.service.AiCodeGeneratorService;
import com.example.aicode.service.AppService;
import com.example.aicode.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * AI 代码生成控制器（HTTP 接口层 / 表现层）
 * <p>
 * 这是前端直接调用的入口，提供四类能力：
 * ① {@code /generate}        同步生成（等 AI 全部生成完再返回）；
 * ② {@code /generate/stream}  SSE 流式生成（边生成边推送，打字机效果）；
 * ③ {@code /list}             生成历史列表（读取磁盘目录）；
 * ④ {@code /content}         查看某次生成的代码内容。
 *
 * @author AI Code
 */
@Slf4j // Lombok：自动生成 log 字段（SLF4J 日志门面），可直接用 log.info/error/warn
@Tag(name = "AI 代码生成", description = "AI 生成 HTML / 多文件代码") // Swagger/Knife4j 文档：给接口分组起名
@RestController // = @Controller + @ResponseBody：本类所有方法返回值直接序列化为 JSON（而非跳转视图）
@RequestMapping("/code") // 类级路由前缀：本类所有接口都在 /api/code 下（/api 来自 application.yml 的 context-path）
public class CodeGeneratorController {

    /** 文件保存根目录，统一取自 {@link AppConstant#CODE_OUTPUT_ROOT_DIR}（与 saver 模块保持一致） */
    private static final String FILE_SAVE_ROOT_DIR = AppConstant.CODE_OUTPUT_ROOT_DIR;

    /** 注入 AI 服务（LangChain4j 生成的代理实现），用于同步生成 */
    @Resource // Spring：按类型注入容器中的 Bean（等价于 @Autowired，但更语义化）
    private AiCodeGeneratorService aiCodeGeneratorService;

    /** 注入门面（facade），主要用于流式生成+保存的编排 */
    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;

    /** 注入应用服务，用于把独立生成器的产物登记为"我的应用" */
    @Resource
    private AppService appService;

    /** 注入用户服务，用于获取当前登录用户（应用归属） */
    @Resource
    private UserService userService;

    /** 注入当前请求，用于获取登录态 */
    @Resource
    private HttpServletRequest httpServletRequest;

    // ==================== 代码生成 ====================

    /**
     * 同步生成代码接口（POST）。
     * 流程：校验 → 查枚举 → switch 调 AI 生成 → 落盘 → 保存提示词 → 返回结果对象。
     *
     * @param request 请求体（含 userMessage 提示词、codeGenType 类型）
     * @return 统一的成功响应，data 为 HtmlCodeResult / MultiFileCodeResult
     */
    @Operation(summary = "同步生成代码") // Swagger 文档：接口说明
    @PostMapping("/generate") // 映射 POST /api/code/generate；@RequestBody 表示整个 JSON 体反序列化为 CodeGenerateRequest
    public BaseResponse<Object> generateCode(@RequestBody CodeGenerateRequest request) {
        // ThrowUtils.throwIf：条件成立则抛异常（简化 if-throw 样板代码）
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        String userMessage = request.getUserMessage();
        String codeGenType = request.getCodeGenType();
        // 提示词与类型都不能为空
        ThrowUtils.throwIf(StrUtil.isBlank(userMessage), ErrorCode.PARAMS_ERROR, "提示词不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(codeGenType), ErrorCode.PARAMS_ERROR, "生成类型不能为空");

        // 把前端传来的字符串类型转成枚举（兼做合法性校验）
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
        ThrowUtils.throwIf(codeGenTypeEnum == null, ErrorCode.PARAMS_ERROR, "生成类型不合法");

        // 先把本次生成登记为一个"我的应用"，使产物出现在应用列表里。
        // 应用记录与代码目录通过 {codeGenType}_{appId} 命名关联，复用现有预览/部署链路。
        long appId = createAppForStandalone(userMessage, codeGenTypeEnum, request.getAppName(), httpServletRequest);

        // switch 表达式：按类型分发。注意这里"同步"路径用的是旧工具类 CodeFileSaver，
        // 而流式路径用的是 facade + 模板方法。两者都能落盘，只是设计风格不同。
        // 落盘目录统一用 {codeGenType}_{appId}，与 App 记录关联。
        Object result = switch (codeGenTypeEnum) {
            case HTML -> {
                // 调 AI（结构化输出）拿到结果对象
                HtmlCodeResult htmlResult = aiCodeGeneratorService.generateHtmlCode(AiCodeGeneratorService.DEFAULT_MEMORY_ID, userMessage);
                // 用旧工具类把结果写盘，返回保存目录
                File savedDir = CodeFileSaver.saveHtmlCodeResult(htmlResult, codeGenTypeEnum, appId);
                savePrompt(savedDir, userMessage);
                yield htmlResult; // switch 表达式用 yield 返回值
            }
            case MULTI_FILE -> {
                MultiFileCodeResult multiResult = aiCodeGeneratorService.generateMultiFileCode(AiCodeGeneratorService.DEFAULT_MEMORY_ID, userMessage);
                File savedDir = CodeFileSaver.saveMultiFileCodeResult(multiResult, codeGenTypeEnum, appId);
                savePrompt(savedDir, userMessage);
                yield multiResult;
            }
            case PROJECT -> {
                // 工程化项目：结构化输出 + 执行器落盘（支持多级目录文件清单）
                ProjectCodeResult projectResult = aiCodeGeneratorService.generateProjectCode(AiCodeGeneratorService.DEFAULT_MEMORY_ID, userMessage);
                File savedDir = CodeFileSaverExecutor.executeSaver(projectResult, CodeGenTypeEnum.PROJECT, appId);
                savePrompt(savedDir, userMessage);
                yield projectResult;
            }
            default -> throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的生成类型：" + codeGenType);
        };

        // 返回中附带 appId，前端可据此提示"已加入我的应用"并跳转
        Map<String, Object> response = new HashMap<>();
        response.put("result", result);
        response.put("appId", appId);
        // ResultUtils.success：包装成统一响应结构 {code, data, message}
        return ResultUtils.success(response);
    }

    /**
     * 把独立生成器的一次生成登记为一个"我的应用"记录。
     * <p>
     * 这样独立生成器产出的代码（落盘到 {@code {codeGenType}_{appId}}）会出现在
     * "我的应用"列表里，可像主页创建的应用一样预览、部署、对话修改。
     *
     * @param userMessage     用户提示词（作为应用初始 prompt 与默认名称来源）
     * @param codeGenTypeEnum 生成类型
     * @param appName         应用名称（可选，为空则取提示词前若干字）
     * @return 新创建的应用 id
     */
    private long createAppForStandalone(String userMessage, CodeGenTypeEnum codeGenTypeEnum, String appName, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        App app = new App();
        app.setInitPrompt(userMessage);
        String name = StrUtil.isBlank(appName)
                ? userMessage.substring(0, Math.min(userMessage.length(), 12))
                : appName;
        app.setAppName(name);
        app.setCodeGenType(codeGenTypeEnum.getValue());
        app.setUserId(loginUser.getId());
        boolean saved = appService.save(app);
        ThrowUtils.throwIf(!saved, ErrorCode.OPERATION_ERROR, "创建应用失败");
        return app.getId();
    }

    /**
     * SSE 流式生成代码接口（GET）。
     * <p>
     * SSE（Server-Sent Events）是一种服务端单向持续推送技术：客户端发一次请求，
     * 服务端可以分多次推送文本，适合"AI 逐字生成"的场景。
     * 本接口返回 {@link SseEmitter}，Spring 会把它变成 text/event-stream 响应。
     * <p>
     * 推送协议约定：普通消息 data 为 JSON {@code {"d": "代码片段"}}（保留空白字符）；
     * 生成完成推送名为 {@code done} 的命名事件；异常推送名为 {@code error} 的命名事件。
     *
     * @param userMessage 用户提示词（URL 查询参数）
     * @param codeGenType 生成类型（URL 查询参数）
     * @return SseEmitter 流式响应对象
     */
    @Operation(summary = "流式生成代码（SSE）")
    @GetMapping(value = "/generate/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE) // 指定响应类型为事件流
    public SseEmitter generateCodeStream(
            @RequestParam String userMessage, // URL ?userMessage=xxx
            @RequestParam String codeGenType) {
        ThrowUtils.throwIf(StrUtil.isBlank(userMessage), ErrorCode.PARAMS_ERROR, "提示词不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(codeGenType), ErrorCode.PARAMS_ERROR, "生成类型不能为空");

        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
        ThrowUtils.throwIf(codeGenTypeEnum == null, ErrorCode.PARAMS_ERROR, "生成类型不合法");

        // 先把本次生成登记为一个"我的应用"（应用 id 用于落盘目录名与产物关联）
        long appId = createAppForStandalone(userMessage, codeGenTypeEnum, null, httpServletRequest);

        // SseEmitter：代表一次长连接；300000ms = 5 分钟超时（AI 生成较慢，需放宽超时）
        SseEmitter emitter = new SseEmitter(300000L);

        // facade 返回 Flux<String>（响应式流）。.subscribe 是响应式"订阅"：
        // 只有订阅后流才开始执行，并提供三个回调：
        aiCodeGeneratorFacade.generateAndSaveCodeStream(userMessage, codeGenTypeEnum, appId)
                .subscribe(
                        // onNext：每收到一段就通过 SSE 推给前端。
                        // 注意：SSE 协议的 data 字段会吞掉内容首尾的空白字符（前导空格、换行），
                        // 直接发送纯文本会导致代码缩进丢失，因此把片段包装为 JSON（{"d": "..."}），
                        // 借助 JSON 字符串转义完整保留所有空白。
                        chunk -> {
                            try {
                                emitter.send(SseEmitter.event().data(Map.of("d", chunk), MediaType.APPLICATION_JSON));
                            } catch (IOException e) {
                                // 客户端断开等情况会导致发送失败，标记错误结束
                                emitter.completeWithError(e);
                            }
                        },
                        // onError：流发生异常时，推送 error 命名事件并标记错误结束
                        error -> {
                            log.error("流式生成失败", error);
                            try {
                                emitter.send(SseEmitter.event().name("error")
                                        .data(error.getMessage() == null ? "生成失败" : error.getMessage()));
                            } catch (IOException ignored) {
                            }
                            emitter.completeWithError(error);
                        },
                        // onComplete：流正常结束时，主动推送 done 命名事件通知前端生成完成，
                        // 携带 appId 方便前端提示"已加入我的应用"并跳转。
                        () -> {
                            try {
                                emitter.send(SseEmitter.event().name("done").data(Map.of("appId", appId)));
                            } catch (IOException ignored) {
                            }
                            emitter.complete();
                        }
                );

        return emitter;
    }

    // ==================== 生成历史 ====================

    /**
     * 获取所有生成记录列表（读取磁盘上 tmp/code_output 下的各目录）。
     *
     * @return 每条记录含 dirName（目录名）、codeGenType、prompt（截断）、createTime
     */
    @Operation(summary = "获取生成历史列表")
    @GetMapping("/list") // GET /api/code/list
    public BaseResponse<List<Map<String, Object>>> listGeneratedCode() {
        File rootFile = new File(FILE_SAVE_ROOT_DIR);
        if (!rootFile.exists()) {
            return ResultUtils.success(new ArrayList<>());
        }

        // listFiles(File::isDirectory)：只保留子目录（每个子目录 = 一次生成）
        File[] dirs = rootFile.listFiles(File::isDirectory);
        if (dirs == null || dirs.length == 0) {
            return ResultUtils.success(new ArrayList<>());
        }

        List<Map<String, Object>> list = new ArrayList<>();
        for (File dir : dirs) {
            Map<String, Object> item = new HashMap<>();
            item.put("dirName", dir.getName());

            // 从目录名前缀推断类型（目录命名规则由 saver 决定）
            if (dir.getName().startsWith("html_")) {
                item.put("codeGenType", "html");
            } else if (dir.getName().startsWith("multi_file_")) {
                item.put("codeGenType", "multi_file");
            } else if (dir.getName().startsWith("project_")) {
                item.put("codeGenType", "project");
            } else {
                item.put("codeGenType", "unknown");
            }

            // 读取该目录下的 prompt.txt，作为历史描述展示（过长截断到 100 字）
            File promptFile = new File(dir, "prompt.txt");
            if (promptFile.exists()) {
                String prompt = FileUtil.readUtf8String(promptFile);
                item.put("prompt", prompt.length() > 100 ? prompt.substring(0, 100) + "..." : prompt);
            } else {
                item.put("prompt", "");
            }

            // 用目录最后修改时间近似"创建时间"
            item.put("createTime", dir.lastModified());

            list.add(item);
        }

        // 按 createTime 倒序（负号实现降序），最新生成排前面
        list.sort(Comparator.comparingLong(m -> -((Long) m.get("createTime"))));
        return ResultUtils.success(list);
    }

    /**
     * 获取指定生成记录的代码内容（HTML/CSS/JS/提示词）。
     *
     * @param dir 目录名（URL 参数）
     * @return 包含各文件内容的 Map
     */
    @Operation(summary = "获取生成代码内容")
    @GetMapping("/content") // GET /api/code/content?dir=xxx
    public BaseResponse<Map<String, String>> getCodeContent(@RequestParam String dir) {
        // 安全校验（防目录穿越攻击）：限制 dir 只能由字母数字下划线组成，
        // 避免用户传 "../../etc/passwd" 之类路径读取系统文件
        if (!dir.matches("^[a-zA-Z0-9_]+$")) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "非法的目录名");
        }

        File dirFile = new File(FILE_SAVE_ROOT_DIR, dir);
        if (!dirFile.exists() || !dirFile.isDirectory()) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "目录不存在");
        }

        Map<String, String> content = new HashMap<>();

        // 工程化项目类型：递归读取整个目录树，以"相对路径 -> 内容"形式返回
        if (dir.startsWith("project_")) {
            collectProjectFiles(dirFile, "", content);
            content.put("codeGenType", "project");
            return ResultUtils.success(content);
        }

        // 三个文件分别读取（不存在则对应 key 不放入 Map，前端按 key 有无判断）
        File htmlFile = new File(dirFile, "index.html");
        if (htmlFile.exists()) {
            content.put("htmlCode", FileUtil.readUtf8String(htmlFile));
        }

        File cssFile = new File(dirFile, "style.css");
        if (cssFile.exists()) {
            content.put("cssCode", FileUtil.readUtf8String(cssFile));
        }

        File jsFile = new File(dirFile, "script.js");
        if (jsFile.exists()) {
            content.put("jsCode", FileUtil.readUtf8String(jsFile));
        }

        File promptFile = new File(dirFile, "prompt.txt");
        if (promptFile.exists()) {
            content.put("prompt", FileUtil.readUtf8String(promptFile));
        }

        // 推断类型（用于前端高亮等）
        if (dir.startsWith("html_")) {
            content.put("codeGenType", "html");
        } else if (dir.startsWith("multi_file_")) {
            content.put("codeGenType", "multi_file");
        }

        return ResultUtils.success(content);
    }

    /**
     * 递归收集工程化项目目录下的所有文件，以"相对路径 -> 内容"写入 content Map。
     * 跳过 node_modules、dist、.git 等生成/依赖目录。
     *
     * @param dir        当前目录
     * @param prefix     相对路径前缀（用于拼接子目录路径）
     * @param content    结果 Map
     */
    private void collectProjectFiles(File dir, String prefix, Map<String, String> content) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            String name = file.getName();
            String relativePath = prefix.isEmpty() ? name : prefix + "/" + name;
            if (file.isDirectory()) {
                if ("node_modules".equals(name) || "dist".equals(name) || ".git".equals(name) || "target".equals(name)) {
                    continue;
                }
                collectProjectFiles(file, relativePath, content);
            } else if (file.isFile()) {
                content.put(relativePath, FileUtil.readUtf8String(file));
            }
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 把用户提示词持久化到生成目录下的 prompt.txt，便于历史列表展示"这次生成了什么"。
     * 使用 try-catch 包裹，保存失败只告警不影响主流程（提示词非关键数据）。
     *
     * @param savedDir    已保存代码的目录
     * @param userMessage 用户输入的提示词
     */
    private void savePrompt(File savedDir, String userMessage) {
        try {
            FileUtil.writeUtf8String(userMessage, savedDir.getAbsolutePath() + File.separator + "prompt.txt");
        } catch (Exception e) {
            log.warn("保存提示词失败: {}", e.getMessage());
        }
    }

}
