package com.example.aicode.ai.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

// ============================================================
// 枚举（enum）基础科普
// ------------------------------------------------------------
// enum 是 Java 的 "枚举类型"，用于定义一组固定的命名常量。
// 相比直接用字符串 "html" / "multi_file" 散落在代码各处，
// 使用枚举可以：① 编译期就能检查拼写错误；② 集中管理取值；
// ③ 通过 value 字段与数据库/前端约定好的字符串做映射。
// ============================================================

/**
 * 代码生成类型枚举
 * <p>
 * 定义 AI 代码生成支持的两种模式。前端调用接口时通过 codeGenType 字段传入对应的
 * value 字符串（"html" 或 "multi_file"），后端据此选择不同的生成与保存策略。
 *
 * @author yupi
 */
@Getter // Lombok：为每个字段自动生成 getter 方法（如 getText()、getValue()）
public enum CodeGenTypeEnum {

    /** 原生 HTML 模式：AI 只生成一段语义化 HTML 结构，不含样式与脚本 */
    HTML("原生 HTML 模式", "html"),

    /** 原生多文件模式：AI 分别生成 HTML、CSS、JS 三个文件 */
    MULTI_FILE("原生多文件模式", "multi_file");

    /** text：给人类看的展示文本（例如页面下拉框显示用） */
    private final String text;

    /** value：与数据库 / 前端 / 接口约定好的唯一标识符（核心判定依据） */
    private final String value;

    /**
     * 枚举构造器（constructor）
     * <p>
     * 注意：枚举的构造器默认且必须是 private（可以不写，编译器强制），
     * 因为枚举常量只能在类内部定义，外部不能 new 枚举对象。
     *
     * @param text  展示文本
     * @param value 标识值
     */
    CodeGenTypeEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据 value 字符串反查枚举常量。
     * <p>
     * 典型用途：前端传来 "multi_file" 这种字符串，
     * 后端调用本方法拿到 MULTI_FILE 枚举，再 switch 分发逻辑。
     *
     * @param value 标识值（如 "html" / "multi_file"）
     * @return 匹配到的枚举；参数为空或匹配不到时返回 null
     */
    public static CodeGenTypeEnum getEnumByValue(String value) {
        // ObjUtil.isEmpty：Hutool 工具，判断 null 或空字符串，避免空指针
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        // values() 是编译器自动生成的静态方法，返回所有枚举常量数组
        for (CodeGenTypeEnum anEnum : CodeGenTypeEnum.values()) {
            if (anEnum.value.equals(value)) {
                return anEnum;
            }
        }
        return null;
    }
}

