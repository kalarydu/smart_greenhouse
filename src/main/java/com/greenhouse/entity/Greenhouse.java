package com.greenhouse.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 大棚信息表
 */
@Data
@TableName("gh_greenhouse")
public class Greenhouse {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 大棚名称 */
    private String name;

    /** 位置 */
    private String location;

    /** 面积（平方米） */
    private Double area;

    /** 描述 */
    private String description;

    /** 状态：0-停用，1-运行中 */
    private Integer status;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除：0-未删除，1-已删除 */
    @TableLogic
    private Integer deleted;
}
