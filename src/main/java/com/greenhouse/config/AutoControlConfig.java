package com.greenhouse.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 自动控制阈值配置（可在 application.yaml 中按需调整）
 * 当传感器数据超出阈值时，自动开关对应设备
 */
@Data
@Component
@ConfigurationProperties(prefix = "greenhouse.auto-control")
public class AutoControlConfig {

    /** 是否启用自动控制 */
    private boolean enabled = true;

    /** 同一设备操作冷却时间（分钟），避免频繁开关 */
    private int cooldownMinutes = 5;

    /** 各设备控制阈值 */
    private Thresholds thresholds = new Thresholds();

    @Data
    public static class Thresholds {
        /** 温度上限（℃），高于此值 → 自动开启风机 */
        private double temperatureHigh = 30.0;

        /** 光照强度下限（Lux），低于此值 → 自动开启补光灯 */
        private double lightLow = 10000.0;

        /** 土壤湿度下限（%），低于此值 → 自动开启灌溉机 */
        private double soilMoistureLow = 30.0;
    }
}
