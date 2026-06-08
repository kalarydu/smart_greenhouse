package com.greenhouse.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.greenhouse.entity.Greenhouse;
import com.greenhouse.mapper.GreenhouseMapper;
import com.greenhouse.service.GreenhouseService;
import org.springframework.stereotype.Service;

@Service
public class GreenhouseServiceImpl extends ServiceImpl<GreenhouseMapper, Greenhouse> implements GreenhouseService {
}
