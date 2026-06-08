package com.greenhouse.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.greenhouse.config.DeepSeekConfig;
import com.greenhouse.entity.SensorData;
import com.greenhouse.entity.Greenhouse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DeepSeekService {

    private final DeepSeekConfig config;
    private final SensorDataService sensorDataService;
    private final GreenhouseService greenhouseService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public DeepSeekService(DeepSeekConfig config, SensorDataService sensorDataService,
                           GreenhouseService greenhouseService) {
        this.config = config;
        this.sensorDataService = sensorDataService;
        this.greenhouseService = greenhouseService;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    public String chat(String userMessage) {
        // 1. 获取所有大棚列表
        List<Long> allGhIds = greenhouseService.list().stream()
                .map(Greenhouse::getId)
                .collect(Collectors.toList());

        if (allGhIds.isEmpty()) {
            return "系统中还没有大棚数据，请先添加大棚。";
        }

        // 查询最近24小时所有大棚的传感器数据作为上下文
        java.time.LocalDateTime end = java.time.LocalDateTime.now();
        java.time.LocalDateTime start = end.minusHours(24);

        List<SensorData> recentData = sensorDataService.listByCondition(
                allGhIds, start, end);

        // 2. 构建传感器上下文文本
        String sensorContext = buildContext(recentData);

        // 3. 构建系统提示词（从配置文件读取，可随时修改）
        String systemPrompt = config.getSystemPrompt();
        if (systemPrompt == null || systemPrompt.isBlank()) {
            systemPrompt = "你是一个专业的智慧农业专家助手，负责分析温室大棚传感器数据。";
        }

        // 4. 调用 DeepSeek API
        return callApi(systemPrompt, userMessage, sensorContext);
    }

    private String buildContext(List<SensorData> dataList) {
        if (dataList.isEmpty()) {
            return "（当前没有任何传感器数据）";
        }

        // 按大棚分组统计
        var grouped = dataList.stream()
                .collect(Collectors.groupingBy(SensorData::getGreenhouseId));

        StringBuilder sb = new StringBuilder();
        sb.append("以下是最近24小时各大棚的传感器数据汇总：\n\n");

        for (var entry : grouped.entrySet()) {
            Long ghId = entry.getKey();
            List<SensorData> items = entry.getValue();

            // 计算统计值
            DoubleSummaryStatistics tempStats = items.stream()
                    .filter(d -> d.getTemperature() != null)
                    .mapToDouble(SensorData::getTemperature).summaryStatistics();
            DoubleSummaryStatistics humStats = items.stream()
                    .filter(d -> d.getHumidity() != null)
                    .mapToDouble(SensorData::getHumidity).summaryStatistics();
            DoubleSummaryStatistics co2Stats = items.stream()
                    .filter(d -> d.getCo2Concentration() != null)
                    .mapToDouble(SensorData::getCo2Concentration).summaryStatistics();
            DoubleSummaryStatistics soilStats = items.stream()
                    .filter(d -> d.getSoilMoisture() != null)
                    .mapToDouble(SensorData::getSoilMoisture).summaryStatistics();
            DoubleSummaryStatistics phStats = items.stream()
                    .filter(d -> d.getSoilPh() != null)
                    .mapToDouble(SensorData::getSoilPh).summaryStatistics();

            // 最新一条数据
            SensorData latest = items.get(items.size() - 1);

            sb.append(String.format("【大棚 #%d】数据条数：%d\n", ghId, items.size()));
            sb.append(String.format("  温度：最新 %.1f℃ | 范围 %.1f~%.1f℃ | 平均 %.1f℃\n",
                    latest.getTemperature(),
                    tempStats.getCount() > 0 ? tempStats.getMin() : 0,
                    tempStats.getCount() > 0 ? tempStats.getMax() : 0,
                    tempStats.getCount() > 0 ? tempStats.getAverage() : 0));
            sb.append(String.format("  空气湿度：最新 %.1f%% | 范围 %.1f~%.1f%% | 平均 %.1f%%\n",
                    latest.getHumidity(),
                    humStats.getCount() > 0 ? humStats.getMin() : 0,
                    humStats.getCount() > 0 ? humStats.getMax() : 0,
                    humStats.getCount() > 0 ? humStats.getAverage() : 0));
            sb.append(String.format("  CO2浓度：最新 %.0f ppm | 平均 %.0f ppm\n",
                    latest.getCo2Concentration(),
                    co2Stats.getCount() > 0 ? co2Stats.getAverage() : 0));
            sb.append(String.format("  土壤湿度：最新 %.1f%% | 范围 %.1f~%.1f%% | 平均 %.1f%%\n",
                    latest.getSoilMoisture(),
                    soilStats.getCount() > 0 ? soilStats.getMin() : 0,
                    soilStats.getCount() > 0 ? soilStats.getMax() : 0,
                    soilStats.getCount() > 0 ? soilStats.getAverage() : 0));
            sb.append(String.format("  土壤pH：最新 %.1f | 平均 %.1f\n",
                    latest.getSoilPh(),
                    phStats.getCount() > 0 ? phStats.getAverage() : 0));
            sb.append("\n");
        }

        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private String callApi(String systemPrompt, String userMessage, String context) {
        String url = config.getBaseUrl() + "/v1/chat/completions";

        // 构建请求体
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.getModel());

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content",
                "传感器数据如下：\n" + context + "\n用户问题：" + userMessage));
        body.put("messages", messages);
        body.put("temperature", 0.7);
        body.put("max_tokens", 1000);

        // 发送请求
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + config.getApiKey());

        try {
            String requestJson = objectMapper.writeValueAsString(body);
            log.info("发送 DeepSeek 请求，消息长度: {} chars", requestJson.length());

            HttpEntity<String> entity = new HttpEntity<>(requestJson, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            // 解析响应
            Map<String, Object> respMap = objectMapper.readValue(response.getBody(), Map.class);
            List<Map<String, Object>> choices = (List<Map<String, Object>>) respMap.get("choices");
            if (choices == null || choices.isEmpty()) {
                return "AI 返回为空，请稍后重试。";
            }
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            return (String) message.get("content");
        } catch (Exception e) {
            log.error("DeepSeek API 调用失败", e);
            return "抱歉，AI 服务暂时不可用：" + e.getMessage();
        }
    }
}
