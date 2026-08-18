package com.example.aicode.saver;

import com.example.aicode.ai.model.HtmlCodeResult;
import com.example.aicode.ai.model.MultiFileCodeResult;
import com.example.aicode.ai.model.ProjectCodeResult;
import com.example.aicode.ai.model.enums.CodeGenTypeEnum;
import com.example.aicode.common.BusinessException;
import com.example.aicode.common.ErrorCode;

import java.io.File;

/**
 * 代码文件保存执行器（调度层，与解析执行器对称设计）
 * <p>
 * 根据代码生成类型，分派到对应的 {@link CodeFileSaverTemplate} 子类执行保存，
 * 隐藏了"该 new 哪个保存器"的细节，上层（facade）只管调用 executeSaver。
 * <p>
 * 说明：本方法入参 codeResult 声明为 Object，是因为两个保存器接受不同类型
 * （HtmlCodeResult / MultiFileCodeResult）。这里用 switch 分支做"向下转型（cast）"，
 * 由调用方保证传入类型与 codeGenType 匹配，否则运行期可能 ClassCastException。
 *
 * @author 程序员yaj
 */
public class CodeFileSaverExecutor {

    /** HTML 保存器单例（无状态、线程安全，复用即可） */
    private static final HtmlCodeFileSaverTemplate htmlCodeFileSaver = new HtmlCodeFileSaverTemplate();

    /** 多文件保存器单例 */
    private static final MultiFileCodeFileSaverTemplate multiFileCodeFileSaver = new MultiFileCodeFileSaverTemplate();

    /** 前端工程化项目保存器单例 */
    private static final ProjectCodeFileSaverTemplate projectCodeFileSaver = new ProjectCodeFileSaverTemplate();

    /**
     * 执行代码保存。
     *
     * @param codeResult  解析后的代码结果对象（类型须与 codeGenType 对应）
     * @param codeGenType 代码生成类型
     * @return 保存代码所在的目录
     * @throws BusinessException 当类型为空或不支持时抛出
     */
    public static File executeSaver(Object codeResult, CodeGenTypeEnum codeGenType) {
        if (codeGenType == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "代码生成类型为空");
        }
        return switch (codeGenType) {
            // 向下转型为 HtmlCodeResult 后调用模板方法 saveCode
            case HTML -> htmlCodeFileSaver.saveCode((HtmlCodeResult) codeResult);
            // 向下转型为 MultiFileCodeResult 后调用
            case MULTI_FILE -> multiFileCodeFileSaver.saveCode((MultiFileCodeResult) codeResult);
            // 向下转型为 ProjectCodeResult 后调用
            case PROJECT -> projectCodeFileSaver.saveCode((ProjectCodeResult) codeResult);
            default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型: " + codeGenType);
        };
    }

    /**
     * 执行代码保存（应用集成版）。
     * <p>
     * 与 {@link #executeSaver(Object, CodeGenTypeEnum)} 逻辑一致，
     * 但目录命名使用 {@code codeGenType_appId}，
     * 使生成的网站目录与数据库中的应用记录一一对应。
     *
     * @param codeResult  解析后的代码结果对象（类型须与 codeGenType 对应）
     * @param codeGenType 代码生成类型
     * @param appId       应用 id（用于构建目录名）
     * @return 保存代码所在的目录
     * @throws BusinessException 当类型为空或不支持时抛出
     */
    public static File executeSaver(Object codeResult, CodeGenTypeEnum codeGenType, Long appId) {
        if (codeGenType == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "代码生成类型为空");
        }
        return switch (codeGenType) {
            case HTML -> htmlCodeFileSaver.saveCode((HtmlCodeResult) codeResult, appId);
            case MULTI_FILE -> multiFileCodeFileSaver.saveCode((MultiFileCodeResult) codeResult, appId);
            case PROJECT -> projectCodeFileSaver.saveCode((ProjectCodeResult) codeResult, appId);
            default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型: " + codeGenType);
        };
    }
}

