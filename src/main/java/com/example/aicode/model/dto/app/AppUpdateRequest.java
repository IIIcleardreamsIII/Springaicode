package com.example.aicode.model.dto.app;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 应用更新请求体（DTO：Data Transfer Object），面向管理员。
 *
 * <p>该对象用于封装"管理员更新任意应用"接口（{@code POST /app/admin/update}）
 * 接收的请求参数，由 Spring 将 HTTP 请求体（JSON）反序列化得到。
 * 管理员支持更新应用名称、应用封面、优先级。</p>
 *
 * @author 程序员yaj
 */
@Data
public class AppUpdateRequest implements Serializable {

    /**
     * 应用 id（用于定位要更新的应用）
     */
    private Long id;

    /**
     * 应用名称
     */
    private String appName;

    /**
     * 应用封面 URL
     */
    private String cover;

    /**
     * 优先级（设置为 99 即精选应用，参考 {@link com.example.aicode.constant.AppConstant#GOOD_APP_PRIORITY}）
     */
    private Integer priority;

    @Serial
    private static final long serialVersionUID = 1L;
}
