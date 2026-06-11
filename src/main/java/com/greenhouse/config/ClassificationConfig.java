package com.greenhouse.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 作物图像定时分类配置
 */
@Slf4j
@Data
@Component
@ConfigurationProperties(prefix = "greenhouse.classification")
public class ClassificationConfig {

    private boolean enabled = true;
    private int intervalSeconds = 30;
    private int batchSizePerGreenhouse = 3;
    private List<GreenhouseConfig> greenhouses = new ArrayList<>();

    @Data
    public static class GreenhouseConfig {
        /** 大棚标识，对应 MinIO 前缀分类：cotton / sunflower / strawberry */
        private String key;
        /** 大棚中文名 */
        private String name;
        /** MinIO 子前缀列表 */
        private List<String> prefixes;
    }

    @PostConstruct
    public void logConfig() {
        log.info("分类配置加载: enabled={}, interval={}s, batchSize={}, 大棚数={}",
                enabled, intervalSeconds, batchSizePerGreenhouse, greenhouses.size());
        greenhouses.forEach(gh ->
                log.info("  {}: name={}, prefixes={}", gh.getKey(), gh.getName(), gh.getPrefixes()));
    }
}
