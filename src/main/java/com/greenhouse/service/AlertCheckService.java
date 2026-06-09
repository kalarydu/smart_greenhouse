package com.greenhouse.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.greenhouse.config.AlertThresholdConfig;
import com.greenhouse.entity.AlertLog;
import com.greenhouse.entity.SensorData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 报警自动检测服务
 * 在传感器数据入库后调用，检测各项指标是否超出阈值，自动生成报警记录
 */
@Slf4j
@Service
public class AlertCheckService {

    private final AlertThresholdConfig config;
    private final AlertLogService alertLogService;

    public AlertCheckService(AlertThresholdConfig config, AlertLogService alertLogService) {
        this.config = config;
        this.alertLogService = alertLogService;
    }

    /**
     * 检测传感器数据并自动创建报警
     *
     * @param data 刚刚入库的传感器数据
     */
    public void checkAndCreateAlerts(SensorData data) {
        if (!config.isEnabled()) {
            return;
        }
        if (data.getGreenhouseId() == null) {
            log.warn("传感器数据缺少 greenhouseId，跳过报警检测");
            return;
        }

        AlertThresholdConfig.Thresholds t = config.getThresholds();
        List<AlertLog> newAlerts = new ArrayList<>();

        // 逐项检测
        check(data.getTemperature(), t.getTemperatureLow(), t.getTemperatureHigh(),
                "TEMP_LOW", "TEMP_HIGH", "温度", "℃", data, newAlerts);
        check(data.getHumidity(), t.getHumidityLow(), t.getHumidityHigh(),
                "HUMIDITY_LOW", "HUMIDITY_HIGH", "空气湿度", "%", data, newAlerts);
        check(data.getCo2Concentration(), t.getCo2Low(), t.getCo2High(),
                "CO2_LOW", "CO2_HIGH", "CO2浓度", "ppm", data, newAlerts);
        checkLowOnly(data.getLightIntensity(), t.getLightLow(),
                "LIGHT_LOW", "光照强度", "Lux", data, newAlerts);
        check(data.getSoilMoisture(), t.getSoilMoistureLow(), t.getSoilMoistureHigh(),
                "SOIL_MOISTURE_LOW", "SOIL_MOISTURE_HIGH", "土壤湿度", "%", data, newAlerts);
        check(data.getSoilPh(), t.getSoilPhLow(), t.getSoilPhHigh(),
                "SOIL_PH_LOW", "SOIL_PH_HIGH", "土壤pH", "", data, newAlerts);

        // 批量保存
        for (AlertLog alert : newAlerts) {
            try {
                alertLogService.save(alert);
                log.info("自动报警已生成 → 大棚:{} 类型:{} 内容:{}",
                        alert.getGreenhouseId(), alert.getAlertType(), alert.getMessage());
            } catch (Exception e) {
                log.error("报警入库失败", e);
            }
        }
    }

    /**
     * 检测双阈值指标（同时有上下限）
     */
    private void check(Double value, double low, double high,
                       String lowType, String highType,
                       String label, String unit,
                       SensorData data, List<AlertLog> alerts) {
        if (value == null) return;

        if (value < low) {
            String msg = String.format("%s过低：当前 %.1f %s，阈值 < %.1f %s",
                    label, value, unit, low, unit);
            tryCreateAlert(data, lowType, msg, alerts);
        } else if (value > high) {
            String msg = String.format("%s过高：当前 %.1f %s，阈值 > %.1f %s",
                    label, value, unit, high, unit);
            tryCreateAlert(data, highType, msg, alerts);
        }
    }

    /**
     * 检测仅下限指标（如光照只需检测过低）
     */
    private void checkLowOnly(Double value, double low,
                              String lowType, String label, String unit,
                              SensorData data, List<AlertLog> alerts) {
        if (value == null) return;

        if (value < low) {
            String msg = String.format("%s不足：当前 %.1f %s，阈值 < %.1f %s",
                    label, value, unit, low, unit);
            tryCreateAlert(data, lowType, msg, alerts);
        }
    }

    /**
     * 去重后创建报警记录
     */
    private void tryCreateAlert(SensorData data, String alertType,
                                String message, List<AlertLog> alerts) {
        // 去重：查询同一大棚 + 同一报警类型 + 未处理 + dedupHours 内已有记录
        LocalDateTime cutoff = LocalDateTime.now().minusHours(config.getDedupHours());
        long count = alertLogService.count(new LambdaQueryWrapper<AlertLog>()
                .eq(AlertLog::getGreenhouseId, data.getGreenhouseId())
                .eq(AlertLog::getAlertType, alertType)
                .eq(AlertLog::getStatus, 0)
                .ge(AlertLog::getCreateTime, cutoff));

        if (count > 0) {
            log.debug("报警去重跳过 → 大棚:{} 类型:{}（{}小时内已有未处理报警）",
                    data.getGreenhouseId(), alertType, config.getDedupHours());
            return;
        }

        AlertLog alert = new AlertLog();
        alert.setGreenhouseId(data.getGreenhouseId());
        alert.setAlertType(alertType);
        alert.setMessage(message);
        alert.setStatus(0);
        alerts.add(alert);
    }
}
