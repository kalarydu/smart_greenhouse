package com.greenhouse.service;

import com.greenhouse.entity.ClassificationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SSE (Server-Sent Events) 推送服务
 *
 * 管理前端 SSE 连接池，定时分类任务完成后通过此服务推送结果。
 */
@Slf4j
@Service
public class ClassificationSSEService {

    /** 线程安全的 SseEmitter 列表 */
    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    /** SSE 超时时间（毫秒），设为 Long.MAX_VALUE 表示永不超时 */
    private static final Long SSE_TIMEOUT = Long.MAX_VALUE;

    /**
     * 创建新的 SSE 连接并注册到连接池
     */
    public SseEmitter createEmitter() {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        emitters.add(emitter);
        log.info("SSE 客户端连接，当前连接数: {}", emitters.size());

        // 连接断开时自动移除
        emitter.onCompletion(() -> {
            emitters.remove(emitter);
            log.info("SSE 客户端断开(completion)，当前连接数: {}", emitters.size());
        });
        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            log.info("SSE 客户端超时，当前连接数: {}", emitters.size());
        });
        emitter.onError(e -> {
            emitters.remove(emitter);
            log.info("SSE 客户端异常断开，当前连接数: {}", emitters.size());
        });

        return emitter;
    }

    /**
     * 向所有已连接的客户端广播分类结果
     */
    public void broadcast(List<ClassificationResult> results) {
        if (results.isEmpty() || emitters.isEmpty()) {
            return;
        }

        // 构造 SSE 事件数据
        StringBuilder sb = new StringBuilder();
        sb.append("{\"event\":\"classification\",\"count\":").append(results.size()).append(",\"data\":[");
        for (int i = 0; i < results.size(); i++) {
            ClassificationResult r = results.get(i);
            sb.append("{");
            sb.append("\"greenhouseKey\":\"").append(jsonSafe(r.getGreenhouseKey())).append("\",");
            sb.append("\"greenhouseName\":\"").append(jsonSafe(r.getGreenhouseName())).append("\",");
            sb.append("\"fileName\":\"").append(jsonSafe(r.getFileName())).append("\",");
            sb.append("\"classNameCn\":\"").append(jsonSafe(r.getClassNameCn())).append("\",");
            sb.append("\"confidence\":").append(String.format("%.4f", r.getConfidence())).append(",");
            sb.append("\"elapsedMs\":").append(r.getElapsedMs());
            sb.append("}");
            if (i < results.size() - 1) sb.append(",");
        }
        sb.append("]}");
        String eventData = sb.toString();

        // 广播到所有客户端，移除已断开的
        List<SseEmitter> deadEmitters = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("classification")
                        .data(eventData));
            } catch (IOException e) {
                deadEmitters.add(emitter);
            }
        }
        emitters.removeAll(deadEmitters);

        log.info("SSE 推送完成: {} 条结果 → {} 个客户端 (移除 {} 个死连接)",
                results.size(), emitters.size(), deadEmitters.size());
    }

    /** 获取当前连接数 */
    public int getConnectionCount() {
        return emitters.size();
    }

    private String jsonSafe(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
