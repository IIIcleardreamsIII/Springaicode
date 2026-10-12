package com.example.aicode.constant;

import java.io.File;

/**
 * 应用相关常量定义接口。
 *
 * <p>该接口集中维护与应用（App）相关的常量值，供各业务模块统一引用，
 * 避免在代码中硬编码魔法数字/字符串。接口中定义的常量默认为 {@code public static final}，
 * 可直接通过接口名访问。</p>
 *
 * @author 程序员yaj
 */
public interface AppConstant {

    /**
     * 精选应用的优先级阈值。
     *
     * <p>应用表中的 priority 字段用于标记应用的重要程度，
     * 当管理员将某应用的优先级设置为该值（99）时，
     * 该应用即视为"精选应用"，会在精选列表接口中被查询出来。</p>
     */
    int GOOD_APP_PRIORITY = 99;

    /**
     * 默认应用优先级。
     *
     * <p>新创建的应用默认优先级为 0，表示普通应用（非精选）。</p>
     */
    int DEFAULT_APP_PRIORITY = 0;

    /**
     * 应用代码生成输出根目录（临时产物目录）。
     *
     * <p>AI 生成代码后，文件先落盘到该目录下，命名规则为
     * {@code {codeGenType}_{appId}}（如 {@code html_123}、{@code multi_file_123}）。
     * 预览功能也读取该目录，确保生成后即可预览；
     * 部署时再从该目录复制到 {@link #CODE_DEPLOY_ROOT_DIR}。</p>
     */
    String CODE_OUTPUT_ROOT_DIR = System.getProperty("user.dir") + File.separator + "tmp" + File.separator + "code_output";

    /**
     * 应用部署根目录（正式发布产物目录）。
     *
     * <p>部署成功后，应用文件以 deployKey 为目录名存放在此，
     * 如 {@code tmp/code_deploy/aB3x9Kq2/}。该目录应由 Web 服务器
     * （Nginx 或 Spring 静态资源控制器）对外提供访问。</p>
     */
    String CODE_DEPLOY_ROOT_DIR = System.getProperty("user.dir") + File.separator + "tmp" + File.separator + "code_deploy";

    /**
     * 部署域名（拼接在 deployKey 前，构成完整可访问 URL）。
     *
     * <p>本地开发时指向 Spring 静态资源控制器（含全局前缀 /api）；
     * 生产环境使用 Nginx 托管部署目录时，改为真实域名即可（如 https://deploy.example.com），
     * 业务代码无需任何改动。</p>
     */
    String CODE_DEPLOY_HOST = "http://localhost:8123/api";
}
