package com.example.aicode.mapper;

import com.mybatisflex.core.BaseMapper;
import com.example.aicode.model.entity.User;

/**
 * 用户映射层接口。
 *
 * <p>继承自 MyBatis-Flex 的 {@link BaseMapper}，自动具备针对 {@link User} 实体的
 * 基础数据库操作能力（如增删改查），无需手写 SQL 即可完成单表 CRUD。</p>
 *
 * @author <a>程序员yaj</a>
 */
public interface UserMapper extends BaseMapper<User> {

}
