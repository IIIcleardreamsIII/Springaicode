package com.example.aicode.controller;

import cn.hutool.core.bean.BeanUtil;
import com.example.aicode.annotation.AuthCheck;
import com.example.aicode.common.*;
import com.example.aicode.constant.UserConstant;
import com.example.aicode.model.dto.user.*;
import com.example.aicode.model.dto.vo.LoginUserVO;
import com.example.aicode.model.dto.vo.UserVO;
import com.example.aicode.model.entity.User;
import com.example.aicode.service.UserService;
import com.mybatisflex.core.paginate.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户管理控制器。
 *
 * <p>提供用户注册、登录、注销、获取当前登录用户、增删改查及分页查询等 RESTful 接口。
 * 基础路径为 {@code /user}，部分管理类接口通过 {@link AuthCheck} 注解限制仅管理员可访问。</p>
 *
 * @author <a>程序员yaj</a>
 */
// ============================================================
// HTTP 接口分层与术语速查（请先读这里）
// ------------------------------------------------------------
// REST 风格约定（本项目采用）：
//   GET    /user/get/login  查询"当前登录用户"（幂等、不修改数据）
//   POST   /user/login      执行"登录"动作（登录会改 Session，所以用 POST 而非 GET）
//   POST   /user/register   注册
//   GET    /user/get        管理员查用户（@AuthCheck 拦截）
//   POST   /user/add        管理员增
//   POST   /user/update     管理员改
//   POST   /user/delete     管理员删
//   POST   /user/list/page/vo  分页查询（用 POST 是因为查询条件字段多，放 body 比 url 更合适）
//
// VO（View Object）与 DTO（Data Transfer Object）的区别：
//   实体 User 含密码等敏感字段，绝不能直接返回前端；
//   LoginUserVO / UserVO 是"脱敏后的视图对象"，只含可公开字段。
//   这就是为什么登录接口返回 LoginUserVO 而非 User。
// ============================================================

@Tag(name = "用户管理", description = "用户注册等接口")
@RestController // 所有方法返回 JSON
@RequestMapping("/user") // 类级前缀：本类接口都在 /api/user 下
public class UserController {

    /** 用户服务，处理用户相关业务逻辑（Spring 注入） */
    @Resource
    private UserService userService;

    /**
     * 用户注册。
     *
     * <p>根据账号、密码、确认密码注册新用户，返回新用户 id。</p>
     *
     * @param userRegisterRequest 用户注册请求
     * @return 注册结果（新用户 id）
     */
    @Operation(summary = "用户注册", description = "根据账号、密码、确认密码注册新用户")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "注册成功"),
            @ApiResponse(responseCode = "400", description = "参数错误")
    })
    @PostMapping("register")
    public BaseResponse<Long> userRegister(@RequestBody UserRegisterRequest userRegisterRequest) {
        // 校验请求参数是否为空
        ThrowUtils.throwIf(userRegisterRequest == null, ErrorCode.PARAMS_ERROR);
        // 提取注册信息
        String userAccount = userRegisterRequest.getUserAccount();
        String userPassword = userRegisterRequest.getUserPassword();
        String checkPassword = userRegisterRequest.getCheckPassword();
        // 调用服务层完成注册
        long result = userService.userRegister(userAccount, userPassword, checkPassword);
        return ResultUtils.success(result);
    }
    /**
     * 用户登录。
     *
     * <p>根据账号、密码进行登录，返回脱敏后的用户信息，并在 Session 中记录登录态。</p>
     *
     * @param userLoginRequest 用户登录请求
     * @param request          HTTP 请求对象，用于记录登录态
     * @return 脱敏后的用户信息
     */
    @PostMapping("/login")
    public BaseResponse<LoginUserVO> userLogin(@RequestBody UserLoginRequest userLoginRequest, HttpServletRequest request) {
        // 校验请求参数是否为空
        ThrowUtils.throwIf(userLoginRequest == null, ErrorCode.PARAMS_ERROR);
        // 提取登录信息
        String userAccount = userLoginRequest.getUserAccount();
        String userPassword = userLoginRequest.getUserPassword();
        // 调用服务层完成登录
        LoginUserVO loginUserVO = userService.userLogin(userAccount, userPassword, request);
        return ResultUtils.success(loginUserVO);
    }
    /**
     * 获取当前登录用户。
     *
     * <p>从 Session 中获取登录态，返回脱敏后的当前用户信息。</p>
     *
     * @param request HTTP 请求对象，用于获取登录态
     * @return 脱敏后的当前登录用户信息
     */
    @GetMapping("/get/login")
    public BaseResponse<LoginUserVO> getLoginUser(HttpServletRequest request) {
        // 获取当前登录用户
        User loginUser = userService.getLoginUser(request);
        // 转换为脱敏后的视图对象返回
        return ResultUtils.success(userService.getLoginUserVO(loginUser));
    }
    /**
     * 用户注销。
     *
     * <p>移除 Session 中的登录态，完成注销。</p>
     *
     * @param request HTTP 请求对象，用于移除登录态
     * @return 注销成功返回 true
     */
    @PostMapping("/logout")
    public BaseResponse<Boolean> userLogout(HttpServletRequest request) {
        // 校验请求是否为空
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        // 调用服务层完成注销
        boolean result = userService.userLogout(request);
        return ResultUtils.success(result);
    }
    /**
     * 创建用户（管理员接口）。
     * <p>
     * 默认密码 12345678 经加盐加密后存储。仅管理员（@AuthCheck 拦截）可调用。
     *
     * @param userAddRequest 创建用户请求
     * @return 新用户 id
     */
    @PostMapping("/add")
    // @AuthCheck 是本项目自定义注解（见 annotation 包）。Spring AOP 会拦截带此注解的方法，
    // 在 AuthInterceptor 中检查当前登录用户角色是否等于 ADMIN_ROLE，否则抛"无权限"异常。
    // 相比在每个方法里手写 if(!isAdmin) 校验，注解方式更声明式、更清爽。
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Long> addUser(@RequestBody UserAddRequest userAddRequest) {
        ThrowUtils.throwIf(userAddRequest == null, ErrorCode.PARAMS_ERROR);
        User user = new User();
        // BeanUtil.copyProperties：Hutool 工具，把请求对象的同名字段批量拷贝到实体，免写一堆 setter
        BeanUtil.copyProperties(userAddRequest, user);
        // 默认密码 12345678，加密后存储（避免明文）
        final String DEFAULT_PASSWORD = "12345678";
        String encryptPassword = userService.getEncryptPassword(DEFAULT_PASSWORD);
        user.setUserPassword(encryptPassword);
        // ServiceImpl 提供的 save：插入一条记录
        boolean result = userService.save(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(user.getId());
    }

    /**
     * 根据 id 获取用户（仅管理员）。
     *
     * @param id 用户 id
     * @return 用户实体
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<User> getUserById(long id) {
        // 校验 id 合法性
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询用户
        User user = userService.getById(id);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(user);
    }

    /**
     * 根据 id 获取包装类。
     *
     * <p>获取用户信息并转换为脱敏后的 {@link UserVO}。</p>
     *
     * @param id 用户 id
     * @return 脱敏后的用户视图对象
     */
    @GetMapping("/get/vo")
    public BaseResponse<UserVO> getUserVOById(long id) {
        // 复用获取用户逻辑
        BaseResponse<User> response = getUserById(id);
        User user = response.getData();
        // 转换为脱敏后的 VO 返回
        return ResultUtils.success(userService.getUserVO(user));
    }

    /**
     * 删除用户。
     *
     * <p>管理员接口，根据 id 删除用户。</p>
     *
     * @param deleteRequest 删除请求，包含用户 id
     * @return 删除结果
     */
    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteUser(@RequestBody DeleteRequest deleteRequest) {
        // 校验请求参数及 id 合法性
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 根据 id 删除用户
        boolean b = userService.removeById(deleteRequest.getId());
        return ResultUtils.success(b);
    }

    /**
     * 更新用户。
     *
     * <p>管理员接口，根据请求参数更新用户信息。</p>
     *
     * @param userUpdateRequest 更新用户请求
     * @return 更新结果
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateUser(@RequestBody UserUpdateRequest userUpdateRequest) {
        // 校验请求参数及 id 是否为空
        if (userUpdateRequest == null || userUpdateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = new User();
        // 拷贝请求参数到实体
        BeanUtil.copyProperties(userUpdateRequest, user);
        // 根据 id 更新用户
        boolean result = userService.updateById(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 分页获取用户封装列表（仅管理员）。
     * <p>
     * 先按条件分页查库得到 {@code Page<User>}，再把每条记录转成脱敏的 {@code UserVO}。
     *
     * @param userQueryRequest 查询/分页请求（含页码、每页大小、筛选条件、排序）
     * @return 脱敏后的用户分页数据
     */
    @PostMapping("/list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<UserVO>> listUserVOByPage(@RequestBody UserQueryRequest userQueryRequest) {
        ThrowUtils.throwIf(userQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long pageNum = userQueryRequest.getPageNum();   // 第几页（从 1 开始）
        long pageSize = userQueryRequest.getPageSize(); // 每页多少条
        // Page.of(pageNum, pageSize)：MyBatis-Flex 的分页对象；第二个参数是查询条件包装器
        // page() 执行后 SQL 会自动拼接 LIMIT，并返回总条数 getTotalRow()
        Page<User> userPage = userService.page(Page.of(pageNum, pageSize),
                userService.getQueryWrapper(userQueryRequest));
        // 重新构造一个 VO 分页对象：保留页码/大小和总条数，但记录换成脱敏 VO 列表
        Page<UserVO> userVOPage = new Page<>(pageNum, pageSize, userPage.getTotalRow());
        // getRecords() 取当前页的数据列表；转成 VO 列表（去掉密码等敏感字段）
        List<UserVO> userVOList = userService.getUserVOList(userPage.getRecords());
        userVOPage.setRecords(userVOList);
        return ResultUtils.success(userVOPage);
    }

}
