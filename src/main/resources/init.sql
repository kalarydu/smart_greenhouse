-- =============================================
-- 智慧大棚管理系统 - 数据库初始化脚本
-- 使用方法：在 Navicat/IDEA/DBeaver 中执行此文件
-- =============================================

-- 1. 创建数据库（如果还不存在）
CREATE DATABASE IF NOT EXISTS greenhouse
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE greenhouse;

-- 2. 大棚信息表
CREATE TABLE IF NOT EXISTS gh_greenhouse (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    name        VARCHAR(100) NOT NULL              COMMENT '大棚名称',
    location    VARCHAR(200) DEFAULT ''            COMMENT '位置',
    area        DOUBLE       DEFAULT 0             COMMENT '面积（平方米）',
    description VARCHAR(500) DEFAULT ''            COMMENT '描述',
    status      TINYINT      DEFAULT 1             COMMENT '状态：0-停用，1-运行中',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      DEFAULT 0             COMMENT '逻辑删除：0-未删除，1-已删除',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='大棚信息表';

-- 3. 传感器数据表
CREATE TABLE IF NOT EXISTS gh_sensor_data (
    id               BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    greenhouse_id    BIGINT   NOT NULL              COMMENT '所属大棚ID',
    temperature      DOUBLE   DEFAULT 0             COMMENT '温度（℃）',
    humidity         DOUBLE   DEFAULT 0             COMMENT '空气湿度（%）',
    light_intensity  DOUBLE   DEFAULT 0             COMMENT '光照强度（Lux）',
    co2_concentration DOUBLE  DEFAULT 0             COMMENT 'CO2浓度（ppm）',
    soil_moisture    DOUBLE   DEFAULT 0             COMMENT '土壤湿度（%）',
    soil_ph          DOUBLE   DEFAULT 0             COMMENT '土壤pH值',
    record_time      DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
    create_time      DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    INDEX idx_greenhouse_id (greenhouse_id),
    INDEX idx_record_time (record_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='传感器数据记录表';

-- 4. 设备表
CREATE TABLE IF NOT EXISTS gh_device (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    greenhouse_id  BIGINT       NOT NULL              COMMENT '所属大棚ID',
    device_name    VARCHAR(100) NOT NULL              COMMENT '设备名称',
    device_type    VARCHAR(50)  NOT NULL              COMMENT '设备类型：FAN/PUMP/LIGHT/CURTAIN',
    status         TINYINT      DEFAULT 0             COMMENT '状态：0-关闭，1-开启',
    create_time    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted        TINYINT      DEFAULT 0             COMMENT '逻辑删除',
    PRIMARY KEY (id),
    INDEX idx_greenhouse_id (greenhouse_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备表';

-- 5. 报警记录表
CREATE TABLE IF NOT EXISTS gh_alert_log (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    greenhouse_id  BIGINT       NOT NULL              COMMENT '所属大棚ID',
    alert_type     VARCHAR(50)  NOT NULL              COMMENT '报警类型',
    message        VARCHAR(500) NOT NULL              COMMENT '报警内容',
    status         TINYINT      DEFAULT 0             COMMENT '状态：0-未处理，1-已处理',
    create_time    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '报警时间',
    handle_time    DATETIME     DEFAULT NULL          COMMENT '处理时间',
    deleted        TINYINT      DEFAULT 0             COMMENT '逻辑删除',
    PRIMARY KEY (id),
    INDEX idx_greenhouse_id (greenhouse_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报警记录表';

-- 6. 作物图像分类结果表（MinIO + YOLO 定时分类）
CREATE TABLE IF NOT EXISTS gh_classification_result (
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    greenhouse_key   VARCHAR(50)  NOT NULL              COMMENT '大棚标识：cotton/sunflower/strawberry',
    greenhouse_name  VARCHAR(50)  NOT NULL              COMMENT '大棚中文名',
    object_name      VARCHAR(500) NOT NULL              COMMENT 'MinIO 对象路径',
    file_name        VARCHAR(200) NOT NULL              COMMENT '文件名',
    class_id         INT          DEFAULT 0             COMMENT '分类类别ID (0-13)',
    class_name_cn    VARCHAR(50)  DEFAULT ''            COMMENT '中文类别名',
    class_name_en    VARCHAR(50)  DEFAULT ''            COMMENT '英文类别名',
    confidence       DOUBLE       DEFAULT 0             COMMENT '置信度 [0,1]',
    elapsed_ms       BIGINT       DEFAULT 0             COMMENT '推理耗时（毫秒）',
    image_size       BIGINT       DEFAULT 0             COMMENT '图片大小（字节）',
    create_time      DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '分类时间',
    PRIMARY KEY (id),
    INDEX idx_greenhouse_key (greenhouse_key),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作物图像分类结果表';

-- =============================================
-- 以下为测试数据（可选）
-- =============================================

-- 插入测试大棚
INSERT INTO gh_greenhouse (name, location, area, description) VALUES
('一号大棚', 'A区-东侧', 200.0, '番茄种植大棚'),
('二号大棚', 'A区-西侧', 180.0, '黄瓜种植大棚'),
('三号大棚', 'B区-南侧', 250.0, '辣椒种植大棚');

-- 插入测试设备
INSERT INTO gh_device (greenhouse_id, device_name, device_type, status) VALUES
(1, '风机-1号', 'FAN', 0),
(1, '补光灯-1号', 'LIGHT', 0);

-- 插入测试传感器数据
INSERT INTO gh_sensor_data (greenhouse_id, temperature, humidity, light_intensity, co2_concentration, soil_moisture, soil_ph, record_time) VALUES
(1, 25.5, 65.0, 30000, 420, 55.0, 6.5, '2026-05-26 08:00:00'),
(1, 26.8, 63.0, 32000, 435, 53.0, 6.3, '2026-05-26 09:00:00'),
(1, 28.2, 60.0, 35000, 450, 50.0, 6.1, '2026-05-26 10:00:00'),
(2, 24.0, 70.0, 28000, 400, 60.0, 6.8, '2026-05-26 08:00:00'),
(2, 25.1, 68.0, 29000, 410, 58.0, 6.7, '2026-05-26 09:00:00');
