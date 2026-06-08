package com.greenhouse.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 传感器数据记录表
 */
@Data
@TableName("gh_sensor_data")
public class SensorData {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属大棚ID */
    private Long greenhouseId;

    /** 温度（℃） */
    private Double temperature;

    /** 空气湿度（%） */
    private Double humidity;

    /** 光照强度（Lux） */
    private Double lightIntensity;

    /** CO2浓度（ppm） */
    private Double co2Concentration;

    /** 土壤湿度（%） */
    private Double soilMoisture;

    /** 土壤pH值 */
    private Double soilPh;

    /** 记录时间 */
    private LocalDateTime recordTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
