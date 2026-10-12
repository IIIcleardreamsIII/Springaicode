package com.example.aicode.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import com.example.aicode.model.dto.user.UserQueryRequest;
import com.example.aicode.model.dto.vo.LoginUserVO;
import com.example.aicode.model.dto.vo.UserVO;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.example.aicode.model.entity.User;
import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户服务层接口。
 *
 * <p>定义了用户注册、登录、注销、获取当前登录用户、密码加密、
 * 查询条件构建以及用户信息脱敏等核心业务方法。</p>
 *
 * <p>继承自 MyBatis-Flex 的 {@link IService}，自动具备基础的 CRUD 能力。</p>
 *
 * @author <a>程序员yaj</a>
 */
public interface UserService extends IService<User> {
    /**
     * 用户注册。
     *
     * <p>校验账号密码合法性、检查账号是否重复、对密码进行加密后写入数据库。</p>
     *
     * @param userAccount   用户账户
     * @param userPassword  用户密码
     * @param checkPassword 校验密码（需与用户密码一致）
     * @return 新用户 id
     * @throws BusinessException 参数为空、账号过短、密码过短、两次密码不一致、账号重复或数据库写入失败时抛出
     */
    long userRegister(String userAccount, String userPassword, String checkPassword);
    /**
     * 获取脱敏的已登录用户信息。
     *
     * <p>将 User 实体中的敏感字段（如密码）过滤后，转换为 {@link LoginUserVO} 返回。</p>
     *
     * @param user 用户实体
     * @return 脱敏后的登录用户视图对象；当传入的用户为空时返回 null
     */
    LoginUserVO getLoginUserVO(User user);
    /**
     * 用户登录。
     *
     * <p>校验账号密码、对密码加密后查询用户是否存在，
     * 校验通过后将用户信息写入 Session 记录登录态，并返回脱敏后的用户信息。</p>
     *
     * @param userAccount  用户账户
     * @param userPassword 用户密码
     * @param request      HTTP 请求对象，用于获取 Session 记录登录态
     * @return 脱敏后的用户信息
     * @throws BusinessException 参数为空、账号密码格式不合法或用户不存在时抛出
     */
    LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request);
    /**
     * 获取当前登录用户。
     *
     * <p>从 Session 中取出登录态，并从数据库重新查询最新的用户信息。</p>
     *
     * @param request HTTP 请求对象，用于获取 Session 中的登录态
     * @return 当前登录的用户实体
     * @throws BusinessException 未登录或用户不存在时抛出
     */
    User getLoginUser(HttpServletRequest request);
    /**
     * 用户注销。
     *
     * <p>校验是否已登录后，从 Session 中移除登录态。</p>
     *
     * @param request HTTP 请求对象，用于获取并移除 Session 中的登录态
     * @return 注销成功返回 true
     * @throws BusinessException 未登录时抛出
     */
    boolean userLogout(HttpServletRequest request);

    /**
     * 加密密码。
     *
     * <p>使用加盐 MD5 算法对原始密码进行加密，用于注册和登录时的密码处理。</p>
     *
     * @param userPassword 原始密码
     * @return 加密后的密码
     */
    String getEncryptPassword(String userPassword);

    /**
     * 获取查询条件包装器。
     *
     * <p>根据查询请求参数构建 MyBatis-Flex 的 {@link QueryWrapper}，用于分页和列表查询。</p>
     *
     * @param userQueryRequest 查询请求参数
     * @return MyBatis-Flex QueryWrapper
     */
    QueryWrapper getQueryWrapper(UserQueryRequest userQueryRequest);

    /**
     * 获取脱敏的用户信息。
     *
     * <p>将 User 实体转换为 {@link UserVO}，过滤敏感字段。</p>
     *
     * @param user 用户实体
     * @return 脱敏后的用户 VO；当传入的用户为空时返回 null
     */
    default UserVO getUserVO(User user) {
        // 入参为空直接返回 null，避免空指针
        if (user == null) {
            return null;
        }
        UserVO userVO = new UserVO();
        // 使用 Hutool 拷贝属性到 VO，实现数据脱敏
        BeanUtil.copyProperties(user, userVO);
        return userVO;
    }

    /**
     * 批量获取脱敏的用户信息。
     *
     * <p>遍历用户实体列表，逐个转换为 {@link UserVO}。</p>
     *
     * @param userList 用户实体列表
     * @return 脱敏后的用户 VO 列表；当列表为空时返回空集合
     */
    default List<UserVO> getUserVOList(List<User> userList) {
        // 列表为空时返回空集合，避免 NPE
        if (CollUtil.isEmpty(userList)) {
            return new ArrayList<>();
        }
        // 使用流式处理逐个脱敏并收集为列表
        return userList.stream().map(this::getUserVO).collect(Collectors.toList());
    }

}
