package com.greenhouse.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.greenhouse.entity.SensorData;

import java.time.LocalDateTime;
import java.util.List;

public interface SensorDataService extends IService<SensorData> {

    /**
     * 按大棚ID列表和时间范围查询传感器数据（升序，供图表使用）
     */
    List<SensorData> listByCondition(List<Long> greenhouseIds, LocalDateTime start, LocalDateTime end);
}
