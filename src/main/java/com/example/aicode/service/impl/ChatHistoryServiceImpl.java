package com.example.aicode.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.example.aicode.common.ErrorCode;
import com.example.aicode.common.ThrowUtils;
import com.example.aicode.mapper.ChatHistoryMapper;
import com.example.aicode.model.dto.chathistory.ChatHistoryQueryRequest;
import com.example.aicode.model.dto.vo.ChatHistoryVO;
import com.example.aicode.model.entity.ChatHistory;
import com.example.aicode.model.enums.MessageTypeEnum;
import com.example.aicode.service.ChatHistoryService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 对话历史业务服务实现类。
 * <p>
 * 实现 {@link ChatHistoryService} 中定义的对话历史相关业务逻辑，继承 MyBatis-Flex 提供的
 * {@link ServiceImpl}，可直接使用其内置的通用数据库操作方法（如 save、page、remove 等），
 * 底层通过注入的 {@link ChatHistoryMapper} 与数据库交互。
 *
 * @author 程序员yaj
 */
@Service
public class ChatHistoryServiceImpl extends ServiceImpl<ChatHistoryMapper, ChatHistory>
        implements ChatHistoryService {

    /**
     * 保存一条用户消息（messageType = "user"）。
     *
     * @param appId   所属应用 id
     * @param userId  创建用户 id
     * @param message 用户消息内容
     * @return 新消息记录的主键 id
     */
    @Override
    public Long saveUserMessage(Long appId, Long userId, String message) {
        return saveMessage(appId, userId, message, MessageTypeEnum.USER.getValue());
    }

    /**
     * 保存一条 AI 消息（messageType = "ai"），成功回复与错误信息均以此方式落库。
     *
     * @param appId   所属应用 id
     * @param userId  创建用户 id
     * @param message AI 回复内容（或错误信息）
     * @return 新消息记录的主键 id
     */
    @Override
    public Long saveAiMessage(Long appId, Long userId, String message) {
        return saveMessage(appId, userId, message, MessageTypeEnum.AI.getValue());
    }

    /**
     * 内部统一的保存消息方法。
     * <p>
     * 完成参数校验后构建实体并落库，消息类型由调用方指定（user / ai）。
     * 消息内容为空或 appId / userId 非法时抛出参数异常，落库失败时抛出操作异常。
     *
     * @param appId       所属应用 id
     * @param userId      创建用户 id
     * @param message     消息内容
     * @param messageType 消息类型（user / ai）
     * @return 新消息记录的主键 id
     */
    private Long saveMessage(Long appId, Long userId, String message, String messageType) {
        // 1. 参数校验：appId / userId 必须为正整数，消息内容不能为空
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 错误");
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.PARAMS_ERROR, "用户 ID 错误");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "消息内容不能为空");
        // 2. 构建实体
        ChatHistory chatHistory = ChatHistory.builder()
                .appId(appId)
                .userId(userId)
                .message(message)
                .messageType(messageType)
                .build();
        // 3. 落库（主键由雪花算法自动生成）
        boolean result = this.save(chatHistory);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "保存对话历史失败");
        // 4. 返回新生成的消息 id
        return chatHistory.getId();
    }

    /**
     * 逻辑删除指定应用下的全部对话历史，用于应用删除时的关联清理。
     *
     * @param appId 应用 id
     * @return 是否删除成功
     */
    @Override
    public boolean deleteByAppId(Long appId) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 错误");
        // MyBatis-Flex 会自动将逻辑删除字段（isDelete）参与的条件转换为软删除
        return this.remove(QueryWrapper.create().eq("appId", appId));
    }

    /**
     * 游标分页查询某应用下的对话历史（按 id 倒序，最新在前）。
     *
     * @param appId    应用 id
     * @param cursorId 游标 id（可为 null，表示加载最新一批）
     * @param pageSize 单次加载条数
     * @return 按 id 倒序排列的消息列表
     */
    @Override
    public List<ChatHistory> listLatestByAppId(Long appId, Long cursorId, int pageSize) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 错误");
        // 1. 限定应用，并依据游标加载更早的历史（id < cursorId）
        QueryWrapper queryWrapper = QueryWrapper.create().eq("appId", appId);
        if (cursorId != null && cursorId > 0) {
            queryWrapper.lt("id", cursorId);
        }
        // 2. 按 id 倒序（最新在前），限制单次加载条数
        queryWrapper.orderBy("id", false).limit(pageSize);
        return this.list(queryWrapper);
    }

    /**
     * 构造对话历史查询条件包装器（QueryWrapper）。
     * <p>
     * 支持按 appId、userId、messageType 精确匹配；默认按创建时间降序排序（便于内容监管），
     * 若传入排序字段则按指定字段排序。
     *
     * @param chatHistoryQueryRequest 查询请求
     * @return 查询条件包装器
     */
    @Override
    public QueryWrapper getQueryWrapper(ChatHistoryQueryRequest chatHistoryQueryRequest) {
        ThrowUtils.throwIf(chatHistoryQueryRequest == null, ErrorCode.PARAMS_ERROR, "请求参数为空");
        // 1. 提取查询条件字段
        Long appId = chatHistoryQueryRequest.getAppId();
        Long userId = chatHistoryQueryRequest.getUserId();
        String messageType = chatHistoryQueryRequest.getMessageType();
        String sortField = chatHistoryQueryRequest.getSortField();
        String sortOrder = chatHistoryQueryRequest.getSortOrder();
        // 2. 构建查询条件（空字段会被框架自动忽略）
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("appId", appId)
                .eq("userId", userId)
                .eq("messageType", messageType);
        // 3. 排序：默认按创建时间降序；传入排序字段则按指定字段排序
        if (StrUtil.isNotBlank(sortField)) {
            queryWrapper.orderBy(sortField, "ascend".equals(sortOrder));
        } else {
            queryWrapper.orderBy("createTime", false);
        }
        return queryWrapper;
    }

    /**
     * 获取对话历史封装视图对象（VO）。
     *
     * @param chatHistory 对话历史实体
     * @return 对话历史视图对象，入参为 null 时返回 null
     */
    @Override
    public ChatHistoryVO getChatHistoryVO(ChatHistory chatHistory) {
        if (chatHistory == null) {
            return null;
        }
        ChatHistoryVO chatHistoryVO = new ChatHistoryVO();
        // 将实体同名字段批量拷贝到 VO（Hutool BeanUtil）
        BeanUtil.copyProperties(chatHistory, chatHistoryVO);
        return chatHistoryVO;
    }

    /**
     * 批量获取对话历史封装视图对象列表（VO）。
     *
     * @param chatHistoryList 对话历史实体列表
     * @return 对话历史视图对象列表（入参为空时返回空列表）
     */
    @Override
    public List<ChatHistoryVO> getChatHistoryVOList(List<ChatHistory> chatHistoryList) {
        if (CollUtil.isEmpty(chatHistoryList)) {
            return new ArrayList<>();
        }
        // 逐条转换为 VO
        return chatHistoryList.stream().map(this::getChatHistoryVO).collect(Collectors.toList());
    }

    /**
     * 将对话历史实体分页对象转换为视图对象分页。
     *
     * @param chatHistoryPage 对话历史实体分页对象
     * @return 对话历史视图对象分页
     */
    @Override
    public Page<ChatHistoryVO> getChatHistoryVOPage(Page<ChatHistory> chatHistoryPage) {
        // 保留分页元数据（页码/每页大小/总条数）
        Page<ChatHistoryVO> chatHistoryVOPage = new Page<>(
                chatHistoryPage.getPageNumber(),
                chatHistoryPage.getPageSize(),
                chatHistoryPage.getTotalRow());
        // 批量转换记录为 VO
        chatHistoryVOPage.setRecords(getChatHistoryVOList(chatHistoryPage.getRecords()));
        return chatHistoryVOPage;
    }
}
