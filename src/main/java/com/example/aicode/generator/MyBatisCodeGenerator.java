package com.example.aicode.generator;

import cn.hutool.core.lang.Dict;
import cn.hutool.setting.yaml.YamlUtil;
import com.mybatisflex.codegen.Generator;
import com.mybatisflex.codegen.config.GlobalConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.util.Map;

// ============================================================
// 什么是"代码生成器"？（请先读这里）
// ------------------------------------------------------------
// 写 CRUD 时，Entity / Mapper / Service / Controller 大量是重复样板代码。
// 代码生成器连接数据库、读取表结构，按模板一次性生成这些文件，省去手工编写。
// 本类是"一次性工具"，不是应用运行时的一部分——开发者手动跑 main() 生成代码后，
// 把生成结果拷进项目即可，运行时并不需要它。
// 注意：basePackage 被故意设为别的包名，就是为了生成后人工确认再移动，避免覆盖现有代码。
// ============================================================

/**
 * MyBatis-Flex 代码生成器（开发期工具，非运行时组件）。
 *
 * <p>读取 {@code application.yml} 中的数据源配置，自动生成 Entity、Mapper、Service、Controller 等各层代码。
 * 运行入口为 {@link #main(String[])}，直接执行即可生成代码。</p>
 *
 * @author <a>程序员yaj</a>
 */
public class MyBatisCodeGenerator {

    /** 需要生成的表名数组（只生成这里列出的表，避免误生成全库） */
    private static final String[] TABLE_NAMES = {"app"};

    /**
     * 代码生成入口方法。
     *
     * <p>从配置文件读取数据源信息并初始化 Hikari 数据源，
     * 结合全局配置创建代码生成器并执行代码生成。</p>
     *
     * @param args 启动参数（未使用）
     */
    public static void main(String[] args) {
        // 获取数据源信息：从 application.yml 读取数据库连接配置
        Dict dict = YamlUtil.loadByPath("application.yml");
        Map<String, Object> dataSourceConfig = dict.getByPath("spring.datasource");
        String url = String.valueOf(dataSourceConfig.get("url"));
        String username = String.valueOf(dataSourceConfig.get("username"));
        String password = String.valueOf(dataSourceConfig.get("password"));
        // 配置数据源：使用 Hikari 连接池
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);

        // 创建配置内容
        GlobalConfig globalConfig = createGlobalConfig();

        // 通过 datasource 和 globalConfig 创建代码生成器
        Generator generator = new Generator(dataSource, globalConfig);

        // 生成代码
        generator.generate();
    }

    /**
     * 创建代码生成器的全局配置。
     *
     * <p>配置生成代码的根包、表策略、各层开关（Entity、Mapper、Service、Controller）、
     * 以及 Javadoc 的作者和版本信息等。</p>
     *
     * @return 代码生成的全局配置
     */
    // 详细配置见：https://mybatis-flex.com/zh/others/codegen.html
    public static GlobalConfig createGlobalConfig() {
        // 创建配置内容
        GlobalConfig globalConfig = new GlobalConfig();

        // 设置根包，建议先生成到一个临时目录下，生成代码后，再移动到项目目录下
        globalConfig.getPackageConfig()
                .setBasePackage("com.example.yuaicodemother.genresult");

        // 设置表前缀和只生成哪些表，setGenerateTable 未配置时，生成所有表
        globalConfig.getStrategyConfig()
                .setGenerateTable(TABLE_NAMES)
                // 设置逻辑删除的默认字段名称
                .setLogicDeleteColumn("isDelete");

        // 设置生成 entity 并启用 Lombok
        globalConfig.enableEntity()
                .setWithLombok(true)
                .setJdkVersion(21);

        // 设置生成 mapper
        globalConfig.enableMapper();
        globalConfig.enableMapperXml();

        // 设置生成 service
        globalConfig.enableService();
        globalConfig.enableServiceImpl();

        // 设置生成 controller
        globalConfig.enableController();

        // 设置生成时间和字符串为空，避免多余的代码改动
        globalConfig.getJavadocConfig()
                .setAuthor("<a>程序员yaj</a>")
                .setSince("");
        return globalConfig;
    }
}
