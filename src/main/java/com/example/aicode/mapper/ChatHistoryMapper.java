package com.example.aicode.mapper;

import com.example.aicode.model.entity.ChatHistory;
import com.mybatisflex.core.BaseMapper;

/**
 * 对话历史 Mapper。
 * <p>
 * 继承 MyBatis-Flex 提供的 {@link BaseMapper}，获得通用的 CRUD 能力，
 * 如 insert、deleteById、updateById、selectOneByQuery、selectListByQuery 等，
 * 无需编写 XML 即可完成数据库操作。
 *
 * @author 程序员yaj
 */
public interface ChatHistoryMapper extends BaseMapper<ChatHistory> {

}
