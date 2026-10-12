package com.example.aicode.controller;

import com.example.aicode.annotation.AuthCheck;
import com.example.aicode.common.BaseResponse;
import com.example.aicode.common.ErrorCode;
import com.example.aicode.common.ResultUtils;
import com.example.aicode.common.ThrowUtils;
import com.example.aicode.constant.ChatHistoryConstant;
import com.example.aicode.constant.UserConstant;
import com.example.aicode.model.dto.chathistory.ChatHistoryQueryRequest;
import com.example.aicode.model.dto.vo.ChatHistoryVO;
import com.example.aicode.model.entity.App;
import com.example.aicode.model.entity.ChatHistory;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 对话历史接口控制器。
 * <p>
 * 对外提供对话历史的查询能力：
 * <ul>
 *     <li>游标分页查看某个应用的对话历史（仅应用创建者和管理员可见）；</li>
 *     <li>管理员分页查看所有应用的对话历史（按时间降序，便于内容监管）。</li>
 * </ul>
 * 对话历史的"持久化写入"由对话生成流程（{@code AppController#chatToGenCode}）在内部调用
 * {@link ChatHistoryService} 完成，不在此暴露写接口。
 *
 * @author 程序员yaj
 */
@Slf4j
@Tag(name = "对话历史接口")
@RestController
@RequestMapping("/chat_history")
public class ChatHistoryController {

    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private UserService userService;

    @Resource
    private AppService appService;

    /**
     * 查询某个应用的对话历史（游标分页，最新在前，支持向前加载更多历史记录）。
     * <p>
     * 类似聊天软件的消息加载机制：进入应用页面时，前端根据 appId 先加载最新一批消息；
     * 若已有历史对话则直接展示，否则才触发初始化提示词，从而避免浏览他人应用时意外触发对话。
     * 仅应用创建者和管理员可见。
     * </p>
     *
     * @param appId    应用 id
     * @param cursorId 游标 id（已加载最早一条消息的 id），首次加载可不传
     * @param pageSize 单次加载条数，默认 {@link ChatHistoryConstant#DEFAULT_PAGE_SIZE}，上限 {@link ChatHistoryConstant#MAX_PAGE_SIZE}
     * @param request  当前 HTTP 请求（用于获取登录用户）
     * @return 按 id 倒序排列的消息列表
     */
    @GetMapping("/list")
    @Operation(summary = "查询某个应用的对话历史")
    public BaseResponse<List<ChatHistoryVO>> listChatHistoryByAppId(
            @RequestParam("appId") Long appId,
            @RequestParam(value = "cursorId", required = false) Long cursorId,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            HttpServletRequest request) {
        // 1. 参数校验：appId 必须合法；pageSize 做上下限保护
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 错误");
        if (pageSize <= 0 || pageSize > ChatHistoryConstant.MAX_PAGE_SIZE) {
            pageSize = ChatHistoryConstant.DEFAULT_PAGE_SIZE;
        }
        // 2. 获取登录用户并校验应用存在
        User loginUser = userService.getLoginUser(request);
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        // 3. 权限校验：仅应用创建者和管理员可见
        ThrowUtils.throwIf(!app.getUserId().equals(loginUser.getId())
                        && !UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole()),
                ErrorCode.NO_AUTH_ERROR, "无权限查看该应用的对话历史");
        // 4. 游标分页查询并转换为 VO
        List<ChatHistory> chatHistoryList = chatHistoryService.listLatestByAppId(appId, cursorId, pageSize);
        return ResultUtils.success(chatHistoryService.getChatHistoryVOList(chatHistoryList));
    }

    /**
     * 管理员分页查询所有应用的对话历史，按时间降序排序，便于内容监管。
     *
     * @param chatHistoryQueryRequest 查询请求（分页参数 + 可选筛选条件 appId / userId / messageType）
     * @return 按时间降序的对话历史分页视图对象
     */
    @PostMapping("/admin/list/page")
    @Operation(summary = "管理员分页查询对话历史")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<ChatHistoryVO>> listChatHistoryByPage(
            @RequestBody ChatHistoryQueryRequest chatHistoryQueryRequest) {
        // 1. 提取分页参数
        long pageNum = chatHistoryQueryRequest.getPageNum();
        long pageSize = chatHistoryQueryRequest.getPageSize();
        // 2. 构建查询条件（默认按时间降序）
        QueryWrapper queryWrapper = chatHistoryService.getQueryWrapper(chatHistoryQueryRequest);
        // 3. 分页查询并转换为 VO
        Page<ChatHistory> chatHistoryPage = chatHistoryService.page(Page.of(pageNum, pageSize), queryWrapper);
        return ResultUtils.success(chatHistoryService.getChatHistoryVOPage(chatHistoryPage));
    }
}
