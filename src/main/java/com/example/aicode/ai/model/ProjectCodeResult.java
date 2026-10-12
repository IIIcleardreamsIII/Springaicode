package com.example.aicode.ai.model;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

/**
 * 前端工程化项目代码生成结果模型。
 * <p>
 * 对应 {@code project} 生成类型：AI 生成一套 Vue3 + Vite + TypeScript 完整工程，
 * 由多个独立文件构成。为避免文件数量不固定导致结构化字段爆炸，本模型采用
 * "文件清单"描述方式——AI 返回一个 {@link #fileList}（每个元素含相对路径与内容），
 * 后端按清单逐一落盘，从而支持任意文件数量与目录层级。
 *
 * @author yupi
 */
@Description("生成前端工程化项目的结果，包含项目元信息与按相对路径组织的文件清单")
@Data
public class ProjectCodeResult {

    /**
     * 项目名称（用于 package.json 的 name 字段与目录展示）
     */
    @Description("项目名称（小写、不含空格，如 ai-todo-app）")
    private String projectName;

    /**
     * 项目简介（一句中文描述）
     */
    @Description("项目简介（一句中文描述）")
    private String description;

    /**
     * 文件清单：AI 返回的工程文件列表，每个元素含 relativePath（相对项目根目录的路径）
     * 与 content（文件完整内容）。
     */
    @Description("工程文件清单，每个元素包含 relativePath 与 content 两个字段")
    private java.util.List<ProjectFile> fileList;

    /**
     * 单个工程文件描述。
     */
    @Data
    public static class ProjectFile {
        /**
         * 文件相对路径（如 src/App.vue、vite.config.ts、package.json）
         */
        @Description("文件相对项目根目录的路径")
        private String relativePath;

        /**
         * 文件完整内容
         */
        @Description("文件完整内容")
        private String content;
    }
}
