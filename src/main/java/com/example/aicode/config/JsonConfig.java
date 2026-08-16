package com.example.aicode.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.jackson.JsonComponent;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

// ============================================================
// 为什么要做 "Long 转 String"？（新手必看）
// ------------------------------------------------------------
// Java 的 Long 是 64 位整数，最大值约 9.2×10^18；
// 而 JavaScript 的 Number 只有 53 位精度（最大安全整数 2^53-1 ≈ 9×10^15）。
// 本项目用雪花 ID 做主键（如 1893728465629102080），超过了 JS 安全整数范围，
// 若直接以数字传给前端，JS 会丢失末尾精度（变成 1893728465629102000），
// 再拿回来查库就对不上了。
// 解决方案：后端序列化 JSON 时把 Long 转成字符串，前端拿到的是字符串，精度不丢。
// ============================================================

/**
 * Spring MVC JSON 序列化全局配置
 * <p>
 * 通过 Jackson 配置，统一把 Long / long 类型在序列化成 JSON 时转为字符串，
 * 避免大整数传给前端 JavaScript 时精度丢失。
 *
 * @author yupi
 */
@JsonComponent // Spring Boot 自动扫描并注册本类的 Jackson 自定义配置
public class JsonConfig {

    /**
     * 自定义 ObjectMapper（JSON 序列化/反序列化核心对象）。
     * <p>
     * 注册一个把 Long 序列化为字符串的模块，返回后 Spring 会用它覆盖默认 ObjectMapper。
     *
     * @param builder Spring 提供的构建器，已预置好默认配置
     * @return 配置好的 ObjectMapper
     */
    @Bean // 声明为 Spring Bean，容器启动时加载
    public ObjectMapper jacksonObjectMapper(Jackson2ObjectMapperBuilder builder) {
        // 基于默认 builder 创建 ObjectMapper，createXmlMapper(false) 表示不启用 XML 支持
        ObjectMapper objectMapper = builder.createXmlMapper(false).build();
        // SimpleModule 是 Jackson 的"扩展模块"，可往里注册自定义序列化/反序列化规则
        SimpleModule module = new SimpleModule();
        // 把 Long（包装类）和 long（基本类型）都注册为"序列化成字符串"
        module.addSerializer(Long.class, ToStringSerializer.instance);
        module.addSerializer(Long.TYPE, ToStringSerializer.instance);
        // 把模块注册到 ObjectMapper
        objectMapper.registerModule(module);
        return objectMapper;
    }
}
