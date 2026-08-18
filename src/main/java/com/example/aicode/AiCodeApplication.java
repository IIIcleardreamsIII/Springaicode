package com.example.aicode;

import dev.langchain4j.community.store.embedding.redis.spring.RedisEmbeddingStoreAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// ============================================================
// @SpringBootApplication 其实是"三合一"注解（新手重点）
// ------------------------------------------------------------
// ① @SpringBootConfiguration → 本质是 @Configuration，声明这是配置类
// ② @EnableAutoConfiguration  → 开启自动装配（按 classpath 依赖自动配好 Tomcat/数据库等）
// ③ @ComponentScan            → 扫描本类所在包及子包下所有 @Component/@Service/@Controller 等，
//    把它们注册进 Spring 容器（这也是为什么 Controller/Service 不用手动 new，直接 @Resource 注入）
// 此外 @MapperScan 专门扫描 Mapper 接口生成实现类（MyBatis 接口需单独扫，不属于普通组件）。
// ============================================================

/**
 * AI 代码项目启动类（Spring Boot 应用唯一入口）。
 *
 * @author AI Code
 * @version 1.0
 */
@SpringBootApplication(exclude = {RedisEmbeddingStoreAutoConfiguration.class})
@MapperScan("com.example.aicode.mapper") // 指定 Mapper 接口包，MyBatis 为其生成实现并注册
public class AiCodeApplication {

    /**
     * 应用程序入口方法，用于启动 Spring Boot 应用。
     *
     * <p>该方法通过 {@link SpringApplication#run} 委托给 Spring Boot 框架，
     * 完成应用上下文的初始化、自动配置加载以及内嵌 Tomcat 服务器的启动。</p>
     *
     * @param args 启动参数数组，可传递给 Spring Boot 以覆盖默认配置项（如 --server.port=8081）
     */
    public static void main(String[] args) {
        // 启动 Spring Boot 应用，传入当前启动类作为配置源以及命令行参数
        SpringApplication.run(AiCodeApplication.class, args);
        System.out.println("启动成功");
    }

}
