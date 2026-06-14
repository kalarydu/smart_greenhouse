package com.greenhouse.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备表
 */
@Data
@TableName("gh_device")
public class Device {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属大棚ID */
    private Long greenhouseId;

    /** 设备名称 */
    private String deviceName;

    /**
     * 设备类型：
     * FAN        - 风机
     * LIGHT      - 补光灯
     * IRRIGATION - 灌溉机
     */
    private String deviceType;

    /** 状态：0-关闭，1-开启 */
    private Integer status;

    /** 控制模式：AUTO-自动（根据阈值），MANUAL-手动（仅前端控制） */
    private String mode = "AUTO";

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
