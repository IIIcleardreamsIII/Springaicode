package com.example.aicode.core;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.example.aicode.common.BusinessException;
import com.example.aicode.common.ErrorCode;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 前端工程化项目构建器（负责把 Vue 工程源码构建为静态产物 dist）。
 * <p>
 * 供两处复用：
 * <ul>
 *   <li>部署（AppServiceImpl）：部署前构建，产出 dist 作为部署源</li>
 *   <li>预览（AiCodeGeneratorFacade）：生成完成后构建，产出 dist 供预览通道访问</li>
 * </ul>
 * 构建采用同步阻塞方式（ProcessBuilder 启动 npm 子进程），
 * 工程较小通常秒级到几十秒完成，需保证服务器已安装 Node.js 与 npm。
 *
 * @author 程序员yaj
 */
public class ProjectBuilder {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ProjectBuilder.class);

    /**
     * 匹配相对导入路径的正则：import ... from './x' / import './x' / from "../x" 等。
     */
    private static final Pattern RELATIVE_IMPORT_PATTERN =
            Pattern.compile("from\\s+['\"](\\.\\.?/[^'\"]+)['\"]|import\\s+['\"](\\.\\.?/[^'\"]+)['\"]");

    /**
     * 解析相对导入时可补的常见扩展名（含空字符串对应"已带扩展名"场景由调用处单独处理）。
     */
    private static final String[] IMPORT_EXTENSIONS = {".ts", ".tsx", ".js", ".jsx", ".vue", ".css", ".scss", ".json"};

    /**
     * 构建工程化项目，产出 dist 目录。
     * <p>
     * 步骤：校验 package.json → npm install → npm run build → 校验 dist 存在。
     *
     * @param projectDir 工程项目根目录（含 package.json）
     * @return 构建产物 dist 目录
     * @throws BusinessException 构建失败时抛出（package.json 缺失 / npm 命令失败 / dist 未生成）
     */
    public static File build(File projectDir) {
        // 1. 校验 package.json 存在，否则无法构建
        File packageJson = new File(projectDir, "package.json");
        if (!packageJson.exists()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "工程缺少 package.json，无法构建");
        }
        // 1.5 校验源码相对导入是否完整（避免 AI 输出被截断，导致引用了未生成的文件）
        validateImports(projectDir);
        // 2. npm install 安装依赖
        try {
            runNpmCommand(projectDir, "npm", "install");
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "npm install 失败：" + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "npm install 被中断");
        }
        // 2.5 归一化构建脚本：vue-tsc 与高版本 typescript 存在工具链耦合，常因
        // "Search string not found: /supportedTSExtensions/" 崩溃。这里把 build 脚本里的
        // vue-tsc 类型检查剥离，仅保留 vite build，避免不兼容导致整个构建失败。
        normalizeBuildScript(projectDir);
        // 3. npm run build 产出 dist
        try {
            runNpmCommand(projectDir, "npm", "run", "build");
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "npm run build 失败：" + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "npm run build 被中断");
        }
        // 4. 校验 dist 目录已生成
        File distDir = new File(projectDir, "dist");
        if (!distDir.exists() || !distDir.isDirectory()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "构建完成但未生成 dist 目录，请检查 vite 配置");
        }
        return distDir;
    }

    /**
     * 校验工程源码中的相对导入是否完整。
     * <p>
     * AI 生成工程时可能因输出长度被截断，导致例如 {@code src/router.ts} 里引用了
     * {@code ./views/HomeView.vue}，但该文件并未生成。此时若直接执行 npm run build，
     * 会抛出晦涩的 Rollup "Could not resolve" 错误，不利于定位问题。
     * 这里在构建前扫描所有源码的相对导入，若发现缺失文件则提前抛出清晰异常。
     *
     * @param projectDir 工程项目根目录
     */
    private static void validateImports(File projectDir) {
        File srcDir = new File(projectDir, "src");
        if (!srcDir.exists()) {
            return;
        }
        List<File> sourceFiles = new ArrayList<>();
        collectSourceFiles(srcDir, sourceFiles);
        List<String> missing = new ArrayList<>();
        for (File source : sourceFiles) {
            String text;
            try {
                text = new String(java.nio.file.Files.readAllBytes(source.toPath()),
                        java.nio.charset.StandardCharsets.UTF_8);
            } catch (IOException e) {
                continue;
            }
            // 匹配相对导入：import x from './...' / import './...' / from "../..." 等
            Matcher matcher = RELATIVE_IMPORT_PATTERN.matcher(text);
            while (matcher.find()) {
                String rel = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
                if (rel == null) {
                    continue;
                }
                File target = resolveRelative(source, rel);
                if (target == null) {
                    missing.add(source.getName() + " -> " + rel);
                }
            }
        }
        if (!missing.isEmpty()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    "工程源码存在缺失的引用文件（AI 生成可能被截断，请重新生成）：" + String.join("；", missing));
        }
    }

    /**
     * 递归收集指定目录下所有前端源码文件（.ts/.vue/.js/.tsx/.jsx）。
     */
    private static void collectSourceFiles(File dir, List<File> out) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File f : files) {
            if (f.isDirectory()) {
                collectSourceFiles(f, out);
            } else if (isSourceFile(f.getName())) {
                out.add(f);
            }
        }
    }

    private static boolean isSourceFile(String name) {
        return name.endsWith(".ts") || name.endsWith(".vue") || name.endsWith(".js")
                || name.endsWith(".tsx") || name.endsWith(".jsx");
    }

    /**
     * 把源码文件里的相对导入路径解析为真实文件；无法解析时返回 null。
     * 兼容省略扩展名、指向目录 index 文件等常见写法。
     */
    private static File resolveRelative(File source, String rel) {
        // 过滤 node_modules 等非源码相对导入（一般不会出现，做防御）
        if (rel.startsWith("../node_modules") || rel.contains("/node_modules/")) {
            return null;
        }
        File base = source.getParentFile();
        File candidate = new File(base, rel);
        // 已带扩展名且文件存在
        if (candidate.isFile()) {
            return candidate;
        }
        // 省略扩展名：尝试补常见扩展名
        for (String ext : IMPORT_EXTENSIONS) {
            File withExt = new File(base, rel + ext);
            if (withExt.isFile()) {
                return withExt;
            }
        }
        // 指向目录：尝试目录下的 index 文件
        for (String ext : IMPORT_EXTENSIONS) {
            File index = new File(base, rel + "/index" + ext);
            if (index.isFile()) {
                return index;
            }
        }
        return null;
    }

    /**
     * 在指定目录下执行一条命令（同步阻塞，等待命令结束）。
     * <p>
     * 使用 {@link ProcessBuilder} 启动子进程，工作目录设为项目目录，
     * 继承父进程 stdout/stderr（便于排查构建错误）。
     * <p>
     * Windows 下 {@code npm} 是 {@code npm.cmd} 批处理脚本，ProcessBuilder 无法直接执行，
     * 需通过 {@code cmd /c} 调用；Linux/macOS 则直接执行可执行文件。
     *
     * @param workDir 命令执行的工作目录
     * @param command 命令及参数（如 {"npm", "run", "build"}）
     * @throws IOException          命令启动失败时抛出
     * @throws InterruptedException 等待被中断时抛出
     */
    /**
     * 归一化 package.json 的 build 脚本：剥离 vue-tsc 类型检查。
     * <p>
     * vue-tsc 的启动脚本会 patch TypeScript 内部源码，当 typescript 版本高于其预期时
     * 报 "Search string not found: /supportedTSExtensions = .*(?=;)/" 而崩溃，导致整个
     * 工程无法构建。剥离后仅用 vite build，既规避不兼容，又不影响产物。
     * 仅做字符串替换，不改动其它字段；无 vue-tsc 时原样保留。
     *
     * @param projectDir 工程项目根目录
     */
    private static void normalizeBuildScript(File projectDir) {
        File packageJson = new File(projectDir, "package.json");
        if (!packageJson.exists()) {
            return;
        }
        String content = FileUtil.readUtf8String(packageJson);
        if (!content.contains("vue-tsc")) {
            return;
        }
        // 匹配 "build": ".....vue-tsc.....及其后的 && vite build 部分"，整体替换为 "vite build"
        String normalized = content.replaceAll(
                "\"build\"\\s*:\\s*\"[^\"]*vue-tsc[^\"]*\"",
                "\"build\": \"vite build\"");
        if (!normalized.equals(content)) {
            FileUtil.writeUtf8String(normalized, packageJson);
            log.info("已归一化 build 脚本（剥离 vue-tsc），避免工具链不兼容：{}", packageJson.getAbsolutePath());
        }
    }

    private static void runNpmCommand(File workDir, String... command) throws IOException, InterruptedException {
        ProcessBuilder processBuilder;
        if (isWindows()) {
            // Windows：用 cmd /c 执行，让 shell 解析 npm.cmd 等批处理脚本
            List<String> cmd = new ArrayList<>();
            cmd.add("cmd");
            cmd.add("/c");
            cmd.addAll(Arrays.asList(command));
            processBuilder = new ProcessBuilder(cmd);
        } else {
            processBuilder = new ProcessBuilder(command);
        }
        processBuilder.directory(workDir);
        // 重定向输出，便于在失败时提取关键错误信息（如 Vue 编译错误的文件与行号）
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        // 读取子进程输出，避免进程因管道缓冲挂起，并用于错误展示
        String output;
        try (java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int len;
            try (java.io.InputStream in = process.getInputStream()) {
                while ((len = in.read(buf)) != -1) {
                    baos.write(buf, 0, len);
                }
            }
            output = baos.toString(java.nio.charset.StandardCharsets.UTF_8);
        }
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            // 从输出中提取关键错误行（含 error / Error / SyntaxError / missing end tag 等），
            // 最多取最后 20 行，避免消息过长，同时保留文件与行号信息帮助用户定位。
            String tail = extractErrorTail(output);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    "命令执行失败（退出码 " + exitCode + "）：" + StrUtil.join(" ", command)
                            + (tail.isEmpty() ? "" : "\n" + tail));
        }
    }

    /**
     * 从命令输出中提取错误尾部，便于在构建失败时定位（如 Vue 编译错误文件与行号）。
     *
     * @param output 子进程完整输出
     * @return 关键错误行拼接，最多 20 行；为空表示无显著错误行
     */
    private static String extractErrorTail(String output) {
        if (output == null || output.isBlank()) {
            return "";
        }
        String[] lines = output.split("\r?\n");
        List<String> errorLines = new ArrayList<>();
        for (String line : lines) {
            if (line.matches(".*(?i)(error|syntaxerror|missing end tag|could not resolve|failed to).*")) {
                errorLines.add(line.strip());
            }
        }
        // 优先返回匹配的错误行；若无匹配则回退到末尾若干行
        List<String> source = errorLines.isEmpty() ? Arrays.asList(lines) : errorLines;
        int from = Math.max(0, source.size() - 20);
        return String.join("\n", source.subList(from, source.size()));
    }

    /**
     * 判断当前运行环境是否为 Windows。
     *
     * @return true 表示 Windows 系统
     */
    private static boolean isWindows() {
        String osName = System.getProperty("os.name");
        return osName != null && osName.toLowerCase().contains("win");
    }
}
