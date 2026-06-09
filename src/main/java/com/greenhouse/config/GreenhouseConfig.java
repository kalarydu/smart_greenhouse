package com.greenhouse.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 大棚通用配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "greenhouse")
public class GreenhouseConfig {

    /** 硬件上报消息缺少 greenhouseId 时的默认大棚ID */
    private Long defaultId = 1L;

    /** 传感器数据落库间隔（分钟），报警和自动控制仍然每条都检测 */
    private int saveIntervalMinutes = 5;
}
