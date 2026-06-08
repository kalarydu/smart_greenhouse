package com.greenhouse.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "spring.mqtt")
public class MqttConfig {

    private String host;
    private Integer port;
    private String accessKey;
    private String accessCode;
    private String instanceId;
    private String subscribeTopic;
    private String subscribeTopicDown;
    private int qos;

    // ===== 应用端下发指令 =====
    private String regionId;
    private String ak;
    private String sk;
    private String projectId;
    private String deviceId;
    private String commandTopic;
}
