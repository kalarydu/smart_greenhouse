package com.greenhouse.mqtt;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.greenhouse.config.GreenhouseConfig;
import com.greenhouse.config.MqttConfig;
import com.greenhouse.entity.SensorData;
import com.greenhouse.service.AlertCheckService;
import com.greenhouse.service.AutoControlService;
import com.greenhouse.service.SensorDataMergeService;
import com.greenhouse.service.SensorDataService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class MqttMessageListener implements MqttCallbackExtended {

    private final MqttConfig config;
    private final GreenhouseConfig greenhouseConfig;
    private final SensorDataService sensorDataService;
    private final SensorDataMergeService sensorDataMergeService;
    private final AlertCheckService alertCheckService;
    private final AutoControlService autoControlService;
    private MqttAsyncClient mqttClient;

    /** 每个大棚上一次落库时间（内存缓存，重启后重置无影响） */
    private final java.util.Map<Long, java.time.LocalDateTime> lastSaveTimeMap = new java.util.concurrent.ConcurrentHashMap<>();

    public MqttMessageListener(MqttConfig config, GreenhouseConfig greenhouseConfig,
                               SensorDataService sensorDataService,
                               SensorDataMergeService sensorDataMergeService,
                               AlertCheckService alertCheckService,
                               AutoControlService autoControlService) {
        this.config = config;
        this.greenhouseConfig = greenhouseConfig;
        this.sensorDataService = sensorDataService;
        this.sensorDataMergeService = sensorDataMergeService;
        this.alertCheckService = alertCheckService;
        this.autoControlService = autoControlService;
    }

    @PostConstruct
    public void init() {
        connect();
    }

    private void connect() {
        try {
            String serverUri = "ssl://" + config.getHost() + ":" + config.getPort();
            String userName = "accessKey=" + config.getAccessKey()
                    + "|timestamp=" + System.currentTimeMillis()
                    + "|instanceId=" + config.getInstanceId();

            mqttClient = new MqttAsyncClient(serverUri, userName, new MemoryPersistence());

            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setAutomaticReconnect(false);
            options.setConnectionTimeout(60);
            options.setKeepAliveInterval(120);
            options.setUserName(userName);
            options.setPassword(config.getAccessCode().toCharArray());
            options.setHttpsHostnameVerificationEnabled(false);

            mqttClient.setCallback(this);
            mqttClient.connect(options, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken token) {
                    log.info("MQTT 连接成功: {}", serverUri);
                    subscribe();
                }

                @Override
                public void onFailure(IMqttToken token, Throwable throwable) {
                    log.error("MQTT 连接失败: {}", throwable.toString());
                    scheduleReconnect();
                }
            });
        } catch (Exception e) {
            log.error("MQTT 启动失败，10秒后重试", e);
            scheduleReconnect();
        }
    }

    private void subscribe() {
        try {
            // 订阅设备消息上行主题 (msg/up)
            mqttClient.subscribe(config.getSubscribeTopic(), config.getQos(), null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken token) {
                    log.info("已订阅主题: {}", config.getSubscribeTopic());
                }

                @Override
                public void onFailure(IMqttToken token, Throwable throwable) {
                    log.error("订阅主题失败: {}", throwable.toString());
                }
            });

            // 订阅设备属性上报转发主题 (/message/down)
            if (config.getSubscribeTopicDown() != null && !config.getSubscribeTopicDown().isEmpty()) {
                mqttClient.subscribe(config.getSubscribeTopicDown(), config.getQos(), null, new IMqttActionListener() {
                    @Override
                    public void onSuccess(IMqttToken token) {
                        log.info("已订阅主题: {}", config.getSubscribeTopicDown());
                    }

                    @Override
                    public void onFailure(IMqttToken token, Throwable throwable) {
                        log.error("订阅主题失败: {}", throwable.toString());
                    }
                });
            }
        } catch (MqttException e) {
            log.error("订阅异常", e);
        }
    }

    private void scheduleReconnect() {
        new Thread(() -> {
            try {
                Thread.sleep(10_000);
                connect();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        log.info("MQTT 连接完成, reconnect={}", reconnect);
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("MQTT 连接断开，将自动重连...", cause);
        scheduleReconnect();
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
        log.info("收到消息 topic: {}", topic);
        log.info("消息内容: {}", payload);

        try {
            SensorData data = null;

            // 根据主题判断消息类型
            if (topic.equals(config.getSubscribeTopicDown())) {
                // /message/down → 设备属性上报（通过IoTDA规则引擎转发）
                log.info("识别为属性上报消息，开始解析...");
                data = parsePropertyReport(payload);
            } else {
                // msg/up → 设备消息上行（MQTTX或设备直接发送）
                log.info("识别为设备上行消息，开始解析...");
                data = parseSensorData(payload);
            }

            if (data == null) {
                log.warn("消息解析失败，返回null。topic={} payload前200字符={}",
                        topic, payload.length() > 200 ? payload.substring(0, 200) : payload);
                return;
            }
            // 增量合并：缺失字段用历史值填充，保证每次都是完整 7 字段
            data = sensorDataMergeService.merge(data);

            // 报警检测 + 自动控制：每条数据都实时检测，不受落库间隔影响
            alertCheckService.checkAndCreateAlerts(data);
            autoControlService.checkAndControl(data);

            // 传感器数据落库：按间隔（默认5分钟）瘦身，节省存储空间
            Long gid = data.getGreenhouseId();
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            java.time.LocalDateTime lastSave = lastSaveTimeMap.get(gid);
            int interval = greenhouseConfig.getSaveIntervalMinutes();
            if (lastSave == null || lastSave.plusMinutes(interval).isBefore(now)) {
                sensorDataService.save(data);
                lastSaveTimeMap.put(gid, now);
                log.info("传感器数据已入库 → 大棚:{} 温度:{}℃ 湿度:{}% 光照:{}Lux CO2:{}ppm 土壤湿度:{}% pH:{}",
                        gid, data.getTemperature(),
                        data.getHumidity(), data.getLightIntensity(),
                        data.getCo2Concentration(), data.getSoilMoisture(),
                        data.getSoilPh());
            } else {
                log.debug("传感器数据跳过保存（{}分钟间隔未到）→ 大棚:{} 温度:{}℃",
                        interval, gid, data.getTemperature());
            }
        } catch (Exception e) {
            log.error("消息处理失败", e);
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
    }

    private SensorData parseSensorData(String payload) {
        JSONObject json = JSON.parseObject(payload);

        // 检测是否为IoTDA规则转发格式（notify_data.body.services）
        if (json.containsKey("notify_data")) {
            JSONObject notifyBody = json.getJSONObject("notify_data")
                                        .getJSONObject("body");
            // 转发格式 → 交给 parsePropertyReport 处理
            if (notifyBody != null && notifyBody.containsKey("services")) {
                log.info("检测到IoTDA转发格式，使用属性上报解析器");
                return parsePropertyReport(payload);
            }
            // 旧格式：notify_data.body.content → 提取content层
            json = notifyBody.getJSONObject("content");
        }

        SensorData data = new SensorData();
        if (json.containsKey("greenhouseId")) {
            data.setGreenhouseId(json.getLong("greenhouseId"));
        }
        if (json.containsKey("temperature")) {
            data.setTemperature(json.getDouble("temperature"));
        }
        if (json.containsKey("humidity")) {
            data.setHumidity(json.getDouble("humidity"));
        }
        if (json.containsKey("lightIntensity")) {
            data.setLightIntensity(json.getDouble("lightIntensity"));
        }
        if (json.containsKey("co2Concentration")) {
            data.setCo2Concentration(json.getDouble("co2Concentration"));
        }
        if (json.containsKey("soilMoisture")) {
            data.setSoilMoisture(json.getDouble("soilMoisture"));
        }
        if (json.containsKey("soilPh")) {
            data.setSoilPh(json.getDouble("soilPh"));
        }
        if (json.containsKey("recordTime")) {
            String timeStr = json.getString("recordTime");
            if (timeStr != null && !timeStr.isEmpty()) {
                data.setRecordTime(java.time.LocalDateTime.parse(timeStr));
            }
        }
        return data;
    }

    /**
     * 解析设备属性上报消息 (/message/down)
     * IoTDA 属性上报格式：
     * {
     *   "notify_data": {
     *     "body": {
     *       "services": [{
     *         "service_id": "智慧农业",
     *         "event_time": "2026-06-08T10:30:00Z",
     *         "properties": {
     *           "Luminance": 576.67,
     *           "temperature": 29.13,
     *           "humidity": 56.12,
     *           "soilMoisture": 35.5,
     *           "co2Concentration": 420.0,
     *           "soilPh": 6.8
     *         }
     *       }]
     *     }
     *   }
     * }
     */
    private SensorData parsePropertyReport(String payload) {
        JSONObject json = JSON.parseObject(payload);

        // 提取 notify_data.body
        if (!json.containsKey("notify_data")) {
            log.warn("属性上报缺少 notify_data 字段。完整消息: {}", payload.length() > 300 ? payload.substring(0, 300) : payload);
            return null;
        }
        JSONObject body = json.getJSONObject("notify_data")
                              .getJSONObject("body");
        if (body == null) {
            log.warn("属性上报 notify_data.body 为空");
            return null;
        }

        // 遍历 services 数组，记录所有遇到的 service_id
        if (!body.containsKey("services")) {
            log.warn("属性上报缺少 services 字段。body内容: {}", body.toJSONString());
            return null;
        }
        var services = body.getJSONArray("services");
        if (services == null || services.isEmpty()) {
            log.warn("属性上报 services 为空");
            return null;
        }

        SensorData data = new SensorData();

        for (int i = 0; i < services.size(); i++) {
            JSONObject service = services.getJSONObject(i);
            String serviceId = service.getString("service_id");
            log.info("遍历service: service_id={}", serviceId);

            // 跳过的服务：占位符、设备状态服务等非传感器数据
            if (serviceId == null || "string".equals(serviceId)
                    || serviceId.contains("设备") || serviceId.equals("Device")) {
                log.info("跳过非传感器服务: service_id={}", serviceId);
                continue;
            }

            // 解析传感器真实采集时间（IoTDA event_time），优先于服务器时间
            if (data.getRecordTime() == null && service.containsKey("event_time")) {
                String eventTime = service.getString("event_time");
                if (eventTime != null && !eventTime.isEmpty()) {
                    // IoTDA 上报的时间是 UTC（带Z后缀），需要转换为北京时间（UTC+8）
                    try {
                        if (eventTime.contains("-")) {
                            // ISO 格式: "2026-06-08T10:30:00Z"
                            java.time.ZonedDateTime zdt = java.time.ZonedDateTime.parse(eventTime);
                            // 转为北京时间
                            data.setRecordTime(zdt.withZoneSameInstant(
                                    java.time.ZoneId.of("Asia/Shanghai")).toLocalDateTime());
                        } else {
                            // 紧凑格式: "20260608T103000Z"
                            String normalized = eventTime.toUpperCase();
                            java.time.LocalDateTime utc = java.time.LocalDateTime.parse(
                                    normalized.replace("Z", ""),
                                    java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"));
                            // UTC → 北京时间（+8小时）
                            data.setRecordTime(utc.plusHours(8));
                        }
                    } catch (Exception e) {
                        log.warn("解析 event_time 失败: {}", eventTime, e);
                    }
                }
            }

            JSONObject properties = service.getJSONObject("properties");
            if (properties == null) {
                continue;
            }

            // 调试：打印所有属性键名和UTF-8字节
            log.info("属性键列表: {}", properties.keySet());
            for (String k : properties.keySet()) {
                byte[] bytes = k.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                StringBuilder hex = new StringBuilder();
                for (byte b : bytes) hex.append(String.format("%02X ", b));
                log.info("  键='{}' 字节=[{}]", k, hex.toString().trim());
            }

            // 设备属性 → SensorData 字段映射（兼容中英文键名）
            // 温度（℃）
            if (properties.containsKey("temperature")) {
                data.setTemperature(properties.getDouble("temperature"));
            } else if (properties.containsKey("温度")) {
                data.setTemperature(properties.getDouble("温度"));
            }
            // 空气湿度（%）
            if (properties.containsKey("humidity")) {
                data.setHumidity(properties.getDouble("humidity"));
            } else if (properties.containsKey("湿度")) {
                data.setHumidity(properties.getDouble("湿度"));
            }
            // 光照强度（Lux）：英文 Luminance / 中文 亮度 / 光照强度 / 光照
            if (properties.containsKey("Luminance")) {
                data.setLightIntensity(properties.getDouble("Luminance"));
            } else if (properties.containsKey("亮度")) {
                data.setLightIntensity(properties.getDouble("亮度"));
            } else if (properties.containsKey("光照强度")) {
                data.setLightIntensity(properties.getDouble("光照强度"));
            } else if (properties.containsKey("光照")) {
                data.setLightIntensity(properties.getDouble("光照"));
            }
            // 土壤湿度（%）
            if (properties.containsKey("soilMoisture")) {
                data.setSoilMoisture(properties.getDouble("soilMoisture"));
            } else if (properties.containsKey("土壤湿度")) {
                data.setSoilMoisture(properties.getDouble("土壤湿度"));
            }
            // CO2浓度（ppm）
            if (properties.containsKey("co2Concentration")) {
                data.setCo2Concentration(properties.getDouble("co2Concentration"));
            } else if (properties.containsKey("二氧化碳浓度")) {
                data.setCo2Concentration(properties.getDouble("二氧化碳浓度"));
            }
            // 土壤pH值
            if (properties.containsKey("soilPh")) {
                data.setSoilPh(properties.getDouble("soilPh"));
            } else if (properties.containsKey("土壤pH")) {
                data.setSoilPh(properties.getDouble("土壤pH"));
            }

            log.info("解析属性上报 → service:{} 温度:{} 湿度:{} 光照:{} 土壤湿度:{} CO2:{} pH:{}",
                    serviceId,
                    data.getTemperature() != null ? data.getTemperature() : "无",
                    data.getHumidity() != null ? data.getHumidity() : "无",
                    data.getLightIntensity() != null ? data.getLightIntensity() : "无",
                    data.getSoilMoisture() != null ? data.getSoilMoisture() : "无",
                    data.getCo2Concentration() != null ? data.getCo2Concentration() : "无",
                    data.getSoilPh() != null ? data.getSoilPh() : "无");
            break;
        }

        // 如果消息中没有 event_time，兜底使用服务器接收时间
        if (data.getRecordTime() == null) {
            data.setRecordTime(java.time.LocalDateTime.now());
        }

        // 没有解析到任何属性数据则跳过
        if (data.getTemperature() == null && data.getHumidity() == null
                && data.getLightIntensity() == null
                && data.getSoilMoisture() == null
                && data.getCo2Concentration() == null
                && data.getSoilPh() == null) {
            log.info("属性上报无传感器数据，跳过入库");
            return null;
        }

        return data;
    }

    @PreDestroy
    public void destroy() {
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.disconnect();
                log.info("MQTT 已断开连接");
            }
        } catch (Exception e) {
            log.error("MQTT 断开异常", e);
        }
    }
}
