package com.greenhouse.mqtt;

import com.greenhouse.config.MqttConfig;
import com.huaweicloud.sdk.core.auth.AbstractCredentials;
import com.huaweicloud.sdk.core.auth.BasicCredentials;
import com.huaweicloud.sdk.core.auth.ICredential;
import com.huaweicloud.sdk.core.region.Region;
import com.huaweicloud.sdk.iotda.v5.IoTDAClient;
import com.huaweicloud.sdk.iotda.v5.model.CreateMessageRequest;
import com.huaweicloud.sdk.iotda.v5.model.CreateMessageResponse;
import com.huaweicloud.sdk.iotda.v5.model.DeviceMessageRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MqttSendUtil {

    private final MqttConfig config;
    private final IoTDAClient client;

    public MqttSendUtil(MqttConfig config) {
        this.config = config;
        ICredential auth = new BasicCredentials()
                .withDerivedPredicate(AbstractCredentials.DEFAULT_DERIVED_PREDICATE)
                .withAk(config.getAk())
                .withSk(config.getSk())
                .withProjectId(config.getProjectId());
        this.client = IoTDAClient.newBuilder()
                .withCredential(auth)
                .withRegion(new Region(config.getRegionId(), config.getHost()))
                .build();
    }

    public void send(String commandName, String parasJson) {
        // 构建设备能识别的命令 JSON 格式:
        // {"service_id":"智慧农业","command_name":"电机控制","paras":{"motor":"ON"}}
        String payload = "{\"service_id\":\"智慧农业\",\"command_name\":\""
                + commandName + "\",\"paras\":" + parasJson + "}";

        CreateMessageRequest request = new CreateMessageRequest();
        request.withDeviceId(config.getDeviceId());
        DeviceMessageRequest body = new DeviceMessageRequest();
        body.withPayloadFormat("raw");
        body.withTopicFullName(config.getCommandTopic());
        body.withMessage(payload);
        request.withBody(body);
        try {
            CreateMessageResponse response = client.createMessage(request);
            log.info("指令已下发 → command:{} payload:{} messageId:{}",
                    commandName, payload, response.getMessageId());
        } catch (Exception e) {
            log.error("指令下发失败", e);
        }
    }
}
