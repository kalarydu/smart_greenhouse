package com.greenhouse.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.greenhouse.config.GreenhouseConfig;
import com.greenhouse.entity.SensorData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Random;

/**
 * 传感器数据增量合并服务
 * <p>
 * 背景：小凌派硬件只上报温度、湿度、光照 3 个字段，
 * MQTTX 模拟上报 CO2、土壤湿度、土壤 pH 3 个字段。
 * 两路数据需要合并成同一条完整记录。
 * <p>
 * 策略：收到任意一路数据后，新值覆盖旧值，缺失字段用上一条记录的值填充，
 * 模拟字段在沿用旧值时加入小幅随机波动，保证图表数据看起来真实。
 */
@Slf4j
@Service
public class SensorDataMergeService {

    private final GreenhouseConfig greenhouseConfig;
    private final SensorDataService sensorDataService;
    private final Random random = new Random();

    /**
     * 首次启动时，模拟字段的合理初始值范围
     */
    private static final double DEFAULT_CO2 = 420.0;          // ppm，大气背景值
    private static final double DEFAULT_SOIL_MOISTURE = 55.0; // %，适宜湿度
    private static final double DEFAULT_SOIL_PH = 6.5;        // 中性偏酸

    public SensorDataMergeService(GreenhouseConfig greenhouseConfig,
                                  SensorDataService sensorDataService) {
        this.greenhouseConfig = greenhouseConfig;
        this.sensorDataService = sensorDataService;
    }

    /**
     * 合并传感器数据：新字段覆盖，缺失字段用历史值填充
     *
     * @param incoming 刚解析的传感器数据（字段可能不完整）
     * @return 合并后的完整数据（7 个传感字段全部有值）
     */
    public SensorData merge(SensorData incoming) {
        // 1. 补全 greenhouseId
        if (incoming.getGreenhouseId() == null) {
            incoming.setGreenhouseId(greenhouseConfig.getDefaultId());
        }

        // 2. 查该大棚最新一条数据
        SensorData last = sensorDataService.getOne(
                new LambdaQueryWrapper<SensorData>()
                        .eq(SensorData::getGreenhouseId, incoming.getGreenhouseId())
                        .orderByDesc(SensorData::getRecordTime)
                        .orderByDesc(SensorData::getId)
                        .last("LIMIT 1")
        );

        // 3. 逐字段合并：新值非 null 覆盖，null 则沿用旧值
        if (last != null) {
            mergeField(incoming, last);
        } else {
            // 数据库无历史记录（首次启动），为模拟字段生成合理初始值
            applyDefaults(incoming);
        }

        // 4. 记录时间兜底
        if (incoming.getRecordTime() == null) {
            incoming.setRecordTime(LocalDateTime.now());
        }

        log.debug("数据合并完成 → 大棚:{} 温度:{} 湿度:{} 光照:{} CO2:{} 土壤湿度:{} pH:{}",
                incoming.getGreenhouseId(), incoming.getTemperature(),
                incoming.getHumidity(), incoming.getLightIntensity(),
                incoming.getCo2Concentration(), incoming.getSoilMoisture(),
                incoming.getSoilPh());

        return incoming;
    }

    /**
     * 逐字段合并：新值覆盖旧值，新值为 null 则沿用旧值
     */
    private void mergeField(SensorData incoming, SensorData last) {
        if (incoming.getTemperature() != null) {
            // 硬件字段：直接覆盖
        } else {
            incoming.setTemperature(last.getTemperature());
        }

        if (incoming.getHumidity() != null) {
        } else {
            incoming.setHumidity(last.getHumidity());
        }

        if (incoming.getLightIntensity() != null) {
        } else {
            incoming.setLightIntensity(last.getLightIntensity());
        }

        // ---- 模拟字段：有值直接用，无值则沿用旧值 + 小幅随机波动 ----
        incoming.setCo2Concentration(
                incoming.getCo2Concentration() != null
                        ? incoming.getCo2Concentration()
                        : jitter(last.getCo2Concentration(), 380, 480, 5)
        );

        incoming.setSoilMoisture(
                incoming.getSoilMoisture() != null
                        ? incoming.getSoilMoisture()
                        : jitter(last.getSoilMoisture(), 45, 65, 3)
        );

        incoming.setSoilPh(
                incoming.getSoilPh() != null
                        ? incoming.getSoilPh()
                        : jitter(last.getSoilPh(), 6.0, 7.0, 0.1)
        );
    }

    /**
     * 首次启动无历史数据，用合理默认值
     */
    private void applyDefaults(SensorData incoming) {
        if (incoming.getCo2Concentration() == null) {
            incoming.setCo2Concentration(DEFAULT_CO2);
        }
        if (incoming.getSoilMoisture() == null) {
            incoming.setSoilMoisture(DEFAULT_SOIL_MOISTURE);
        }
        if (incoming.getSoilPh() == null) {
            incoming.setSoilPh(DEFAULT_SOIL_PH);
        }
    }

    /**
     * 在旧值基础上小幅随机波动，并钳制在合理范围内
     *
     * @param oldValue  上一次的值
     * @param min       合理下限
     * @param max       合理上限
     * @param amplitude 波动幅度（正负）
     * @return 波动后的新值
     */
    private double jitter(Double oldValue, double min, double max, double amplitude) {
        if (oldValue == null) {
            // 无旧值，取范围中值
            return (min + max) / 2.0;
        }
        double delta = (random.nextDouble() * 2 - 1) * amplitude; // [-amplitude, +amplitude]
        double newVal = oldValue + delta;
        // 钳制到合理范围内
        if (newVal < min) newVal = min;
        if (newVal > max) newVal = max;
        return Math.round(newVal * 10.0) / 10.0; // 保留1位小数
    }
}
