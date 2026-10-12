package com.example.aicode.model.dto.app;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 应用部署请求体（DTO：Data Transfer Object）。
 *
 * <p>该对象用于封装"部署应用"接口（{@code POST /app/deploy}）接收的请求参数，
 * 由 Spring 将 HTTP 请求体（JSON）反序列化得到。</p>
 *
 * @author 程序员yaj
 */
@Data
public class AppDeployRequest implements Serializable {

    /**
     * 要部署的应用 id
     */
    private Long appId;

    @Serial
    private static final long serialVersionUID = 1L;
}
