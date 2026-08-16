package com.example.aicode.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 跨域资源共享（CORS）全局配置类。
 *
 * <p>该配置类实现了 {@link WebMvcConfigurer} 接口，用于统一配置全局的跨域访问策略，
 * 允许前端应用（如浏览器中的页面）跨域访问后端接口。由于前后端分离架构下前端与后端
 * 通常部署在不同域名或端口下，浏览器会出于安全限制拦截跨域请求，因此需要在此放开限制。</p>
 *
 * <p>配置策略说明：
 * <ul>
 *   <li>允许所有来源（Origin）发起跨域请求；</li>
 *   <li>允许携带 Cookie，保证登录态可以跨域传递；</li>
 *   <li>允许常见的 HTTP 方法（GET、POST、PUT、DELETE、OPTIONS）；</li>
 *   <li>允许携带任意请求头，并暴露所有响应头。</li>
 * </ul>
 * </p>
 *
 * @author AI Code
 * @version 1.0
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    /**
     * 重写父类方法，自定义全局跨域映射规则。
     *
     * <p>当 {@code allowCredentials} 设置为 {@code true} 时，不能直接使用
     * {@code allowedOrigins("*")}，否则会与 Cookie 携带策略产生冲突，
     * 因此必须使用 {@code allowedOriginPatterns("*")} 来匹配来源域名。</p>
     *
     * @param registry Spring MVC 提供的跨域注册器，用于注册和配置跨域映射规则
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 覆盖所有请求路径，对所有接口生效
        registry.addMapping("/**")
                // 允许发送 Cookie，保证登录态可在跨域请求中传递
                .allowCredentials(true)
                // 放行哪些域名（必须用 patterns，否则 * 会和 allowCredentials 冲突）
                .allowedOriginPatterns("*")
                // 允许的 HTTP 方法，包含常见的增删改查及预检请求方法
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                // 允许客户端在请求中携带任意请求头
                .allowedHeaders("*")
                // 允许前端读取任意响应头信息
                .exposedHeaders("*");
    }
}

