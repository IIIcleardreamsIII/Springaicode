package com.example.aicode.saver;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.example.aicode.ai.model.enums.CodeGenTypeEnum;
import com.example.aicode.common.BusinessException;
import com.example.aicode.common.ErrorCode;
import com.example.aicode.constant.AppConstant;

import java.io.File;
import java.nio.charset.StandardCharsets;

// ============================================================
// 模板方法模式（Template Method Pattern）科普（请先读这里）
// ------------------------------------------------------------
// 这是本类最核心的设计思想：父类定义"算法的骨架"（固定步骤顺序），
// 把其中"会变的部分"声明为抽象方法，留给子类去实现。
//
// 本类中：saveCode() 是"模板方法"，它定义固定流程：
//   验证 → 建目录 → 写文件 → 返回
// 其中 validateInput()、getCodeType()、saveFiles() 是"钩子/抽象步骤"，
// 由子类（HTML 保存器 / 多文件保存器）各自实现。
//
// 好处：① 避免子类重复写"建目录、写文件"等公共逻辑；
//       ② 流程顺序被父类用 final 锁死，子类无法破坏；
//       ③ 新增一种保存方式只需新增一个子类，不动父类。
// ============================================================

/**
 * 抽象代码文件保存器（模板方法模式 + 泛型 T）
 * <p>
 * 定义"保存代码到本地磁盘"的通用骨架，具体如何校验、如何落盘由子类决定。
 *
 * @param <T> 待保存的代码结果类型（HtmlCodeResult / MultiFileCodeResult）
 * @author 程序员yaj
 */
public abstract class CodeFileSaverTemplate<T> {

    /** 文件保存根目录：统一取自 {@link AppConstant#CODE_OUTPUT_ROOT_DIR}，
     *  确保生成落盘、预览、部署复制的源目录三处一致。
     *  protected：子类可直接访问；static final：全局唯一常量，类加载时确定 */
    protected static final String FILE_SAVE_ROOT_DIR = AppConstant.CODE_OUTPUT_ROOT_DIR;

    /**
     * 模板方法：保存代码的标准流程（final 修饰，禁止子类重写，确保流程不被篡改）。
     *
     * @param result 代码结果对象（泛型 T）
     * @return 保存代码所在的目录 {@link File} 对象
     */
    public final File saveCode(T result) {
        // 步骤 1：校验入参（子类可扩展校验规则）
        validateInput(result);
        // 步骤 2：基于类型+雪花ID构建唯一的输出目录
        String baseDirPath = buildUniqueDir();
        // 步骤 3：调用子类实现的"具体落盘逻辑"（多态分发）
        saveFiles(result, baseDirPath);
        // 步骤 4：返回目录，便于上层记录路径 / 返回给前端
        return new File(baseDirPath);
    }

    /**
     * 模板方法（应用集成版）：保存代码并与指定应用关联。
     * <p>
     * 与 {@link #saveCode(Object)} 的唯一区别是目录命名规则：
     * 使用 {@code codeGenType_appId}（如 {@code html_123}），
     * 使生成目录与应用一一对应——通过 appId 查库拿到应用信息后，
     * 即可按同一规则拼出目录路径找到网站文件。
     *
     * @param result 代码结果对象（泛型 T）
     * @param appId  应用 id（用于构建目录名）
     * @return 保存代码所在的目录 {@link File} 对象
     */
    public final File saveCode(T result, Long appId) {
        // 步骤 1：校验入参
        validateInput(result);
        // 步骤 2：基于类型+appId 构建应用专属目录
        String baseDirPath = buildUniqueDir(appId);
        // 步骤 3：调用子类实现的"具体落盘逻辑"
        saveFiles(result, baseDirPath);
        // 步骤 4：返回目录
        return new File(baseDirPath);
    }

    /**
     * 校验输入（钩子方法，子类可 @Override 扩展）。
     * 默认实现仅做"非空"兜底校验。
     *
     * @param result 代码结果对象
     */
    protected void validateInput(T result) {
        if (result == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "代码结果对象不能为空");
        }
    }

    /**
     * 构建唯一目录路径（final，公共逻辑，所有子类复用）。
     * <p>
     * 目录命名格式：{根目录}/{类型}_{雪花ID}，例如
     * tmp/code_output/html_1893728465629102080，
     * 用雪花 ID 保证每次生成互不覆盖。
     *
     * @return 新建目录的绝对路径
     */
    protected final String buildUniqueDir() {
        // getCodeType() 是抽象方法，由子类返回 HTML / MULTI_FILE
        String codeType = getCodeType().getValue();
        // IdUtil.getSnowflakeNextIdStr()：Hutool 的雪花算法，生成全局唯一、趋势递增的 ID 字符串
        String uniqueDirName = StrUtil.format("{}_{}", codeType, IdUtil.getSnowflakeNextIdStr());
        String dirPath = FILE_SAVE_ROOT_DIR + File.separator + uniqueDirName;
        // FileUtil.mkdir：Hutool 工具，递归创建目录（父目录不存在也会一并创建）
        FileUtil.mkdir(dirPath);
        return dirPath;
    }

    /**
     * 构建应用专属目录路径（final，公共逻辑）。
     * <p>
     * 目录命名格式：{根目录}/{类型}_{appId}，例如
     * tmp/code_output/multi_file_1893728465629102080。
     * 同一应用重复生成时目录名固定，直接覆盖旧文件即可（mkdir 对已存在目录无副作用）。
     *
     * @param appId 应用 id
     * @return 应用专属目录的绝对路径
     */
    protected final String buildUniqueDir(Long appId) {
        if (appId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "应用 id 不能为空");
        }
        String codeType = getCodeType().getValue();
        String uniqueDirName = StrUtil.format("{}_{}", codeType, appId);
        String dirPath = FILE_SAVE_ROOT_DIR + File.separator + uniqueDirName;
        FileUtil.mkdir(dirPath);
        return dirPath;
    }

    /**
     * 写入单个文件的工具方法（final，公共逻辑）。
     * 非空才写，避免生成空的 css/js 文件。
     *
     * @param dirPath  目标目录
     * @param filename 文件名（如 index.html）
     * @param content  文件内容
     */
    protected final void writeToFile(String dirPath, String filename, String content) {
        // StrUtil.isNotBlank：非空且含非空白字符才写入
        if (StrUtil.isNotBlank(content)) {
            String filePath = dirPath + File.separator + filename;
            // FileUtil.writeString：Hutool 一次性写字符串到文件，指定 UTF-8 编码（避免中文乱码）
            FileUtil.writeString(content, filePath, StandardCharsets.UTF_8);
        }
    }

    /**
     * 抽象方法：获取代码类型（由子类实现，决定目录前缀与校验策略）。
     *
     * @return 对应的 {@link CodeGenTypeEnum}
     */
    protected abstract CodeGenTypeEnum getCodeType();

    /**
     * 抽象方法：保存文件的具体实现（由子类实现核心落盘逻辑）。
     *
     * @param result      代码结果对象
     * @param baseDirPath 已创建好的基础目录路径
     */
    protected abstract void saveFiles(T result, String baseDirPath);
}

