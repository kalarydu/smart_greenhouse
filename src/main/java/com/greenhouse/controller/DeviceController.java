package com.greenhouse.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.greenhouse.common.Result;
import com.greenhouse.entity.Device;
import com.greenhouse.mqtt.MqttSendUtil;
import com.greenhouse.service.DeviceService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/device")
public class DeviceController {

    private final DeviceService deviceService;
    private final MqttSendUtil mqttSendUtil;

    public DeviceController(DeviceService deviceService, MqttSendUtil mqttSendUtil) {
        this.deviceService = deviceService;
        this.mqttSendUtil = mqttSendUtil;
    }

    /**
     * 分页查询设备列表（可按大棚ID或设备类型筛选）
     * GET /api/device/list?page=1&size=10&greenhouseId=1&deviceType=FAN
     */
    @GetMapping("/list")
    public Result<Page<Device>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Long greenhouseId,
            @RequestParam(required = false) String deviceType) {

        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
        if (greenhouseId != null) {
            wrapper.eq(Device::getGreenhouseId, greenhouseId);
        }
        if (deviceType != null && !deviceType.isEmpty()) {
            wrapper.eq(Device::getDeviceType, deviceType);
        }
        wrapper.orderByDesc(Device::getCreateTime);

        Page<Device> result = deviceService.page(new Page<>(page, size), wrapper);
        return Result.ok(result);
    }

    @GetMapping("/{id}")
    public Result<Device> getById(@PathVariable Long id) {
        Device device = deviceService.getById(id);
        return device != null ? Result.ok(device) : Result.fail("设备不存在");
    }

    @PostMapping
    public Result<?> add(@RequestBody Device device) {
        boolean saved = deviceService.save(device);
        return saved ? Result.ok("新增成功") : Result.fail("新增失败");
    }

    @PutMapping
    public Result<?> update(@RequestBody Device device) {
        boolean updated = deviceService.updateById(device);
        return updated ? Result.ok("修改成功") : Result.fail("修改失败");
    }

    /**
     * 切换设备开关状态，并下发指令到设备
     * PUT /api/device/1/toggle
     *
     * 设备类型 → 命令映射：
     *   FAN        → 电机控制   paras: {"motor": "ON"/"OFF"}
     *   LIGHT      → 紫光灯控制 paras: {"light": "ON"/"OFF"}
     *   IRRIGATION → 灌溉控制   paras: {"Irrigation": "ON"/"OFF"}
     */
    @PutMapping("/{id}/toggle")
    public Result<?> toggle(@PathVariable Long id) {
        Device device = deviceService.getById(id);
        if (device == null) {
            return Result.fail("设备不存在");
        }
        device.setStatus(device.getStatus() == 1 ? 0 : 1);
        deviceService.updateById(device);
        String state = device.getStatus() == 1 ? "ON" : "OFF";

        // 根据设备类型选择命令名和参数
        String commandName;
        String parasJson;
        switch (device.getDeviceType()) {
            case "FAN":
                commandName = "电机控制";
                parasJson = "{\"motor\":\"" + state + "\"}";
                break;
            case "LIGHT":
                commandName = "紫光灯控制";
                parasJson = "{\"light\":\"" + state + "\"}";
                break;
            case "IRRIGATION":
                commandName = "灌溉机控制";
                parasJson = "{\"Irrigation\":\"" + state + "\"}";
                break;
            default:
                commandName = device.getDeviceType() + "控制";
                parasJson = "{\"switch\":\"" + state + "\"}";
        }

        mqttSendUtil.send(commandName, parasJson);
        return Result.ok(device.getStatus() == 1 ? "设备已开启，指令已下发" : "设备已关闭，指令已下发");
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        boolean removed = deviceService.removeById(id);
        return removed ? Result.ok("删除成功") : Result.fail("删除失败");
    }
}
