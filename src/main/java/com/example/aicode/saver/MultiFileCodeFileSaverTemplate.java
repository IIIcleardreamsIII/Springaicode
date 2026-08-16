package com.example.aicode.saver;

import cn.hutool.core.util.StrUtil;
import com.example.aicode.ai.model.MultiFileCodeResult;
import com.example.aicode.ai.model.enums.CodeGenTypeEnum;
import com.example.aicode.common.BusinessException;
import com.example.aicode.common.ErrorCode;

/**
 * 多文件代码保存器
 * <p>
 * 继承自 {@link CodeFileSaverTemplate}，泛型 T 固定为 {@link MultiFileCodeResult}。
 * 把 HTML/CSS/JS 三个字段分别落盘为 index.html / style.css / script.js。
 * HTML 为必需项，CSS 和 JS 为空时父类 writeToFile 会自动跳过（不生成空文件）。
 *
 * @author 程序员yaj
 */
public class MultiFileCodeFileSaverTemplate extends CodeFileSaverTemplate<MultiFileCodeResult> {

    /**
     * 实现抽象方法：返回多文件类型，决定输出目录前缀为 "multi_file"。
     *
     * @return CodeGenTypeEnum.MULTI_FILE
     */
    @Override
    public CodeGenTypeEnum getCodeType() {
        return CodeGenTypeEnum.MULTI_FILE;
    }

    /**
     * 实现抽象方法：分别把三个字段写入对应文件。
     * writeToFile 内部已做非空判断，因此 CSS/JS 为空时自动跳过。
     *
     * @param result      多文件代码结果对象（含 htmlCode/cssCode/jsCode）
     * @param baseDirPath 父类已建好的目标目录
     */
    @Override
    protected void saveFiles(MultiFileCodeResult result, String baseDirPath) {
        writeToFile(baseDirPath, "index.html", result.getHtmlCode());
        writeToFile(baseDirPath, "style.css", result.getCssCode());
        writeToFile(baseDirPath, "script.js", result.getJsCode());
    }

    /**
     * 扩展校验：要求 HTML 代码必须存在（CSS/JS 允许为空）。
     *
     * @param result 多文件代码结果对象
     * @throws BusinessException 当 HTML 代码为空时抛出
     */
    @Override
    protected void validateInput(MultiFileCodeResult result) {
        super.validateInput(result);
        if (StrUtil.isBlank(result.getHtmlCode())) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "HTML代码内容不能为空");
        }
    }
}

