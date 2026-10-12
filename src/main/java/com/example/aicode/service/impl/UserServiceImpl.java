package com.example.aicode.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.example.aicode.common.BusinessException;
import com.example.aicode.common.ErrorCode;
import com.example.aicode.model.dto.user.UserQueryRequest;
import com.example.aicode.model.dto.vo.LoginUserVO;
import com.example.aicode.model.enums.UserRoleEnum;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.example.aicode.model.entity.User;
import com.example.aicode.mapper.UserMapper;
import com.example.aicode.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import static com.example.aicode.constant.UserConstant.USER_LOGIN_STATE;

/**
 * 用户服务层实现。
 *
 * <p>实现 {@link UserService} 接口，提供用户注册、登录、注销、获取当前登录用户、
 * 密码加密、查询条件构建及用户信息脱敏等业务逻辑。</p>
 *
 * <p>继承 MyBatis-Flex 的 {@link ServiceImpl}，自动具备基础 CRUD 能力，
 * 同时通过 {@link Service} 注解交由 Spring 容器管理。</p>
 *
 * @author <a>程序员yaj</a>
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>  implements UserService{

    // ============================================================
    // 关键安全概念（请先读这里）
    // ------------------------------------------------------------
    // ① 密码绝不明文存储：数据库里存的永远是"加盐 MD5 密文"。
    //    "盐（salt）"是一段固定字符串，拼接在密码前再哈希，使相同密码在
    //    不同系统密文不同，且无法用彩虹表反查。登录时把用户输入同样加盐哈希，
    //    与库里密文比对，从而"永远不直接处理明文密码"。
    //
    // ② Session 登录态：登录成功后把 User 放进 HttpSession（服务端内存，
    //    浏览器持 JSESSIONID cookie）。后续请求凭 cookie 取到 Session 即知"谁登录了"，
    //    无需每次传账号密码。getLoginUser 正是从 Session 取登录态。
    //
    // ③ 逻辑删除（isDelete）：删除用户时并不真删库记录，而是把 isDelete 置 1，
    //    MyBatis-Flex 自动在查询时过滤已删除行，便于数据恢复与审计。
    // ============================================================

    /**
     * 用户注册。
     * <p>流程：参数校验 → 账号重复校验 → 密码加盐加密 → 写入数据库（默认普通用户角色）。</p>
     *
     * @param userAccount   用户账户（需唯一）
     * @param userPassword  用户密码
     * @param checkPassword 确认密码（需与 userPassword 一致）
     * @return 新注册用户的 id（雪花 ID）
     * @throws BusinessException 参数不合法、账号重复或写入失败时抛出
     */
    @Override
    public long userRegister(String userAccount, String userPassword, String checkPassword) {
        // 1. 校验参数：账号、密码、确认密码均不能为空（hasBlank 任一为空即 true）
        if (StrUtil.hasBlank(userAccount, userPassword, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        // 账号长度不能少于 4 位（简单规则，可按需调整）
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号过短");
        }
        // 密码长度不能少于 8 位（提升爆破成本）
        if (userPassword.length() < 8 || checkPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户密码过短");
        }
        // 两次输入的密码必须一致（防止用户手误）
        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入的密码不一致");
        }
        // 2. 检查账号是否重复，保证唯一（先查 count 再插入，避免重复注册）
        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.eq("userAccount", userAccount); // eq = 等于
        long count = this.mapper.selectCountByQuery(queryWrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号重复");
        }
        // 3. 加密密码（加盐 MD5），避免明文存储
        String encryptPassword = getEncryptPassword(userPassword);
        // 4. 组装用户实体并持久化
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(encryptPassword);
        user.setUserName("无名"); // 默认昵称
        user.setUserRole(UserRoleEnum.USER.getValue()); // 新用户默认普通角色
        boolean saveResult = this.save(user); // ServiceImpl 提供的 save 方法
        if (!saveResult) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "注册失败，数据库错误");
        }
        return user.getId();
    }

    /**
     * 加密密码（加盐 MD5）。
     * <p>
     * 把固定盐值 "yaj" 拼在密码前，再用 MD5 生成 32 位十六进制密文。
     * 注册和登录都用同一逻辑，才能保证比对一致。
     *
     * @param userPassword 原始密码（明文）
     * @return 加密后的密文（MD5 摘要，不可逆）
     */
    @Override
    public String getEncryptPassword(String userPassword) {
        // 盐值：固定混淆串，提升哈希安全性（实际项目建议每用户随机盐并存库）
        final String SALT = "yaj";
        // DigestUtils.md5DigestAsHex：Spring 提供的 MD5 工具，返回十六进制字符串
        // (SALT + userPassword).getBytes()：字符串转字节数组作为 MD5 输入
        return DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
    }

    /**
     * 获取脱敏的已登录用户信息。
     *
     * <p>将 User 实体属性拷贝到 {@link LoginUserVO}，过滤敏感字段。</p>
     *
     * @param user 用户实体
     * @return 脱敏后的登录用户视图对象；当传入的用户为空时返回 null
     */
    @Override
    public LoginUserVO getLoginUserVO(User user) {
        // 入参为空直接返回 null
        if (user == null) {
            return null;
        }
        LoginUserVO loginUserVO = new LoginUserVO();
        // 使用 Hutool 拷贝属性到 VO，实现数据脱敏
        BeanUtil.copyProperties(user, loginUserVO);
        return loginUserVO;
    }

    /**
     * 用户登录。
     *
     * <p>登录流程：参数校验 -> 密码加密 -> 查询用户 -> 写入 Session 登录态 -> 返回脱敏用户信息。</p>
     *
     * @param userAccount  用户账户
     * @param userPassword 用户密码
     * @param request      HTTP 请求对象，用于获取 Session 记录登录态
     * @return 脱敏后的用户信息
     * @throws BusinessException 参数为空、账号密码格式不合法或用户不存在时抛出
     */
    @Override
    public LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request) {
        // 1. 校验参数：账号、密码均不能为空
        if (StrUtil.hasBlank(userAccount, userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号错误");
        }
        if (userPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码错误");
        }
        // 2. 把用户输入的明文密码按同样的加盐规则哈希，再与库里密文比对
        //    （绝不能把库里密文解密去比明文，因为 MD5 不可逆；只能"同法再算一次"比对）
        String encryptPassword = getEncryptPassword(userPassword);
        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.eq("userAccount", userAccount);
        queryWrapper.eq("userPassword", encryptPassword); // 账号+密文都匹配才算登录成功
        User user = this.mapper.selectOneByQuery(queryWrapper);
        if (user == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在或密码错误");
        }
        // 3. 登录成功：把用户对象放入 Session（键名来自 UserConstant.USER_LOGIN_STATE）
        //    Session 由服务器维护，浏览器只持有 JSESSIONID cookie 来关联
        request.getSession().setAttribute(USER_LOGIN_STATE, user);
        // 4. 返回脱敏后的用户信息（含 LoginUserVO，已剔除密码等敏感字段）
        return this.getLoginUserVO(user);
    }

    /**
     * 获取当前登录用户。
     *
     * <p>从 Session 中取出登录态，并从数据库重新查询最新的用户信息。</p>
     *
     * @param request HTTP 请求对象，用于获取 Session 中的登录态
     * @return 当前登录的用户实体
     * @throws BusinessException 未登录或用户不存在时抛出
     */
    @Override
    public User getLoginUser(HttpServletRequest request) {
        // 从 Session 取登录态（键名 USER_LOGIN_STATE 由 UserConstant 定义，需与登录时一致）
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        User currentUser = (User) userObj;
        // 登录态不存在或 id 缺失 → 视为未登录（前端据此跳登录页）
        if (currentUser == null || currentUser.getId() == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        // 二次查库：从数据库取最新数据（避免 Session 中旧对象与实际不一致，
        // 例如管理员改了角色后，下次请求能拿到最新权限。性能敏感场景可跳过）
        long userId = currentUser.getId();
        currentUser = this.getById(userId);
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return currentUser;
    }

    /**
     * 用户注销。
     *
     * <p>校验是否已登录后，从 Session 中移除登录态。</p>
     *
     * @param request HTTP 请求对象，用于获取并移除 Session 中的登录态
     * @return 注销成功返回 true
     * @throws BusinessException 未登录时抛出
     */
    @Override
    public boolean userLogout(HttpServletRequest request) {
        // 先判断是否已登录：从 Session 中获取登录态
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        if (userObj == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "未登录");
        }
        // 移除登录态，完成注销
        request.getSession().removeAttribute(USER_LOGIN_STATE);
        return true;
    }

    /**
     * 获取查询条件包装器。
     *
     * <p>根据查询请求参数构建 MyBatis-Flex 的 {@link QueryWrapper}，用于分页和列表查询。
     * 其中 id、用户角色为精确匹配，账号、用户名、用户简介为模糊匹配，
     * 并支持按指定字段升序/降序排序。</p>
     *
     * @param userQueryRequest 查询请求参数
     * @return MyBatis-Flex QueryWrapper
     * @throws BusinessException 请求参数为空时抛出
     */
    @Override
    public QueryWrapper getQueryWrapper(UserQueryRequest userQueryRequest) {
        // 查询请求为空直接报错（controller 已做过一次判空，这里兜底）
        if (userQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        // 提取查询条件（前端可只传其中部分，其余为 null）
        Long id = userQueryRequest.getId();
        String userAccount = userQueryRequest.getUserAccount();
        String userName = userQueryRequest.getUserName();
        String userProfile = userQueryRequest.getUserProfile();
        String userRole = userQueryRequest.getUserRole();
        String sortField = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();
        // MyBatis-Flex 的"链式"构建查询条件：
        //   eq   = 等于（id、role 精确匹配）
        //   like = 模糊匹配（账号/用户名/简介，自动拼 %keyword%）
        //   orderBy(field, asc?)：field 为 null 时不排序；第二个布尔值 true=升序
        // 注意：eq / like 传 null 时框架会自动忽略该条件，所以空条件不会报错
        return QueryWrapper.create()
                .eq("id", id)
                .eq("userRole", userRole)
                .like("userAccount", userAccount)
                .like("userName", userName)
                .like("userProfile", userProfile)
                .orderBy(sortField, "ascend".equals(sortOrder));
    }

}
