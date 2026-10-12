package com.example.aicode.service;

import com.example.aicode.model.dto.app.AppDeployRequest;
import com.example.aicode.model.dto.app.AppQueryRequest;
import com.example.aicode.model.dto.vo.AppVO;
import com.example.aicode.model.entity.App;
import com.example.aicode.model.entity.User;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;

import java.io.File;
import java.util.List;

/**
 * 应用业务服务接口。
 * <p>
 * 定义应用（App）相关的业务操作规范，继承 MyBatis-Flex 提供的 {@link IService}，
 * 可直接使用其内置的通用 CRUD 方法（如 getById、save、updateById、page 等）。
 * 具体的业务逻辑由实现类 {@code AppServiceImpl} 完成。
 *
 * @author 程序员yaj
 */
public interface AppService extends IService<App> {

    /**
     * 获取应用封装视图对象（VO）。
     * <p>
     * 将应用实体转换为脱敏后的视图对象，并关联查询创建者信息（UserVO）。
     *
     * @param app 应用实体
     * @return 应用视图对象，入参为 null 时返回 null
     */
    AppVO getAppVO(App app);

    /**
     * 批量获取应用封装视图对象列表（VO）。
     * <p>
     * 内部会批量查询创建者信息，避免 N+1 查询问题。
     *
     * @param appList 应用实体列表
     * @return 应用视图对象列表
     */
    List<AppVO> getAppVOList(List<App> appList);

    /**
     * 根据 id 查询应用详情及其关联信息（创建者）。
     * <p>
     * 内部流程：按 id 查询应用实体 → 转换为视图对象并关联填充创建者信息（UserVO）。
     * 相当于 {@code getById} + {@link #getAppVO} 的组合封装，简化 Controller 调用。
     *
     * @param id 应用 id
     * @return 含创建者信息的应用视图对象；应用不存在时返回 null
     */
    AppVO getAppVOById(long id);

    /**
     * 将应用实体分页对象转换为视图对象分页（含每条记录的关联创建者信息）。
     * <p>
     * 保留原分页的页码、每页大小、总条数，仅将记录列表批量转换为 {@link AppVO}，
     * 用于消除各分页查询接口中重复的 VO 转换代码。
     *
     * @param appPage 应用实体分页对象
     * @return 应用视图对象分页
     */
    Page<AppVO> getAppVOPage(Page<App> appPage);

    /**
     * 构造应用查询条件包装器（QueryWrapper）。
     * <p>
     * 将 {@link AppQueryRequest} 中的查询条件转换为 MyBatis-Flex 的查询包装器，
     * 支持 id、优先级、用户 id、部署标识、代码生成类型精确匹配，
     * 应用名称、封面、提示词模糊匹配，并支持排序。
     * 空字段（null）对应的条件会被框架自动忽略。
     *
     * @param appQueryRequest 查询请求
     * @return 查询条件包装器
     */
    QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest);

    /**
     * 部署应用，将生成的代码发布为可在线访问的网站。
     * <p>
     * 业务流程：参数与归属校验 → 幂等生成 deployKey →
     * 将 {@code code_output/{codeGenType}_{appId}} 复制到 {@code code_deploy/{deployKey}} →
     * 更新应用的 deployKey 与部署时间 → 返回可访问 URL。
     *
     * @param appDeployRequest 部署请求（含 appId）
     * @param loginUser        当前登录用户（用于归属校验）
     * @return 部署后的完整访问 URL，格式为 {@code {部署域名}/{deployKey}}
     */
    String deployApp(AppDeployRequest appDeployRequest, User loginUser);

    /**
     * 下载应用源码（打包为 zip）。
     * <p>
     * 仅已部署的应用（deployKey 非空，即已生成访问地址）允许下载，用于二次开发。
     * 打包源码目录 {@code code_output/{codeGenType}_{appId}}，并原子递增下载次数。
     *
     * @param appId     应用 id
     * @param loginUser 当前登录用户（用于归属校验）
     * @return 打包好的 zip 文件；无法下载时抛业务异常
     */
    File downloadAppCode(long appId, User loginUser);

    /**
     * 递增应用的下载次数（原子操作，保证并发安全）。
     *
     * @param appId 应用 id
     */
    void increaseDownloadCount(long appId);

}
