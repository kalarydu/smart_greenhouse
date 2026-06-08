package com.greenhouse.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.greenhouse.common.Result;
import com.greenhouse.entity.Greenhouse;
import com.greenhouse.entity.SensorData;
import com.greenhouse.service.GreenhouseService;
import com.greenhouse.service.SensorDataService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/sensor-data")
public class SensorDataController {

    private final SensorDataService sensorDataService;
    private final GreenhouseService greenhouseService;

    public SensorDataController(SensorDataService sensorDataService, GreenhouseService greenhouseService) {
        this.sensorDataService = sensorDataService;
        this.greenhouseService = greenhouseService;
    }

    /**
     * 分页查询传感器数据（可按大棚ID筛选）
     * GET /api/sensor-data/list?page=1&size=10&greenhouseId=1
     */
    @GetMapping("/list")
    public Result<Page<SensorData>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Long greenhouseId) {

        LambdaQueryWrapper<SensorData> wrapper = new LambdaQueryWrapper<>();
        if (greenhouseId != null) {
            wrapper.eq(SensorData::getGreenhouseId, greenhouseId);
        }
        wrapper.orderByDesc(SensorData::getRecordTime)
               .orderByDesc(SensorData::getId);

        Page<SensorData> result = sensorDataService.page(new Page<>(page, size), wrapper);
        return Result.ok(result);
    }

    /**
     * 查询某大棚最新的传感器数据
     * GET /api/sensor-data/latest/1
     */
    @GetMapping("/latest/{greenhouseId}")
    public Result<SensorData> getLatest(@PathVariable Long greenhouseId) {
        LambdaQueryWrapper<SensorData> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SensorData::getGreenhouseId, greenhouseId)
               .orderByDesc(SensorData::getRecordTime)
               .last("LIMIT 1");
        SensorData data = sensorDataService.getOne(wrapper);
        return data != null ? Result.ok(data) : Result.fail("暂无数据");
    }

    /**
     * 图表数据接口：按指标、大棚、时间范围返回分组数据
     * GET /api/sensor-data/chart?metric=temperature&greenhouseIds=1,2,3&startTime=2026-06-05T00:00:00&endTime=2026-06-08T23:59:59
     */
    @GetMapping("/chart")
    public Result<?> chart(
            @RequestParam String metric,
            @RequestParam String greenhouseIds,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {

        // 1. 解析大棚ID列表
        List<Long> ids = Arrays.stream(greenhouseIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .collect(Collectors.toList());

        if (ids.isEmpty()) {
            return Result.fail("请选择至少一个大棚");
        }

        // 2. 解析时间范围
        LocalDateTime start = (startTime != null && !startTime.isEmpty())
                ? LocalDateTime.parse(startTime) : null;
        LocalDateTime end = (endTime != null && !endTime.isEmpty())
                ? LocalDateTime.parse(endTime) : null;

        // 3. 查询数据
        List<SensorData> rawList = sensorDataService.listByCondition(ids, start, end);

        // 4. 汇总所有出现的大棚ID（含查询列表中但没有数据的）
        Set<Long> queriedIds = new LinkedHashSet<>(ids);
        rawList.forEach(d -> queriedIds.add(d.getGreenhouseId()));

        // 查询大棚名称映射
        Map<Long, String> nameMap;
        List<Greenhouse> greenhouses = greenhouseService.listByIds(new ArrayList<>(queriedIds));
        nameMap = greenhouses.stream()
                .collect(Collectors.toMap(Greenhouse::getId, Greenhouse::getName, (a, b) -> a));

        // 5. 按 greenhouseId 分组，构建 series
        Map<Long, List<SensorData>> grouped = rawList.stream()
                .collect(Collectors.groupingBy(SensorData::getGreenhouseId));

        List<Map<String, Object>> series = new ArrayList<>();
        // 保持传入顺序
        for (Long gid : ids) {
            List<SensorData> items = grouped.getOrDefault(gid, Collections.emptyList());
            List<Map<String, Object>> points = items.stream().map(d -> {
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("recordTime", d.getRecordTime() != null ? d.getRecordTime().toString() : null);
                p.put("value", extractMetric(d, metric));
                return p;
            }).collect(Collectors.toList());

            Map<String, Object> s = new LinkedHashMap<>();
            s.put("greenhouseId", gid);
            s.put("greenhouseName", nameMap.getOrDefault(gid, "大棚#" + gid));
            s.put("dataPoints", points);
            series.add(s);
        }

        // 6. 指标标签映射
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("temperature", "温度 (℃)");
        labels.put("humidity", "空气湿度 (%)");
        labels.put("lightIntensity", "光照强度 (Lux)");
        labels.put("co2Concentration", "CO2浓度 (ppm)");
        labels.put("soilMoisture", "土壤湿度 (%)");
        labels.put("soilPh", "土壤pH值");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("metric", metric);
        data.put("metricLabel", labels.getOrDefault(metric, metric));
        data.put("series", series);

        return Result.ok(data);
    }

    private Object extractMetric(SensorData d, String metric) {
        switch (metric) {
            case "temperature": return d.getTemperature();
            case "humidity": return d.getHumidity();
            case "lightIntensity": return d.getLightIntensity();
            case "co2Concentration": return d.getCo2Concentration();
            case "soilMoisture": return d.getSoilMoisture();
            case "soilPh": return d.getSoilPh();
            default: return null;
        }
    }

    @GetMapping("/{id}")
    public Result<SensorData> getById(@PathVariable Long id) {
        SensorData data = sensorDataService.getById(id);
        return data != null ? Result.ok(data) : Result.fail("数据不存在");
    }

    @PostMapping
    public Result<?> add(@RequestBody SensorData sensorData) {
        boolean saved = sensorDataService.save(sensorData);
        return saved ? Result.ok("新增成功") : Result.fail("新增失败");
    }

    @PutMapping
    public Result<?> update(@RequestBody SensorData sensorData) {
        boolean updated = sensorDataService.updateById(sensorData);
        return updated ? Result.ok("修改成功") : Result.fail("修改失败");
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        boolean removed = sensorDataService.removeById(id);
        return removed ? Result.ok("删除成功") : Result.fail("删除失败");
    }
}
