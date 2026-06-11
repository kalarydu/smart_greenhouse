package com.greenhouse.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MinIO 对象存储配置
 * 绑定 application.yaml 中 minio.* 的配置项
 */
@Data
@Component
@ConfigurationProperties(prefix = "minio")
public class MinioConfig {

    /** MinIO 端点列表，按顺序尝试连接 */
    private List<String> endpoints;

    /** 访问密钥 */
    private String accessKey;

    /** 密钥 */
    private String secretKey;

    /** 是否使用 TLS（https），默认 false */
    private boolean secure = false;

    /** 默认 bucket */
    private String defaultBucket;
}
