package com.example.aicode.controller;

import cn.hutool.core.util.StrUtil;
import com.example.aicode.constant.AppConstant;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;

// ============================================================
// 本类说明（静态资源托管：预览 + 部署 双通道）
// ------------------------------------------------------------
// ① 预览通道（/api/static/{目录名}/**）：
//    直接读取 CODE_OUTPUT_ROOT_DIR（tmp/code_output）下 AI 生成的最新产物，
//    目录名为 {codeGenType}_{appId}（如 html_123）。用于"生成后即可预览"，
//    重新进入对话页也能恢复预览，重新生成后刷新即见最新代码。
//
// ② 部署通道（/api/{deployKey}/**）：
//    读取 CODE_DEPLOY_ROOT_DIR（tmp/code_deploy）下的正式发布产物，
//    目录名为随机 deployKey（如 aB3x9K）。部署是"快照"：点击部署时把
//    当前生成产物复制过来，重复部署覆盖同名目录，保证 URL 稳定不变。
//
// 生产环境建议用 Nginx 直接托管 code_deploy 目录，本控制器仅用于开发联调。
// ============================================================

/**
 * 静态资源控制器（预览与部署站点的访问入口）。
 * <p>
 * 包含路径穿越防护（正则 + canonical path 双重校验）与常见文件类型的 MIME 识别。
 *
 * @author 程序员yaj
 */
@Hidden // 不纳入接口文档：路径含通配符，且属于资源访问入口而非业务接口
@RestController
public class StaticResourceController {

    /** 部署根目录（与部署服务复制目标保持一致） */
    private static final String DEPLOY_DIR = AppConstant.CODE_DEPLOY_ROOT_DIR;

    /** 代码生成输出根目录（与 saver 模块落盘目录保持一致） */
    private static final String OUTPUT_DIR = AppConstant.CODE_OUTPUT_ROOT_DIR;

    // ==================== 部署通道（正式发布的站点） ====================

    /**
     * 访问部署地址根路径时，302 重定向到 index.html。
     * <p>
     * 例如 {@code /api/aB3x9K} → 重定向到 {@code /api/aB3x9K/index.html}。
     * 重定向的意义：浏览器随后会以 {@code /api/aB3x9K/} 作为相对路径基准，
     * 页面中 {@code <link href="style.css">} 等相对引用才能解析到正确的部署子目录。
     *
     * @param deployKey 应用部署标识
     * @param request   HTTP 请求（用于拼接含全局前缀的完整路径）
     * @return 302 重定向响应
     */
    @GetMapping("/{deployKey}")
    public ResponseEntity<Void> redirectDeployToIndex(@PathVariable String deployKey, HttpServletRequest request) {
        return redirect(request.getContextPath() + "/" + deployKey + "/index.html");
    }

    /**
     * 提供部署目录下静态资源的访问。
     * <p>
     * URL 格式：{@code /api/{deployKey}/{文件相对路径}}，
     * 例如 {@code /api/aB3x9K/index.html} 映射到磁盘
     * {@code tmp/code_deploy/aB3x9K/index.html}。
     *
     * @param deployKey 应用部署标识
     * @param request   HTTP 请求（用于提取通配符匹配到的文件路径）
     * @return 文件资源响应；路径非法 / 越权 / 不存在时分别返回 400 / 403 / 404
     */
    @GetMapping("/{deployKey}/**")
    public ResponseEntity<Resource> serveDeployedFile(@PathVariable String deployKey, HttpServletRequest request) {
        // deployKey 只允许字母数字（与生成规则一致）
        if (!isValidDirName(deployKey)) {
            return ResponseEntity.badRequest().build();
        }
        // 去掉 "/{deployKey}" 前缀，剩下的就是文件相对路径
        String filePath = extractFilePath(request, deployKey);
        return serveFile(DEPLOY_DIR, deployKey, filePath);
    }

    // ==================== 预览通道（最新生成产物） ====================

    /**
     * 访问预览地址根路径时，302 重定向到 index.html。
     * <p>
     * 例如 {@code /api/static/html_123} → 重定向到 {@code /api/static/html_123/index.html}，
     * 使浏览器以 {@code /api/static/html_123/} 作为相对路径基准解析页面内的 CSS/JS 外链。
     *
     * @param dirName 生成目录名（{codeGenType}_{appId}，如 html_123 / multi_file_123）
     * @param request HTTP 请求
     * @return 302 重定向响应
     */
    @GetMapping("/static/{dirName}")
    public ResponseEntity<Void> redirectPreviewToIndex(@PathVariable String dirName, HttpServletRequest request) {
        return redirect(request.getContextPath() + "/static/" + dirName + "/index.html");
    }

    /**
     * 提供代码输出目录下静态资源的访问（生成预览）。
     * <p>
     * URL 格式：{@code /api/static/{目录名}/{文件相对路径}}，
     * 例如 {@code /api/static/multi_file_123/style.css} 映射到磁盘
     * {@code tmp/code_output/multi_file_123/style.css}。
     * <p>
     * 注意：Spring MVC 的 URL 匹配规则中，字面量路径（/static/...）
     * 优先级高于模板路径（/{deployKey}/...），因此不会与部署通道冲突。
     *
     * @param dirName 生成目录名
     * @param request HTTP 请求
     * @return 文件资源响应；路径非法 / 越权 / 不存在时分别返回 400 / 403 / 404
     */
    @GetMapping("/static/{dirName}/**")
    public ResponseEntity<Resource> servePreviewFile(@PathVariable String dirName, HttpServletRequest request) {
        // 目录名允许字母数字下划线（与 saver 的命名规则 codeGenType_appId 一致）
        if (StrUtil.isBlank(dirName) || !dirName.matches("^[a-zA-Z0-9_]+$")) {
            return ResponseEntity.badRequest().build();
        }
        // 去掉 "/static/{dirName}" 前缀
        String filePath = extractFilePath(request, "static/" + dirName);
        return serveFile(OUTPUT_DIR, dirName, filePath);
    }

    // ==================== 公共方法 ====================

    /**
     * 构造 302 重定向响应。
     *
     * @param location 重定向目标地址（含全局前缀的绝对路径）
     * @return 302 响应
     */
    private ResponseEntity<Void> redirect(String location) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, location)
                .build();
    }

    /**
     * 校验目录名是否为纯字母数字（deployKey 的合法字符集）。
     *
     * @param name 目录名
     * @return 是否合法
     */
    private boolean isValidDirName(String name) {
        return StrUtil.isNotBlank(name) && name.matches("^[a-zA-Z0-9]+$");
    }

    /**
     * 从请求路径中提取指定前缀之后的文件相对路径。
     *
     * @param request     HTTP 请求
     * @param pathSegment 需要剥离的路径段（如 "aB3x9K" 或 "static/html_123"）
     * @return 文件相对路径（不含前导斜杠）；为空时返回 index.html
     */
    private String extractFilePath(HttpServletRequest request, String pathSegment) {
        String contextPath = request.getContextPath(); // 全局前缀 /api
        String requestUri = request.getRequestURI();   // 完整路径
        // 去掉 contextPath 与 "/{pathSegment}" 前缀
        String filePath = requestUri.substring(contextPath.length() + pathSegment.length() + 1);
        if (StrUtil.isBlank(filePath) || "/".equals(filePath)) {
            return "index.html"; // 访问目录根路径时默认返回首页
        }
        // 去掉前导斜杠，便于与父目录安全拼接
        return filePath.startsWith("/") ? filePath.substring(1) : filePath;
    }

    /**
     * 从指定根目录中读取文件并返回资源响应。
     * <p>
     * 使用"规范路径（canonical path）"校验防止 ../ 路径穿越：
     * 目标文件解析后的真实路径必须仍位于目标子目录之内。
     *
     * @param rootDir  根目录（预览或部署根目录）
     * @param subDir   子目录名（deployKey 或生成目录名）
     * @param filePath 文件相对路径
     * @return 文件资源响应；越权返回 403，不存在返回 404，异常返回 500
     */
    private ResponseEntity<Resource> serveFile(String rootDir, String subDir, String filePath) {
        try {
            File subDirFile = new File(rootDir, subDir);
            File targetFile = new File(subDirFile, filePath);
            // canonical path 校验：解析 .. 和符号链接后的真实路径必须在子目录内
            String canonicalSubDir = subDirFile.getCanonicalPath();
            String canonicalTarget = targetFile.getCanonicalPath();
            if (!canonicalTarget.startsWith(canonicalSubDir + File.separator)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            // 文件不存在（或不是文件）返回 404
            if (!targetFile.exists() || !targetFile.isFile()) {
                return ResponseEntity.notFound().build();
            }
            // 按扩展名推断 Content-Type 并返回文件内容（FileSystemResource 由 Spring 流式写出）
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(getContentType(targetFile.getName())))
                    .body(new FileSystemResource(targetFile));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 根据文件扩展名返回对应的 MIME 类型。
     * <p>
     * 浏览器依据 Content-Type 决定如何解析响应：HTML 渲染、CSS 加载、JS 执行等。
     * 文本类资源统一附带 UTF-8 字符集，避免中文乱码。
     *
     * @param fileName 文件名（含扩展名）
     * @return MIME 类型字符串
     */
    private String getContentType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html;charset=UTF-8";
        if (lower.endsWith(".css")) return "text/css;charset=UTF-8";
        if (lower.endsWith(".js")) return "application/javascript;charset=UTF-8";
        if (lower.endsWith(".json")) return "application/json;charset=UTF-8";
        if (lower.endsWith(".txt")) return "text/plain;charset=UTF-8";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".ico")) return "image/x-icon";
        if (lower.endsWith(".woff")) return "font/woff";
        if (lower.endsWith(".woff2")) return "font/woff2";
        if (lower.endsWith(".ttf")) return "font/ttf";
        if (lower.endsWith(".mp4")) return "video/mp4";
        return "application/octet-stream"; // 未知类型按二进制流处理（浏览器触发下载）
    }
}
