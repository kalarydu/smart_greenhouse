package com.greenhouse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 作物图像分类结果实体
 */
@Data
@TableName("gh_classification_result")
public class ClassificationResult {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 大棚标识：cotton / sunflower / strawberry */
    private String greenhouseKey;

    /** 大棚中文名 */
    private String greenhouseName;

    /** MinIO 对象路径 */
    private String objectName;

    /** 文件名 */
    private String fileName;

    /** 分类类别 ID (0-13) */
    private Integer classId;

    /** 中文类别名 */
    private String classNameCn;

    /** 英文类别名 */
    private String classNameEn;

    /** 置信度 [0, 1] */
    private Double confidence;

    /** 推理耗时（毫秒） */
    private Long elapsedMs;

    /** 图片大小（字节） */
    private Long imageSize;

    /** 分类时间 */
    private LocalDateTime createTime;
}
