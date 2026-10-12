package com.example.aicode.saver;

import cn.hutool.core.util.StrUtil;
import com.example.aicode.ai.model.ProjectCodeResult;
import com.example.aicode.ai.model.ProjectCodeResult.ProjectFile;
import com.example.aicode.ai.model.enums.CodeGenTypeEnum;
import com.example.aicode.common.BusinessException;
import com.example.aicode.common.ErrorCode;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.List;

/**
 * 前端工程化项目代码保存器。
 * <p>
 * 继承自 {@link CodeFileSaverTemplate}，泛型 T 固定为 {@link ProjectCodeResult}。
 * 与 HTML / 多文件保存器不同，工程化项目由数量不固定的多文件（且含多级子目录）组成，
 * 因此本保存器遍历 {@link ProjectCodeResult#getFileList()}，按每个文件的
 * {@code relativePath} 递归创建目录并落盘，天然支持任意目录层级与文件数量。
 *
 * @author yupi
 */
@Slf4j
public class ProjectCodeFileSaverTemplate extends CodeFileSaverTemplate<ProjectCodeResult> {

    /**
     * 实现抽象方法：返回 project 类型，决定输出目录前缀为 "project"。
     *
     * @return CodeGenTypeEnum.PROJECT
     */
    @Override
    protected CodeGenTypeEnum getCodeType() {
        return CodeGenTypeEnum.PROJECT;
    }

    /**
     * 实现抽象方法：遍历文件清单，按相对路径逐一落盘。
     * <p>
     * 相对路径可能包含子目录（如 src/components/HelloWorld.vue），
     * 这里先补齐父目录再写入文件。
     *
     * @param result      工程化项目结果对象（含 fileList）
     * @param baseDirPath 父类已建好的项目根目录
     */
    @Override
    protected void saveFiles(ProjectCodeResult result, String baseDirPath) {
        List<ProjectFile> fileList = result.getFileList();
        if (fileList == null || fileList.isEmpty()) {
            return;
        }
        for (ProjectFile file : fileList) {
            String relativePath = file.getRelativePath();
            String content = file.getContent();
            // 跳过无路径或空内容的文件
            if (StrUtil.isBlank(relativePath) || content == null) {
                continue;
            }
            // 归一化：统一用系统分隔符，并去掉开头的 / 或 ./，防止越界写盘
            String normalized = relativePath.replace('/', File.separatorChar).replace('\\', File.separatorChar);
            normalized = normalized.replaceFirst("^[./\\\\]+", "");
            if (normalized.isEmpty()) {
                continue;
            }
            File target = new File(baseDirPath, normalized);
            // 递归创建父目录
            File parent = target.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            // 落盘（不经过 writeToFile，因空内容文件也需要保留，如 .gitkeep）
            cn.hutool.core.io.FileUtil.writeString(content, target, java.nio.charset.StandardCharsets.UTF_8);
            // .vue 文件完整性校验：template/script/style 三段结束标签任一缺失，
            // 说明 AI 输出大概率被截断，落盘后构建必然失败，提前告警便于诊断与提示自愈方向。
            if (normalized.endsWith(".vue") && !hasCompleteVueStructure(content)) {
                log.error("检测到 .vue 文件可能输出被截断（缺少结束标签）：{}，该文件构建时大概率报 " +
                        "\"Element is missing end tag\"，建议在自愈时拆分为多个子组件", normalized);
            }
        }
    }

    /**
     * 轻量校验 .vue 文件结构完整性：template / script / style 三段的结束标签是否齐全。
     * <p>
     * 若任一结束标签缺失，说明 AI 输出在写入前被截断（单文件过大超出 token 预算），
     * 此类文件无法通过常规自愈修复（自愈同样受输出预算限制），需拆分组件。
     *
     * @param content .vue 文件内容
     * @return 三对结束标签齐全返回 true
     */
    private boolean hasCompleteVueStructure(String content) {
        return content != null
                && content.contains("</template>")
                && content.contains("</script>")
                && content.contains("</style>");
    }

    /**
     * 扩展校验：要求文件清单非空（至少有一个文件）。
     *
     * @param result 工程化项目结果对象
     * @throws BusinessException 当文件清单为空时抛出
     */
    @Override
    protected void validateInput(ProjectCodeResult result) {
        super.validateInput(result);
        if (result.getFileList() == null || result.getFileList().isEmpty()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    "工程化项目文件清单不能为空（AI 未按约定格式输出文件，请要求模型使用" +
                            "\"// 文件：相对路径\" 标注每个文件并以代码块包裹完整内容）");
        }
    }
}
