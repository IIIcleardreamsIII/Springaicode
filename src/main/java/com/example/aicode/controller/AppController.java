package com.example.aicode.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.example.aicode.annotation.AuthCheck;
import com.example.aicode.ai.model.enums.CodeGenTypeEnum;
import com.example.aicode.common.*;
import com.example.aicode.constant.AppConstant;
import com.example.aicode.constant.UserConstant;
import com.example.aicode.core.AiCodeGeneratorFacade;
import com.example.aicode.model.dto.app.AppAddRequest;
import com.example.aicode.model.dto.app.AppDeployRequest;
import com.example.aicode.model.dto.app.AppSuggestTypeRequest;
import com.example.aicode.model.dto.app.AppEditRequest;
import com.example.aicode.model.dto.app.AppQueryRequest;
import com.example.aicode.model.dto.app.AppUpdateRequest;
import com.example.aicode.model.dto.user.DeleteRequest;
import com.example.aicode.model.dto.vo.AppVO;
import com.example.aicode.model.entity.App;
import com.example.aicode.model.entity.User;
import com.example.aicode.service.AppService;
import com.example.aicode.service.ChatHistoryService;
import com.example.aicode.service.UserService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 应用控制器（HTTP 接口层）。
 * <p>
 * 提供应用（App）的增删改查接口，面向两类调用方：
 * <ul>
 *   <li>普通用户：创建应用、编辑/删除/查看/分页查询自己的应用、查询精选应用列表；</li>
 *   <li>管理员（路径统一带 {@code /admin} 前缀，并通过 {@link AuthCheck} 注解校验角色）：
 *       删除/更新任意应用、分页查询全量应用、查看任意应用详情。</li>
 * </ul>
 * 类级路由前缀为 {@code /app}，与全局 context-path（/api）拼接后为 {@code /api/app/**}。
 *
 * @author 程序员yaj
 */
@Slf4j
@Tag(name = "应用管理", description = "应用创建、编辑、删除、查询、对话生成等接口")
@RestController
@RequestMapping("/app")
public class AppController {

    /**
     * 局部异常处理器：按请求类型区分 SSE 端点与普通（JSON/文件下载）接口的异常处理。
     * <p>
     * 问题背景：本类同时包含 SSE 流式端点（{@link #chatToGenCode}）与普通接口（含下载接口
     * {@link #downloadAppCode}）。SSE 端点抛出异常时若交给全局 {@link GlobalExceptionHandler}，
     * 它返回的 {@code Result} JSON 无法用 text/event-stream 序列化，会触发
     * {@code HttpMessageNotWritableException} 反复刷屏，因此需要在就近拦截。
     * 但若对所有异常都返回 null（void），又会把下载接口的异常吞成 HTTP 200 空响应，
     * 导致前端拿到 0 字节文件。故这里按请求类型分流：
     * <ul>
     *   <li>SSE 请求（EventSource 的 Accept 头固定为 text/event-stream）：返回 null 吞掉异常，
     *       避免序列化刷屏；前端通过 EventSource 连接异常感知；</li>
     *   <li>非 SSE 请求（普通 JSON 接口、文件下载接口等）：返回标准 {@link Result} 错误响应，
     *       与全局处理器保持一致，让前端 request.js 拦截器能正确提示。</li>
     * </ul>
     */
    @ExceptionHandler(Exception.class)
    public Object handleException(Exception e, HttpServletRequest request) {
        // 依据 Accept 头区分 SSE 请求（EventSource 强制 Accept: text/event-stream）
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        boolean isSseRequest = accept != null && accept.contains(MediaType.TEXT_EVENT_STREAM_VALUE);
        if (isSseRequest) {
            // SSE 端点异常已通过 error 事件通知前端，这里仅记录并忽略，避免全局处理器二次处理刷屏
            log.warn("SSE 端点异常已被局部处理器接收（已通过 SSE 上报前端）：{}", e.getMessage());
            return null;
        }
        // 非 SSE 请求：返回标准错误响应，避免下载接口被吞成 200 空响应（0 字节文件）
        if (e instanceof BusinessException) {
            BusinessException businessException = (BusinessException) e;
            return Result.error(businessException.getCode(), businessException.getMessage());
        }
        return Result.error(500, "系统错误：" + e.getMessage());
    }

    /** 应用服务，处理应用相关业务逻辑（Spring 注入） */
    @Resource
    private AppService appService;

    /** 用户服务，用于获取当前登录用户（Spring 注入） */
    @Resource
    private UserService userService;

    /** AI 代码生成门面，编排"AI 生成 → 解析 → 落盘"流程（Spring 注入） */
    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;

    /** 对话历史服务，用于持久化对话消息及关联删除（Spring 注入） */
    @Resource
    private ChatHistoryService chatHistoryService;

    // ==================== 用户接口 ====================

    /**
     * 创建应用（用户接口）。
     *
     * <p>要求填写初始化提示词（initPrompt），可选择代码生成类型（codeGenType：
     * html / multi_file / project，不传默认 html）。应用名称自动取提示词前 12 个字符，
     * 应用归属于当前登录用户。</p>
     *
     * @param appAddRequest 创建应用请求（含 initPrompt、可选 codeGenType）
     * @param request       HTTP 请求（用于获取登录态）
     * @return 新应用 id
     */
    @Operation(summary = "创建应用")
    @PostMapping("/add")
    public BaseResponse<Long> addApp(@RequestBody AppAddRequest appAddRequest, HttpServletRequest request) {
        // 1. 参数校验：请求体与 initPrompt 均不能为空
        ThrowUtils.throwIf(appAddRequest == null, ErrorCode.PARAMS_ERROR);
        String initPrompt = appAddRequest.getInitPrompt();
        ThrowUtils.throwIf(StrUtil.isBlank(initPrompt), ErrorCode.PARAMS_ERROR, "初始化 prompt 不能为空");
        // 2. 获取当前登录用户（未登录会抛出 NOT_LOGIN_ERROR）
        User loginUser = userService.getLoginUser(request);
        // 3. 解析代码生成类型：传入合法类型则使用，否则（空/非法）默认 HTML
        String codeGenType = appAddRequest.getCodeGenType();
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
        if (codeGenTypeEnum == null) {
            codeGenTypeEnum = CodeGenTypeEnum.HTML;
        }
        // 4. 组装应用实体：名称取提示词前 12 字符，生成类型按请求解析，归属当前用户
        App app = new App();
        BeanUtil.copyProperties(appAddRequest, app);
        app.setAppName(initPrompt.substring(0, Math.min(initPrompt.length(), 12)));
        app.setCodeGenType(codeGenTypeEnum.getValue());
        app.setUserId(loginUser.getId());
        // 5. 写入数据库
        boolean result = appService.save(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(app.getId());
    }

    /**
     * AI 智能选择代码生成方案（用户接口）。
     *
     * <p>根据用户对应用/页面的自然语言描述，由 AI 自动判断应该使用
     * html / multi_file / project 中的哪一种代码生成方案，并返回建议的类型标识。
     * 前端拿到结果后可直接填充到创建应用的 codeGenType 字段。</p>
     *
     * @param appSuggestTypeRequest 请求体（含 prompt 应用描述）
     * @param request               HTTP 请求（用于获取登录态）
     * @return 建议的代码生成类型标识：html / multi_file / project
     */
    @Operation(summary = "AI 智能选择代码生成方案")
    @PostMapping("/suggest/type")
    public BaseResponse<String> suggestCodeGenType(@RequestBody AppSuggestTypeRequest appSuggestTypeRequest, HttpServletRequest request) {
        // 1. 参数校验：请求体与 prompt 均不能为空
        ThrowUtils.throwIf(appSuggestTypeRequest == null, ErrorCode.PARAMS_ERROR);
        String prompt = appSuggestTypeRequest.getPrompt();
        ThrowUtils.throwIf(StrUtil.isBlank(prompt), ErrorCode.PARAMS_ERROR, "应用描述不能为空");
        // 2. 获取当前登录用户（未登录会抛出 NOT_LOGIN_ERROR）
        userService.getLoginUser(request);
        // 3. 调用 AI 智能路由，返回建议的代码生成类型
        CodeGenTypeEnum typeEnum = aiCodeGeneratorFacade.suggestCodeGenType(prompt.trim());
        return ResultUtils.success(typeEnum.getValue());
    }

    /**
     * 编辑自己的应用（用户接口，目前仅支持修改应用名称）。
     *
     * <p>仅允许编辑本人创建的应用，越权操作会抛出 NO_AUTH_ERROR。</p>
     *
     * @param appEditRequest 编辑请求（含 id 和 appName）
     * @param request        HTTP 请求（用于获取登录态）
     * @return 是否编辑成功
     */
    @Operation(summary = "编辑应用（仅本人）")
    @PostMapping("/edit")
    public BaseResponse<Boolean> editApp(@RequestBody AppEditRequest appEditRequest, HttpServletRequest request) {
        // 1. 参数校验
        if (appEditRequest == null || appEditRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 2. 获取当前登录用户
        User loginUser = userService.getLoginUser(request);
        // 3. 判断应用是否存在，只有创建者本人可以编辑
        long id = appEditRequest.getId();
        App oldApp = appService.getById(id);
        ThrowUtils.throwIf(oldApp == null, ErrorCode.NOT_FOUND_ERROR);
        if (!oldApp.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 4. 构造更新对象（仅更新名称与编辑时间，其余字段保持原值）
        App app = new App();
        app.setId(id);
        app.setAppName(appEditRequest.getAppName());
        app.setEditTime(LocalDateTime.now());
        // 5. 执行更新（MyBatis-Flex 默认忽略 null 字段，未设置的字段不会被覆盖）
        boolean result = appService.updateById(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 删除自己的应用（用户接口）。
     *
     * <p>仅允许删除本人创建的应用（管理员也可通过本接口删除）。
     * 删除为逻辑删除（isDelete 置 1），数据不会物理消失。</p>
     *
     * @param deleteRequest 删除请求（含 id）
     * @param request       HTTP 请求（用于获取登录态）
     * @return 是否删除成功
     */
    @Operation(summary = "删除应用（仅本人）")
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteApp(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        // 1. 参数校验
        if (deleteRequest == null || deleteRequest.getId() == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 2. 获取当前登录用户
        User loginUser = userService.getLoginUser(request);
        // 3. 判断应用是否存在，仅本人或管理员可以删除
        long id = deleteRequest.getId();
        App oldApp = appService.getById(id);
        ThrowUtils.throwIf(oldApp == null, ErrorCode.NOT_FOUND_ERROR);
        if (!oldApp.getUserId().equals(loginUser.getId()) && !UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 4. 执行逻辑删除
        boolean result = appService.removeById(id);
        // 5. 关联删除该应用的全部对话历史，避免数据冗余
        chatHistoryService.deleteByAppId(id);
        return ResultUtils.success(result);
    }

    /**
     * 根据 id 查看应用详情（用户接口）。
     *
     * <p>返回脱敏后的 {@link AppVO}（含创建者信息），任何登录用户均可查看。</p>
     *
     * @param id 应用 id
     * @return 应用视图对象
     */
    @Operation(summary = "根据 id 获取应用详情（VO）")
    @GetMapping("/get/vo")
    public BaseResponse<AppVO> getAppVOById(long id) {
        // 1. 参数校验
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 2. 查询应用详情（含关联的创建者信息），不存在则报错
        AppVO appVO = appService.getAppVOById(id);
        ThrowUtils.throwIf(appVO == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(appVO);
    }

    /**
     * 分页查询自己的应用列表（用户接口）。
     *
     * <p>仅返回当前登录用户创建的应用，支持按名称模糊查询，每页最多 20 条。</p>
     *
     * @param appQueryRequest 查询/分页请求
     * @param request         HTTP 请求（用于获取登录态）
     * @return 脱敏后的应用分页数据
     */
    @Operation(summary = "分页查询我的应用列表")
    @PostMapping("/my/list/page/vo")
    public BaseResponse<Page<AppVO>> listMyAppVOByPage(@RequestBody AppQueryRequest appQueryRequest, HttpServletRequest request) {
        // 1. 参数校验：每页最多 20 条
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long pageSize = appQueryRequest.getPageSize();
        ThrowUtils.throwIf(pageSize > 20, ErrorCode.PARAMS_ERROR, "每页最多查询 20 个应用");
        // 2. 获取当前登录用户，并强制限定只查自己的应用（防止越权传 userId）
        User loginUser = userService.getLoginUser(request);
        appQueryRequest.setUserId(loginUser.getId());
        // 3. 分页查询
        long pageNum = appQueryRequest.getPageNum();
        QueryWrapper queryWrapper = appService.getQueryWrapper(appQueryRequest);
        Page<App> appPage = appService.page(Page.of(pageNum, pageSize), queryWrapper);
        // 4. 转换为 VO 分页对象返回（含每条记录的创建者信息）
        return ResultUtils.success(appService.getAppVOPage(appPage));
    }

    /**
     * 分页查询精选应用列表（用户接口）。
     *
     * <p>精选应用即优先级被管理员设置为 99 的应用，支持按名称模糊查询，每页最多 20 条。</p>
     *
     * @param appQueryRequest 查询/分页请求
     * @return 脱敏后的精选应用分页数据
     */
    @Operation(summary = "分页查询精选应用列表")
    @PostMapping("/good/list/page/vo")
    public BaseResponse<Page<AppVO>> listGoodAppVOByPage(@RequestBody AppQueryRequest appQueryRequest) {
        // 1. 参数校验：每页最多 20 条
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long pageSize = appQueryRequest.getPageSize();
        ThrowUtils.throwIf(pageSize > 20, ErrorCode.PARAMS_ERROR, "每页最多查询 20 个应用");
        // 2. 强制限定查询条件为精选应用（优先级 = 99），防止调用方篡改 priority 查询全部应用
        appQueryRequest.setPriority(AppConstant.GOOD_APP_PRIORITY);
        // 3. 分页查询并转换为 VO 返回（含每条记录的创建者信息）
        long pageNum = appQueryRequest.getPageNum();
        QueryWrapper queryWrapper = appService.getQueryWrapper(appQueryRequest);
        Page<App> appPage = appService.page(Page.of(pageNum, pageSize), queryWrapper);
        return ResultUtils.success(appService.getAppVOPage(appPage));
    }

    // ==================== 应用对话生成 ====================

    /**
     * 应用对话生成代码（SSE 流式接口）。
     *
     * <p>业务流程：用户在主页输入提示词创建应用（入库）→ 拿到应用 id 跳转对话页 →
     * 对话页调用本接口，后端使用应用的初始化提示词与 AI 对话并流式生成网站代码。
     * 前端无需关心是否为首次对话，始终调用本接口即可，由后端决定生成策略。</p>
     *
     * <p>生成完成后，代码会落盘到 {@code codeGenType_appId} 命名的目录（如 html_123），
     * 从而与应用记录一一关联——后续通过 appId 查库拿到应用信息，
     * 即可按同一规则拼出目录路径找到网站文件。</p>
     *
     * <p>SSE 推送协议约定：
     * <ul>
     *   <li>普通消息（onmessage）：data 为 JSON 字符串 {@code {"d": "代码片段"}}，
     *       包装为 JSON 是为了保留片段首尾的空白字符（SSE 协议会吞掉 data 首尾的空白，
     *       导致代码缩进/空格丢失）；</li>
     *   <li>完成通知：名为 {@code done} 的命名事件，前端通过
     *       {@code addEventListener("done", ...)} 监听，收到后可关闭连接；</li>
     *   <li>错误通知：名为 {@code error} 的命名事件，data 为错误信息文本。</li>
     * </ul></p>
     *
     * @param appId       应用 id（URL 查询参数）
     * @param userMessage 用户本轮输入的消息（可选）；为空表示首次对话，此时使用应用的 initPrompt
     * @param request     HTTP 请求（用于获取登录态并校验应用归属）
     * @return SseEmitter 流式响应对象
     */
    @Operation(summary = "应用对话生成代码（SSE）")
    @GetMapping(value = "/chat/gen/code", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatToGenCode(@RequestParam Long appId,
                                    @RequestParam(value = "userMessage", required = false) String userMessage,
                                    HttpServletRequest request) {
        // 1. 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 id 无效");
        // 2. 查询应用信息
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        // 3. 权限校验：仅应用创建者本人可以与该应用对话生成代码
        User loginUser = userService.getLoginUser(request);
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问该应用");
        }
        // 4. 取应用的初始化提示词与代码生成类型；本轮实际使用的用户消息：
        //    首次对话（未传 userMessage）使用 initPrompt，后续对话使用用户新输入的消息。
        //    配合 langchain4j 的对话记忆（按 appId 隔离），即可实现"带上下文的连续对话生成"。
        String initPrompt = app.getInitPrompt();
        String effectivePrompt = StrUtil.isBlank(userMessage) ? initPrompt : userMessage;
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(app.getCodeGenType());
        ThrowUtils.throwIf(codeGenTypeEnum == null, ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型");
        // 5. 创建 SSE 推送对象（5 分钟超时，AI 生成较慢需放宽）
        SseEmitter emitter = new SseEmitter(300000L);
        // 6. 保存用户消息（本轮实际发送的消息），确保对话完整性
        chatHistoryService.saveUserMessage(appId, loginUser.getId(), effectivePrompt);
        // 7. 调用门面流式生成并落盘（目录名 codeGenType_appId，与应用关联），订阅流转推给前端
        Flux<String> contentFlux = aiCodeGeneratorFacade.generateAndSaveCodeStream(effectivePrompt, codeGenTypeEnum, appId);
        // 用于累积 AI 流式返回的完整内容，生成结束后统一保存为 AI 消息
        StringBuilder aiResponseBuilder = new StringBuilder();
        contentFlux.subscribe(
                // onNext：每收到一段代码片段就通过 SSE 推给前端。
                // 注意：SSE 协议的 data 字段会吞掉内容首尾的空白字符（前导空格、换行），
                // 直接发送纯文本会导致代码缩进丢失，因此把片段包装为 JSON（{"d": "..."}），
                // 借助 JSON 字符串转义完整保留所有空白；Spring 会用 Jackson 自动序列化该 Map。
                chunk -> {
                    // 累积 AI 返回内容，用于生成结束后持久化为 AI 消息
                    aiResponseBuilder.append(chunk);
                    try {
                        emitter.send(SseEmitter.event().data(Map.of("d", chunk), MediaType.APPLICATION_JSON));
                    } catch (IOException e) {
                        emitter.completeWithError(e);
                    }
                },
                // onError：流异常时推送 error 命名事件（前端可监听并展示错误信息），
                // 同时记录错误信息，确保对话完整性（错误信息以 AI 消息落库）
                error -> {
                    String errorMessage = error.getMessage() == null ? "生成失败" : error.getMessage();
                    try {
                        chatHistoryService.saveAiMessage(appId, loginUser.getId(), errorMessage);
                    } catch (Exception ex) {
                        log.error("保存 AI 错误消息失败，appId={}", appId, ex);
                    }
                    log.error("应用 {} 对话生成代码失败", appId, error);
                    try {
                        emitter.send(SseEmitter.event().name("error").data(errorMessage));
                    } catch (IOException ignored) {
                    }
                    // 关键：错误已通过上面的命名 error 事件推送给前端，这里用 complete() 正常结束响应，
                    // 避免 completeWithError 把异常抛给 Servlet 容器，从而触发全局异常处理器的
                    // HttpMessageNotWritableException（text/event-stream 无法序列化 Result）刷屏。
                    emitter.complete();
                },
                // onComplete：流正常结束时，主动推送 done 命名事件通知前端生成完成，
                // 并保存 AI 完整回复内容。
                // 相比 [DONE] 文本哨兵，命名事件由协议层区分，不会被 AI 生成内容中的
                // 相同字符串干扰；前端通过 addEventListener("done", ...) 精确感知。
                () -> {
                    try {
                        chatHistoryService.saveAiMessage(appId, loginUser.getId(), aiResponseBuilder.toString());
                    } catch (Exception ex) {
                        log.error("保存 AI 回复消息失败，appId={}", appId, ex);
                    }
                    try {
                        emitter.send(SseEmitter.event().name("done").data(""));
                    } catch (IOException ignored) {
                    }
                    emitter.complete();
                }
        );
        return emitter;
    }

    // ==================== 应用部署 ====================

    /**
     * 部署应用（用户接口）。
     *
     * <p>将应用生成的代码从临时输出目录复制到部署目录，发布为可在线访问的网站。
     * 仅应用创建者本人可部署；重复部署是幂等的——复用同一 deployKey，
     * 用最新代码覆盖部署目录，并刷新部署时间。</p>
     *
     * @param appDeployRequest 部署请求（含 appId）
     * @param request          HTTP 请求（用于获取登录态）
     * @return 部署后的可访问 URL，格式为 {@code {部署域名}/{deployKey}}
     */
    @Operation(summary = "部署应用")
    @PostMapping("/deploy")
    public BaseResponse<String> deployApp(@RequestBody AppDeployRequest appDeployRequest, HttpServletRequest request) {
        // 1. 参数校验
        ThrowUtils.throwIf(appDeployRequest == null, ErrorCode.PARAMS_ERROR);
        Long appId = appDeployRequest.getAppId();
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 错误");
        // 2. 获取当前登录用户（未登录会抛出 NOT_LOGIN_ERROR）
        User loginUser = userService.getLoginUser(request);
        // 3. 调用服务完成部署（归属校验、文件复制、更新数据库均在服务层完成）
        String deployUrl = appService.deployApp(appDeployRequest, loginUser);
        return ResultUtils.success(deployUrl);
    }

    /**
     * 下载应用源码（用户接口）。
     *
     * <p>将应用源码打包为 zip 供用户下载进行二次开发。仅应用创建者本人可下载，
     * 且必须先部署（已生成访问地址）才能下载；每次下载成功会累加下载次数。</p>
     *
     * @param appId   应用 id（URL 路径参数）
     * @param request HTTP 请求（用于获取登录态）
     * @return zip 文件流（Content-Disposition 为 attachment，浏览器触发下载）
     */
    @Operation(summary = "下载应用源码")
    @GetMapping("/download/{appId}")
    public ResponseEntity<org.springframework.core.io.Resource> downloadAppCode(@PathVariable Long appId, HttpServletRequest request) {
        // 1. 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 id 无效");
        // 2. 获取当前登录用户（未登录会抛出 NOT_LOGIN_ERROR）
        User loginUser = userService.getLoginUser(request);
        // 3. 调用服务完成打包（归属校验、部署校验、打包、计数均在服务层完成）
        File zipFile = appService.downloadAppCode(appId, loginUser);
        // 4. 组装下载响应：Content-Disposition attachment 触发浏览器下载，
        //    文件名用 UTF-8 编码（filename*）避免中文乱码
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(zipFile.getName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body((org.springframework.core.io.Resource) new FileSystemResource(zipFile));
    }

    // ==================== 管理员接口 ====================

    /**
     * 更新任意应用（管理员接口）。
     *
     * <p>支持更新应用名称、应用封面、优先级（如将 priority 置为 99 即设为精选）。</p>
     *
     * @param appUpdateRequest 更新请求
     * @return 是否更新成功
     */
    @Operation(summary = "更新应用（管理员）")
    @PostMapping("/admin/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateApp(@RequestBody AppUpdateRequest appUpdateRequest) {
        // 1. 参数校验
        if (appUpdateRequest == null || appUpdateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 2. 判断应用是否存在
        long id = appUpdateRequest.getId();
        App oldApp = appService.getById(id);
        ThrowUtils.throwIf(oldApp == null, ErrorCode.NOT_FOUND_ERROR);
        // 3. 拷贝请求字段并刷新编辑时间
        App app = new App();
        BeanUtil.copyProperties(appUpdateRequest, app);
        app.setEditTime(LocalDateTime.now());
        // 4. 执行更新
        boolean result = appService.updateById(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 删除任意应用（管理员接口）。
     *
     * @param deleteRequest 删除请求（含 id）
     * @return 是否删除成功
     */
    @Operation(summary = "删除应用（管理员）")
    @PostMapping("/admin/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteAppByAdmin(@RequestBody DeleteRequest deleteRequest) {
        // 1. 参数校验
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() == null || deleteRequest.getId() <= 0, ErrorCode.PARAMS_ERROR);
        // 2. 判断应用是否存在
        long id = deleteRequest.getId();
        App oldApp = appService.getById(id);
        ThrowUtils.throwIf(oldApp == null, ErrorCode.NOT_FOUND_ERROR);
        // 3. 执行逻辑删除
        boolean result = appService.removeById(id);
        // 4. 关联删除该应用的全部对话历史，避免数据冗余
        chatHistoryService.deleteByAppId(id);
        return ResultUtils.success(result);
    }

    /**
     * 分页查询应用列表（管理员接口）。
     *
     * <p>支持根据除时间外的任何字段查询，每页数量不限；返回完整实体（未脱敏）。</p>
     *
     * @param appQueryRequest 查询/分页请求
     * @return 应用分页数据
     */
    @Operation(summary = "分页查询应用列表（管理员）")
    @PostMapping("/admin/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<App>> listAppByPage(@RequestBody AppQueryRequest appQueryRequest) {
        // 1. 参数校验
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);
        // 2. 分页查询（管理员接口不限制每页数量）
        long pageNum = appQueryRequest.getPageNum();
        long pageSize = appQueryRequest.getPageSize();
        QueryWrapper queryWrapper = appService.getQueryWrapper(appQueryRequest);
        Page<App> appPage = appService.page(Page.of(pageNum, pageSize), queryWrapper);
        return ResultUtils.success(appPage);
    }

    /**
     * 根据 id 查看应用详情（管理员接口）。
     *
     * <p>返回完整实体（未脱敏），便于管理端查看全部字段。</p>
     *
     * @param id 应用 id
     * @return 应用实体
     */
    @Operation(summary = "根据 id 获取应用详情（管理员）")
    @GetMapping("/admin/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<App> getAppById(long id) {
        // 1. 参数校验
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 2. 查询数据库
        App app = appService.getById(id);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(app);
    }

}
