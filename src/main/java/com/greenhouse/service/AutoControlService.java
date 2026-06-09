package com.greenhouse.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.greenhouse.config.AutoControlConfig;
import com.greenhouse.entity.Device;
import com.greenhouse.entity.SensorData;
import com.greenhouse.mqtt.MqttSendUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备自动控制服务
 * 在传感器数据入库后调用，根据阈值自动开关风机和补光灯，
 * 并通过 MQTT 下发指令到设备（与手动控制走同一通道）
 */
@Slf4j
@Service
public class AutoControlService {

    private final AutoControlConfig config;
    private final DeviceService deviceService;
    private final MqttSendUtil mqttSendUtil;

    public AutoControlService(AutoControlConfig config,
                              DeviceService deviceService,
                              MqttSendUtil mqttSendUtil) {
        this.config = config;
        this.deviceService = deviceService;
        this.mqttSendUtil = mqttSendUtil;
    }

    /**
     * 检测传感器数据，自动控制设备
     *
     * @param data 刚刚入库的传感器数据
     */
    public void checkAndControl(SensorData data) {
        if (!config.isEnabled()) {
            return;
        }

        Long greenhouseId = data.getGreenhouseId();
        if (greenhouseId == null) {
            log.debug("传感器数据缺少 greenhouseId，跳过自动控制检测");
            return;
        }

        AutoControlConfig.Thresholds t = config.getThresholds();

        // 1. 温度过高 → 自动开启风机
        if (data.getTemperature() != null && data.getTemperature() > t.getTemperatureHigh()) {
            controlDeviceByType(greenhouseId, "FAN", true,
                    String.format("温度过高自动触发：当前 %.1f ℃，阈值 > %.1f ℃",
                            data.getTemperature(), t.getTemperatureHigh()));
        }

        // 2. 光照不足 → 自动开启补光灯
        if (data.getLightIntensity() != null && data.getLightIntensity() < t.getLightLow()) {
            controlDeviceByType(greenhouseId, "LIGHT", true,
                    String.format("光照不足自动触发：当前 %.0f Lux，阈值 < %.0f Lux",
                            data.getLightIntensity(), t.getLightLow()));
        }
    }

    /**
     * 控制指定大棚中指定类型的所有设备
     *
     * @param greenhouseId 大棚ID
     * @param deviceType   设备类型（FAN / LIGHT）
     * @param turnOn       true-开启，false-关闭
     * @param reason       操作原因（日志用）
     */
    private void controlDeviceByType(Long greenhouseId, String deviceType,
                                     boolean turnOn, String reason) {
        // 查询该大棚下该类型的所有设备
        List<Device> devices = deviceService.list(
                new LambdaQueryWrapper<Device>()
                        .eq(Device::getGreenhouseId, greenhouseId)
                        .eq(Device::getDeviceType, deviceType)
        );

        if (devices.isEmpty()) {
            log.debug("大棚 {} 下无 {} 类型设备，跳过自动控制", greenhouseId, deviceType);
            return;
        }

        int targetStatus = turnOn ? 1 : 0;

        for (Device device : devices) {
            // 已经是目标状态，跳过
            if (device.getStatus() != null && device.getStatus() == targetStatus) {
                log.debug("设备 {} 已处于目标状态，跳过", device.getDeviceName());
                continue;
            }

            // 冷却检查：距离上次更新不足冷却时间则跳过，避免频繁开关
            if (device.getUpdateTime() != null) {
                LocalDateTime cooldownCutoff = LocalDateTime.now().minusMinutes(config.getCooldownMinutes());
                if (device.getUpdateTime().isAfter(cooldownCutoff)) {
                    log.info("设备 {} 处于冷却期（{}分钟内操作过），跳过自动控制",
                            device.getDeviceName(), config.getCooldownMinutes());
                    continue;
                }
            }

            // 更新设备状态
            device.setStatus(targetStatus);
            deviceService.updateById(device);

            // 构建 MQTT 指令（与手动 toggle 完全一致）
            String state = turnOn ? "ON" : "OFF";
            String commandName;
            String parasJson;
            switch (deviceType) {
                case "FAN":
                    commandName = "电机控制";
                    parasJson = "{\"motor\":\"" + state + "\"}";
                    break;
                case "LIGHT":
                    commandName = "紫光灯控制";
                    parasJson = "{\"light\":\"" + state + "\"}";
                    break;
                default:
                    commandName = deviceType + "控制";
                    parasJson = "{\"switch\":\"" + state + "\"}";
            }

            // 下发 MQTT 指令到设备
            mqttSendUtil.send(commandName, parasJson);

            log.info("自动控制 → 大棚:{} 设备:{} 操作:{} 原因:{}",
                    greenhouseId, device.getDeviceName(), state, reason);
        }
    }
}
