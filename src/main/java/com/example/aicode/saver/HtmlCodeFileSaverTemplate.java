package com.example.aicode.saver;

import cn.hutool.core.util.StrUtil;
import com.example.aicode.ai.model.HtmlCodeResult;
import com.example.aicode.ai.model.enums.CodeGenTypeEnum;
import com.example.aicode.common.BusinessException;
import com.example.aicode.common.ErrorCode;

/**
 * HTML 单文件代码保存器
 * <p>
 * 继承自 {@link CodeFileSaverTemplate}，把泛型 T 固定为 {@link HtmlCodeResult}。
 * 实现 HTML 模式的核心逻辑：把 HTML 代码落盘为 {@code index.html}，
 * 并额外校验 HTML 代码内容非空。
 *
 * @author 程序员yaj
 */
public class HtmlCodeFileSaverTemplate extends CodeFileSaverTemplate<HtmlCodeResult> {

    /**
     * 实现抽象方法：返回 HTML 类型，决定输出目录前缀为 "html"。
     *
     * @return CodeGenTypeEnum.HTML
     */
    @Override
    protected CodeGenTypeEnum getCodeType() {
        return CodeGenTypeEnum.HTML;
    }

    /**
     * 实现抽象方法：把 HTML 代码写入 index.html。
     * 这里仅调用父类的公共工具方法 writeToFile，无需关心目录创建与编码。
     *
     * @param result      HTML 代码结果对象（含 htmlCode）
     * @param baseDirPath 父类已建好的目标目录
     */
    @Override
    protected void saveFiles(HtmlCodeResult result, String baseDirPath) {
        writeToFile(baseDirPath, "index.html", result.getHtmlCode());
    }

    /**
     * 扩展校验：在父类"非空"基础上，进一步要求 HTML 代码内容必须非空。
     * 注意先调用 super.validateInput 沿用父类校验，再追加本类专属规则。
     *
     * @param result HTML 代码结果对象
     * @throws BusinessException 当 HTML 代码为空时抛出
     */
    @Override
    protected void validateInput(HtmlCodeResult result) {
        // super.validateInput 先执行父类的 null 校验
        super.validateInput(result);
        // StrUtil.isBlank：为 null / 空串 / 纯空白 都算空
        if (StrUtil.isBlank(result.getHtmlCode())) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "HTML代码内容不能为空");
        }
    }
}

