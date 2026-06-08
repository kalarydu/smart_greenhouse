package com.greenhouse.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.greenhouse.entity.AlertLog;
import com.greenhouse.mapper.AlertLogMapper;
import com.greenhouse.service.AlertLogService;
import org.springframework.stereotype.Service;

@Service
public class AlertLogServiceImpl extends ServiceImpl<AlertLogMapper, AlertLog> implements AlertLogService {
}
