package com.example.aicode.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.ZipUtil;
import com.example.aicode.ai.model.enums.CodeGenTypeEnum;
import com.example.aicode.common.BusinessException;
import com.example.aicode.common.ErrorCode;
import com.example.aicode.common.ThrowUtils;
import com.example.aicode.constant.AppConstant;
import com.example.aicode.core.ProjectBuilder;
import com.example.aicode.mapper.AppMapper;
import com.example.aicode.model.dto.app.AppDeployRequest;
import com.example.aicode.model.dto.app.AppQueryRequest;
import com.example.aicode.model.dto.vo.AppVO;
import com.example.aicode.model.dto.vo.UserVO;
import com.example.aicode.model.entity.App;
import com.example.aicode.model.entity.User;
import com.example.aicode.service.AppService;
import com.example.aicode.service.UserService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateChain;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 应用业务服务实现类。
 * <p>
 * 实现 {@link AppService} 中定义的应用相关业务逻辑，继承 MyBatis-Flex 提供的
 * {@link ServiceImpl}，可直接使用其内置的通用数据库操作方法（如 getById、save、page 等），
 * 底层通过注入的 {@link AppMapper} 与数据库交互。
 *
 * @author 程序员yaj
 */
@Slf4j
@Service
public class AppServiceImpl extends ServiceImpl<AppMapper, App> implements AppService {

    /**
     * 用户服务，用于在返回应用视图对象时关联查询创建者信息
     */
    @Resource
    private UserService userService;

    /**
     * 获取应用封装视图对象（VO）。
     * <p>
     * 转换流程：字段拷贝 → 按 userId 关联查询创建者 → 填充脱敏后的 UserVO。
     *
     * @param app 应用实体
     * @return 应用视图对象，入参为 null 时返回 null
     */
    @Override
    public AppVO getAppVO(App app) {
        if (app == null) {
            return null;
        }
        // 将实体同名字段批量拷贝到 VO（Hutool BeanUtil）
        AppVO appVO = new AppVO();
        BeanUtil.copyProperties(app, appVO);
        // 关联查询创建者信息（脱敏后填充）
        Long userId = app.getUserId();
        if (userId != null) {
            User user = userService.getById(userId);
            appVO.setUser(userService.getUserVO(user));
        }
        return appVO;
    }

    /**
     * 批量获取应用封装视图对象列表（VO）。
     * <p>
     * 为避免 N+1 查询问题（每条记录单独查一次用户），这里先收集所有 userId，
     * 一次性批量查询用户并构建 id → UserVO 的映射，再统一填充。
     *
     * @param appList 应用实体列表
     * @return 应用视图对象列表（入参为空时返回空列表）
     */
    @Override
    public List<AppVO> getAppVOList(List<App> appList) {
        if (CollUtil.isEmpty(appList)) {
            return new ArrayList<>();
        }
        // 1. 收集所有应用的创建者 id（Set 去重）
        Set<Long> userIdSet = appList.stream()
                .map(App::getUserId)
                .collect(Collectors.toSet());
        // 2. 一次性批量查询用户，并构建 userId → UserVO 映射
        Map<Long, UserVO> userIdUserVOMap = userService.listByIds(userIdSet).stream()
                .map(userService::getUserVO)
                .collect(Collectors.toMap(UserVO::getId, userVO -> userVO));
        // 3. 逐条转换实体为 VO，并从映射中填充创建者信息
        return appList.stream().map(app -> {
            AppVO appVO = new AppVO();
            BeanUtil.copyProperties(app, appVO);
            appVO.setUser(userIdUserVOMap.get(app.getUserId()));
            return appVO;
        }).collect(Collectors.toList());
    }

    /**
     * 根据 id 查询应用详情及其关联信息（创建者）。
     * <p>
     * 内部流程：按 id 查询应用实体 → 转换为视图对象并关联填充创建者信息（UserVO）。
     * 应用不存在时 getAppVO(null) 返回 null，由调用方决定如何响应。
     *
     * @param id 应用 id
     * @return 含创建者信息的应用视图对象；应用不存在时返回 null
     */
    @Override
    public AppVO getAppVOById(long id) {
        // 1. 按 id 查询应用实体（MyBatis-Flex 内置方法，逻辑删除的记录查不到）
        App app = this.getById(id);
        // 2. 复用单条转换逻辑，关联填充创建者信息
        return getAppVO(app);
    }

    /**
     * 将应用实体分页对象转换为视图对象分页（含每条记录的关联创建者信息）。
     *
     * @param appPage 应用实体分页对象
     * @return 应用视图对象分页
     */
    @Override
    public Page<AppVO> getAppVOPage(Page<App> appPage) {
        // 保留分页元数据（页码/每页大小/总条数）
        Page<AppVO> appVOPage = new Page<>(appPage.getPageNumber(), appPage.getPageSize(), appPage.getTotalRow());
        // 批量转换记录为 VO（内部会批量关联查询创建者，避免 N+1）
        appVOPage.setRecords(getAppVOList(appPage.getRecords()));
        return appVOPage;
    }

    /**
     * 构造应用查询条件包装器（QueryWrapper）。
     * <p>
     * 链式构建查询条件：eq 精确匹配、like 模糊匹配、orderBy 排序。
     * MyBatis-Flex 会自动忽略值为 null 的条件，因此无需逐个判空。
     *
     * @param appQueryRequest 查询请求
     * @return 查询条件包装器
     */
    @Override
    public QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest) {
        // 校验请求参数是否为空
        if (appQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        // 提取查询条件字段
        Long id = appQueryRequest.getId();
        String appName = appQueryRequest.getAppName();
        String cover = appQueryRequest.getCover();
        String initPrompt = appQueryRequest.getInitPrompt();
        String codeGenType = appQueryRequest.getCodeGenType();
        String deployKey = appQueryRequest.getDeployKey();
        Integer priority = appQueryRequest.getPriority();
        Long userId = appQueryRequest.getUserId();
        String sortField = appQueryRequest.getSortField();
        String sortOrder = appQueryRequest.getSortOrder();
        // 构建查询条件：id/优先级/用户/部署标识/生成类型精确匹配，
        // 名称/封面/提示词模糊匹配，并支持排序（"ascend" 表示升序）
        return QueryWrapper.create()
                .eq("id", id)
                .eq("priority", priority)
                .eq("userId", userId)
                .eq("deployKey", deployKey)
                .eq("codeGenType", codeGenType)
                .like("appName", appName)
                .like("cover", cover)
                .like("initPrompt", initPrompt)
                .orderBy(sortField, "ascend".equals(sortOrder));
    }

    /**
     * 部署应用，将生成的代码发布为可在线访问的网站。
     * <p>
     * 目录与 URL 的对应关系：
     * <pre>
     *   生成产物：{CODE_OUTPUT_ROOT_DIR}/{codeGenType}_{appId}/   （如 tmp/code_output/html_123/）
     *   部署产物：{CODE_DEPLOY_ROOT_DIR}/{deployKey}/              （如 tmp/code_deploy/aB3x9Kq2/）
     *   访问地址：{CODE_DEPLOY_HOST}/{deployKey}                   （如 http://localhost:8123/api/aB3x9Kq2）
     * </pre>
     *
     * @param appDeployRequest 部署请求（含 appId）
     * @param loginUser        当前登录用户（用于归属校验）
     * @return 部署后的完整访问 URL
     */
    @Override
    public String deployApp(AppDeployRequest appDeployRequest, User loginUser) {
        // 1. 参数校验：请求体与 appId 必须合法
        ThrowUtils.throwIf(appDeployRequest == null, ErrorCode.PARAMS_ERROR);
        Long appId = appDeployRequest.getAppId();
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 错误");
        // 2. 查询应用是否存在
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        // 3. 权限校验：仅应用创建者本人可以部署该应用
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限部署该应用");
        }
        // 4. 生成 deployKey（幂等：应用已有 deployKey 则直接复用，
        //    重复部署只会用最新代码覆盖原目录，不会生成新的访问地址）
        String deployKey = app.getDeployKey();
        if (StrUtil.isBlank(deployKey)) {
            deployKey = generateUniqueDeployKey();
        }
        // 5. 定位源目录（AI 生成代码的输出目录），不存在说明尚未生成过代码
        String sourceDirName = StrUtil.format("{}_{}", app.getCodeGenType(), appId);
        File sourceDir = new File(AppConstant.CODE_OUTPUT_ROOT_DIR, sourceDirName);
        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用代码不存在，请先生成代码");
        }
        // 5.1 工程化项目类型：先执行 npm install + npm run build 构建出 dist 目录，
        //     再以 dist 作为真正的部署源。这样与 html/multi_file 一样"生成即部署"，
        //     Nginx 直接托管 dist 即可。构建失败则抛异常中断部署。
        File deploySourceDir = sourceDir;
        if (CodeGenTypeEnum.PROJECT.getValue().equals(app.getCodeGenType())) {
            deploySourceDir = ProjectBuilder.build(sourceDir);
        }
        // 6. 将源目录（或构建产物 dist）完整复制到部署目录（overwrite=true 覆盖旧文件，保证重复部署拿到最新代码）
        File deployDir = new File(AppConstant.CODE_DEPLOY_ROOT_DIR, deployKey);
        try {
            FileUtil.copyContent(deploySourceDir, deployDir, true);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "部署失败：" + e.getMessage());
        }
        // 7. 更新数据库：写入 deployKey 与部署时间
        App updateApp = new App();
        updateApp.setId(appId);
        updateApp.setDeployKey(deployKey);
        updateApp.setDeployedTime(LocalDateTime.now());
        boolean updateResult = this.updateById(updateApp);
        ThrowUtils.throwIf(!updateResult, ErrorCode.OPERATION_ERROR, "更新应用部署信息失败");
        // 8. 拼接并返回可访问 URL：{部署域名}/{deployKey}
        return StrUtil.format("{}/{}", AppConstant.CODE_DEPLOY_HOST, deployKey);
    }

    /**
     * 下载应用源码（打包为 zip），供用户二次开发。
     * <p>
     * 业务规则：
     * <ul>
     *   <li>仅应用创建者本人可下载（归属校验）；</li>
     *   <li>必须先部署（deployKey 非空，即已生成访问地址）才能下载；</li>
     *   <li>打包 {@code code_output/{codeGenType}_{appId}} 源码目录为 zip；</li>
     *   <li>下载成功后递增下载次数。</li>
     * </ul>
     *
     * @param appId     应用 id
     * @param loginUser 当前登录用户
     * @return 打包好的 zip 文件
     */
    @Override
    public File downloadAppCode(long appId, User loginUser) {
        // 1. 查询应用是否存在
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        // 2. 权限校验：仅应用创建者本人可下载
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限下载该应用");
        }
        // 3. 校验是否已部署：只有生成了访问地址（deployKey 非空）才能下载
        ThrowUtils.throwIf(StrUtil.isBlank(app.getDeployKey()), ErrorCode.FORBIDDEN_ERROR, "请先部署应用，生成访问地址后才能下载代码");
        // 4. 定位源码目录（AI 生成代码的输出目录）
        String sourceDirName = StrUtil.format("{}_{}", app.getCodeGenType(), appId);
        File sourceDir = new File(AppConstant.CODE_OUTPUT_ROOT_DIR, sourceDirName);
        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用代码不存在，请先生成代码");
        }
        // 5. 打包为 zip（zip 文件放在临时目录，文件名用应用名 + id 保证可辨识）
        String zipName = StrUtil.format("{}_{}.zip", StrUtil.blankToDefault(app.getAppName(), "app"), appId);
        File zipFile = new File(FileUtil.getTmpDir(), zipName);
        ZipUtil.zip(sourceDir.getAbsolutePath(), zipFile.getAbsolutePath(), false);
        ThrowUtils.throwIf(!zipFile.exists(), ErrorCode.SYSTEM_ERROR, "代码打包失败");
        // 6. 递增下载次数（原子操作）：计数失败不应阻断下载，仅记录日志
        try {
            increaseDownloadCount(appId);
        } catch (Exception ex) {
            log.error("递增应用下载次数失败，appId={}", appId, ex);
        }
        return zipFile;
    }

    /**
     * 递增应用的下载次数（原子操作，避免并发下载导致计数丢失）。
     * <p>
     * 通过 MyBatis-Flex 的 update 直接执行 {@code downloadCount = downloadCount + 1}，
     * 而非"读-改-写"，保证并发场景下计数准确。
     *
     * @param appId 应用 id
     */
    @Override
    public void increaseDownloadCount(long appId) {
        // 用 UpdateChain 的 setRaw 直接拼 SQL 实现原子自增（downloadCount = downloadCount + 1），
        // 避免"读-改-写"在并发下载时丢失计数
        UpdateChain.of(App.class)
                .setRaw("downloadCount", "downloadCount + 1", false)
                .where("id = " + appId)
                .update();
    }

    /**
     * 生成全局唯一的 deployKey。
     * <p>
     * 规则：由大小写字母 + 数字组成的 6 位随机字符串（如 aB3x9K），
     * 生成后查库确认未被占用（冲突概率极低，循环重试即可）。
     *
     * @return 未被占用的 deployKey
     */
    private String generateUniqueDeployKey() {
        String deployKey;
        do {
            // RandomUtil.randomString：Hutool 工具，生成含大小写字母和数字的随机串
            deployKey = RandomUtil.randomString(6);
            // 查库校验唯一性（逻辑删除条件由 MyBatis-Flex 自动附加）
        } while (this.count(QueryWrapper.create().eq("deployKey", deployKey)) > 0);
        return deployKey;
    }

}
