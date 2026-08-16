package com.example.aicode.core;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.example.aicode.ai.model.HtmlCodeResult;
import com.example.aicode.ai.model.MultiFileCodeResult;
import com.example.aicode.ai.model.enums.CodeGenTypeEnum;
import com.example.aicode.constant.AppConstant;

import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * 代码文件保存工具类（旧版，纯静态方法风格）
 * <p>
 * 与 saver 包下的"模板方法"设计不同，本类是一组无状态的静态工具方法，
 * 直接把 AI 结果对象落盘，由 {@link CodeGeneratorController#generateCode} 同步生成时调用。
 * <p>
 * 注意：本项目存在两套保存实现——
 * ① 本类（core.CodeFileSaver，静态工具）；
 * ② saver 包（模板方法 + 执行器，被 facade 流式路径调用）。
 * 二者根目录、命名规则一致，为避免混淆，建议在后续重构中统一为一套。
 *
 * @author yupi
 */
public class CodeFileSaver {

    /** 文件保存根目录，统一取自 {@link AppConstant#CODE_OUTPUT_ROOT_DIR}（确保与部署源目录一致） */
    private static final String FILE_SAVE_ROOT_DIR = AppConstant.CODE_OUTPUT_ROOT_DIR;

    /**
     * 保存 HTML 单文件代码结果：建目录 → 写 index.html → 返回目录。
     *
     * @param result HTML 代码结果对象
     * @return 保存的目标目录 {@link File}
     */
    public static File saveHtmlCodeResult(HtmlCodeResult result) {
        String baseDirPath = buildUniqueDir(CodeGenTypeEnum.HTML.getValue());
        writeToFile(baseDirPath, "index.html", result.getHtmlCode());
        return new File(baseDirPath);
    }

    /**
     * 保存多文件代码结果：建目录 → 写 index.html/style.css/script.js → 返回目录。
     *
     * @param result 多文件代码结果对象
     * @return 保存的目标目录 {@link File}
     */
    public static File saveMultiFileCodeResult(MultiFileCodeResult result) {
        String baseDirPath = buildUniqueDir(CodeGenTypeEnum.MULTI_FILE.getValue());
        writeToFile(baseDirPath, "index.html", result.getHtmlCode());
        writeToFile(baseDirPath, "style.css", result.getCssCode());
        writeToFile(baseDirPath, "script.js", result.getJsCode());
        return new File(baseDirPath);
    }

    /**
     * 构建唯一目录路径。
     * <p>
     * 命名规则：{根目录}/{类型}_{雪花ID}，例如 tmp/code_output/html_1893728465629102080。
     * 雪花 ID 保证并发多次生成互不覆盖。
     *
     * @param bizType 业务类型标识（来自枚举的 value）
     * @return 创建的目录绝对路径
     */
    private static String buildUniqueDir(String bizType) {
        // StrUtil.format：Hutool 的占位符格式化，{0}、{1}……或 {} 顺序填充
        String uniqueDirName = StrUtil.format("{}_{}", bizType, IdUtil.getSnowflakeNextIdStr());
        String dirPath = FILE_SAVE_ROOT_DIR + File.separator + uniqueDirName;
        FileUtil.mkdir(dirPath); // 递归创建目录
        return dirPath;
    }

    /**
     * 将内容写入指定目录下的文件（UTF-8）。
     *
     * @param dirPath  目标目录路径
     * @param filename 文件名
     * @param content  文件内容
     */
    private static void writeToFile(String dirPath, String filename, String content) {
        String filePath = dirPath + File.separator + filename; // File.separator 跨平台路径分隔符（Windows \ / Linux /）
        FileUtil.writeString(content, filePath, StandardCharsets.UTF_8);
    }
}

