package com.greenhouse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.greenhouse.entity.SensorData;
import com.greenhouse.mapper.SensorDataMapper;
import com.greenhouse.service.SensorDataService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SensorDataServiceImpl extends ServiceImpl<SensorDataMapper, SensorData> implements SensorDataService {

    @Override
    public List<SensorData> listByCondition(List<Long> greenhouseIds, LocalDateTime start, LocalDateTime end) {
        LambdaQueryWrapper<SensorData> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(SensorData::getGreenhouseId, greenhouseIds);
        if (start != null) {
            wrapper.ge(SensorData::getRecordTime, start);
        }
        if (end != null) {
            wrapper.le(SensorData::getRecordTime, end);
        }
        wrapper.orderByAsc(SensorData::getRecordTime);
        return this.list(wrapper);
    }
}
