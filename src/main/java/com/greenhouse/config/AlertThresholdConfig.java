package com.greenhouse.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 报警阈值配置（可在 application.yaml 中按需调整）
 */
@Data
@Component
@ConfigurationProperties(prefix = "greenhouse.alert")
public class AlertThresholdConfig {

    /** 是否启用自动报警检测 */
    private boolean enabled = true;

    /** 同一大棚+同一报警类型去重窗口（小时），窗口内已有未处理报警则跳过 */
    private int dedupHours = 2;

    /** 各传感器指标阈值 */
    private Thresholds thresholds = new Thresholds();

    @Data
    public static class Thresholds {
        /** 温度下限（℃），低于此值 → TEMP_LOW */
        private double temperatureLow = 15.0;
        /** 温度上限（℃），高于此值 → TEMP_HIGH */
        private double temperatureHigh = 35.0;

        /** 空气湿度下限（%），低于此值 → HUMIDITY_LOW */
        private double humidityLow = 40.0;
        /** 空气湿度上限（%），高于此值 → HUMIDITY_HIGH */
        private double humidityHigh = 90.0;

        /** CO2浓度下限（ppm），低于此值 → CO2_LOW */
        private double co2Low = 300.0;
        /** CO2浓度上限（ppm），高于此值 → CO2_HIGH */
        private double co2High = 1000.0;

        /** 光照强度下限（Lux），低于此值 → LIGHT_LOW */
        private double lightLow = 5000.0;

        /** 土壤湿度下限（%），低于此值 → SOIL_MOISTURE_LOW */
        private double soilMoistureLow = 30.0;
        /** 土壤湿度上限（%），高于此值 → SOIL_MOISTURE_HIGH */
        private double soilMoistureHigh = 80.0;

        /** 土壤pH下限，低于此值 → SOIL_PH_LOW */
        private double soilPhLow = 5.5;
        /** 土壤pH上限，高于此值 → SOIL_PH_HIGH */
        private double soilPhHigh = 7.5;
    }
}
