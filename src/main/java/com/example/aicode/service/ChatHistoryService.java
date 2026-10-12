package com.example.aicode.service;

import com.example.aicode.model.dto.chathistory.ChatHistoryQueryRequest;
import com.example.aicode.model.dto.vo.ChatHistoryVO;
import com.example.aicode.model.entity.ChatHistory;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;

import java.util.List;

/**
 * 对话历史业务服务接口。
 * <p>
 * 定义对话历史（ChatHistory）相关的业务操作规范，继承 MyBatis-Flex 提供的 {@link IService}，
 * 可直接使用其内置的通用 CRUD 方法（如 getById、save、page、remove 等）。
 * 具体的业务逻辑由实现类 {@code ChatHistoryServiceImpl} 完成。
 *
 * @author 程序员yaj
 */
public interface ChatHistoryService extends IService<ChatHistory> {

    /**
     * 保存一条用户消息。
     *
     * <p>用户发送消息时调用，将消息以 messageType = "user" 持久化到对话历史，
     * 保证对话的完整性。</p>
     *
     * @param appId   所属应用 id
     * @param userId  创建用户 id
     * @param message 用户消息内容
     * @return 新消息记录的主键 id
     */
    Long saveUserMessage(Long appId, Long userId, String message);

    /**
     * 保存一条 AI 消息。
     *
     * <p>AI 成功回复后，或 AI 回复失败需要记录错误信息时调用，
     * 将内容以 messageType = "ai" 持久化到对话历史（错误信息同样以 AI 消息落库）。</p>
     *
     * @param appId   所属应用 id
     * @param userId  创建用户 id
     * @param message AI 回复内容（或错误信息）
     * @return 新消息记录的主键 id
     */
    Long saveAiMessage(Long appId, Long userId, String message);

    /**
     * 删除（逻辑删除）指定应用下的全部对话历史。
     *
     * <p>应用被删除时调用，用于应用级数据隔离：将属于该应用的对话历史一并逻辑删除，
     * 避免出现无归属的冗余历史数据。</p>
     *
     * @param appId 应用 id
     * @return 是否删除成功
     */
    boolean deleteByAppId(Long appId);

    /**
     * 游标分页查询某应用下的对话历史（按 id 倒序，最新在前）。
     *
     * <p>实现类似聊天软件的"消息加载机制"：
     * <ul>
     *     <li>首次加载：cursorId 传 null，返回最新 {@code pageSize} 条消息；</li>
     *     <li>向前加载：cursorId 传当前已加载最早一条消息的 id，返回更早的 {@code pageSize} 条消息。</li>
     * </ul>
     * 通过 {@code id < cursorId} 的范围条件 + 联合索引（appId, createTime / appId, id）保证查询性能。</p>
     *
     * @param appId    应用 id
     * @param cursorId 游标 id（当前已加载最早一条消息的 id），可为 null
     * @param pageSize 单次加载条数
     * @return 按 id 倒序排列的消息列表
     */
    List<ChatHistory> listLatestByAppId(Long appId, Long cursorId, int pageSize);

    /**
     * 构造对话历史查询条件包装器（QueryWrapper）。
     *
     * <p>将 {@link ChatHistoryQueryRequest} 中的查询条件转换为 MyBatis-Flex 的查询包装器，
     * 支持按 appId、userId、messageType 精确匹配；默认按创建时间降序排序，便于内容监管。</p>
     *
     * @param chatHistoryQueryRequest 查询请求
     * @return 查询条件包装器
     */
    QueryWrapper getQueryWrapper(ChatHistoryQueryRequest chatHistoryQueryRequest);

    /**
     * 获取对话历史封装视图对象（VO）。
     *
     * @param chatHistory 对话历史实体
     * @return 对话历史视图对象，入参为 null 时返回 null
     */
    ChatHistoryVO getChatHistoryVO(ChatHistory chatHistory);

    /**
     * 批量获取对话历史封装视图对象列表（VO）。
     *
     * @param chatHistoryList 对话历史实体列表
     * @return 对话历史视图对象列表（入参为空时返回空列表）
     */
    List<ChatHistoryVO> getChatHistoryVOList(List<ChatHistory> chatHistoryList);

    /**
     * 将对话历史实体分页对象转换为视图对象分页。
     *
     * @param chatHistoryPage 对话历史实体分页对象
     * @return 对话历史视图对象分页
     */
    Page<ChatHistoryVO> getChatHistoryVOPage(Page<ChatHistory> chatHistoryPage);
}
