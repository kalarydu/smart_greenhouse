package com.greenhouse.controller;

import com.greenhouse.common.Result;
import com.greenhouse.entity.ClassificationResult;
import com.greenhouse.mapper.ClassificationResultMapper;
import com.greenhouse.service.ClassificationSSEService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * 作物图像分类 - SSE 推送 & 历史查询接口
 */
@RestController
@RequestMapping("/api/classification")
public class ClassificationController {

    @Autowired
    private ClassificationSSEService sseService;

    @Autowired
    private ClassificationResultMapper resultMapper;

    /**
     * GET /api/classification/stream
     * SSE 实时推送端点，前端通过 EventSource 连接
     *
     * 前端示例：
     *   const es = new EventSource('/api/classification/stream');
     *   es.addEventListener('classification', e => {
     *       const { count, data } = JSON.parse(e.data);
     *       // data 是 ClassificationResult[] 数组
     *   });
     */
    @GetMapping("/stream")
    public SseEmitter stream() {
        return sseService.createEmitter();
    }

    /**
     * GET /api/classification/status
     * 查询 SSE 连接状态
     */
    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        return Result.ok(Map.of(
                "connections", sseService.getConnectionCount(),
                "status", "running"
        ));
    }

    /**
     * GET /api/classification/recent?limit=30
     * 查询最近的分类结果
     */
    @GetMapping("/recent")
    public Result<List<ClassificationResult>> recent(
            @RequestParam(defaultValue = "30") int limit) {
        return Result.ok(resultMapper.findRecent(Math.min(limit, 200)));
    }

    /**
     * GET /api/classification/recent/{greenhouseKey}?limit=10
     * 按大棚查询最近分类结果
     */
    @GetMapping("/recent/{greenhouseKey}")
    public Result<List<ClassificationResult>> recentByGreenhouse(
            @PathVariable String greenhouseKey,
            @RequestParam(defaultValue = "10") int limit) {
        return Result.ok(resultMapper.findRecentByGreenhouse(greenhouseKey, Math.min(limit, 100)));
    }
}
