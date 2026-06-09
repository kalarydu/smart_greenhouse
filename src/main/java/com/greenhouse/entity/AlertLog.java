package com.greenhouse.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 报警记录表
 */
@Data
@TableName("gh_alert_log")
public class AlertLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属大棚ID */
    private Long greenhouseId;

    /**
     * 报警类型（由系统根据阈值自动检测生成）：
     * TEMP_HIGH - 温度过高
     * TEMP_LOW - 温度过低
     * HUMIDITY_HIGH - 湿度过高
     * HUMIDITY_LOW - 湿度过低
     * CO2_HIGH - CO2浓度过高
     * CO2_LOW - CO2浓度过低
     * LIGHT_LOW - 光照不足
     * SOIL_MOISTURE_HIGH - 土壤湿度过高
     * SOIL_MOISTURE_LOW - 土壤湿度过低
     * SOIL_PH_HIGH - 土壤pH过高
     * SOIL_PH_LOW - 土壤pH过低
     */
    private String alertType;

    /** 报警内容 */
    private String message;

    /** 状态：0-未处理，1-已处理 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 处理时间 */
    private LocalDateTime handleTime;

    @TableLogic
    private Integer deleted;
}
