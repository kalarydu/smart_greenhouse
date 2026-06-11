package com.greenhouse.service;

import com.greenhouse.entity.ClassificationResult;
import com.greenhouse.mapper.ClassificationResultMapper;
import com.greenhouse.onnx.service.ClassifierService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 作物图像定时分类调度器
 *
 * 按配置的间隔时间，自动从 MinIO 拉取三大棚（棉花/向日葵/草莓）的图片，
 * 调用 YOLO 模型分类，结果存入数据库并通过 SSE 推送到前端。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CropImageScheduler {

    private final MinioService minioService;
    private final ClassifierService classifierService;
    private final ClassificationResultMapper resultMapper;
    private final ClassificationSSEService sseService;

    @Value("${minio.default-bucket:crops}")
    private String bucket;

    /**
     * 三大棚配置 —— 子前缀直接对应 MinIO 中 "农作物_时期" 命名的文件夹。
     * 例：cotton_flowering/aug_0_5367.png → 棉花大棚
     */
    private static final List<GreenhouseEntry> GREENHOUSES = List.of(
        new GreenhouseEntry("cotton", "棉花大棚", List.of(
            "cotton_flowering/", "cotton_fruiting/", "cotton_plant/",
            "cotton_seedling/", "cotton_sprout/")),
        new GreenhouseEntry("sunflower", "向日葵大棚", List.of(
            "sunflower_earlyBloom/", "sunflower_healthy/", "sunflower_matureBud/")),
        new GreenhouseEntry("strawberry", "草莓大棚", List.of(
            "strawberry_flowering/", "strawberry_fruiting/",
            "strawberry_growing/", "strawberry_mature/"))
    );

    private static class GreenhouseEntry {
        final String key;
        final String name;
        final List<String> prefixes;
        GreenhouseEntry(String key, String name, List<String> prefixes) {
            this.key = key; this.name = name; this.prefixes = prefixes;
        }
        String getKey() { return key; }
        String getName() { return name; }
        List<String> getPrefixes() { return prefixes; }
    }

    @Scheduled(initialDelayString = "5000", fixedRateString = "${greenhouse.classification.interval-seconds:30}000")
    public void run() {
        log.info("========== 定时分类任务开始 ==========");
        long roundStart = System.currentTimeMillis();
        List<ClassificationResult> allResults = new ArrayList<>();
        int totalSuccess = 0, totalFail = 0;
        int batchSize = 3;

        // 列举 bucket 下所有对象（MinIO 精确前缀匹配有 bug，统一全量列举 + 代码过滤）
        List<String> allObjects;
        try {
            allObjects = minioService.listObjects(bucket, "");
            log.info("MinIO {} 下共有 {} 个对象", bucket, allObjects.size());
        } catch (Exception e) {
            log.error("列举 MinIO 对象失败: {}", e.getMessage());
            return;
        }

        for (GreenhouseEntry gh : GREENHOUSES) {
            String key = gh.getKey();
            String name = gh.getName();
            List<String> prefixes = gh.getPrefixes();

            try {
                // 从全部对象中过滤出属于本大棚的
                List<String> mine = new ArrayList<>();
                for (String obj : allObjects) {
                    for (String prefix : prefixes) {
                        if (obj.startsWith(prefix)) {
                            mine.add(obj);
                            break;
                        }
                    }
                }

                if (mine.isEmpty()) {
                    log.info("  {}: 无可用图片 (过滤自 {} 个子前缀)，跳过", name, prefixes.size());
                    continue;
                }

                // 随机选取
                Collections.shuffle(mine, new Random());
                int take = Math.min(batchSize, mine.size());
                List<String> selected = mine.subList(0, take);
                log.info("  {}: 从 {} 张中选取 {} 张", name, mine.size(), take);

                // 逐张拉取 + 分类
                for (int i = 0; i < selected.size(); i++) {
                    String objectName = selected.get(i);
                    try {
                        long t0 = System.currentTimeMillis();
                        Map<String, Object> predictResult = classifierService.predictFromMinio(bucket, objectName);
                        long elapsed = System.currentTimeMillis() - t0;

                        ClassificationResult record = new ClassificationResult();
                        record.setGreenhouseKey(key);
                        record.setGreenhouseName(name);
                        record.setObjectName(objectName);
                        record.setFileName(
                                java.nio.file.Paths.get(objectName).getFileName().toString());
                        record.setClassId((Integer) predictResult.get("classId"));
                        record.setClassNameCn((String) predictResult.get("classNameCn"));
                        record.setClassNameEn((String) predictResult.get("classNameEn"));
                        record.setConfidence((Double) predictResult.get("confidence"));
                        record.setElapsedMs(elapsed);
                        record.setCreateTime(LocalDateTime.now());

                        resultMapper.insert(record);
                        allResults.add(record);
                        totalSuccess++;

                        log.debug("    [{}/{}] {} -> {} conf={} {}ms",
                                i + 1, take, record.getFileName(),
                                record.getClassNameCn(),
                                String.format("%.4f", record.getConfidence()), elapsed);

                    } catch (Exception e) {
                        totalFail++;
                        log.warn("    [{}/{}] {} 分类失败: {}", i + 1, take,
                                java.nio.file.Paths.get(objectName).getFileName(),
                                e.getMessage());
                    }
                }

            } catch (Exception e) {
                log.error("  {} 大棚处理异常: {}", name, e.getMessage());
            }
        }

        // 4. SSE 推送
        if (!allResults.isEmpty()) {
            sseService.broadcast(allResults);
        }

        // 5. 清理 24 小时前的旧记录
        try {
            int deleted = resultMapper.deleteOlderThan(24);
            if (deleted > 0) log.debug("清理了 {} 条过期分类记录", deleted);
        } catch (Exception e) {
            log.warn("清理旧记录失败: {}", e.getMessage());
        }

        long totalMs = System.currentTimeMillis() - roundStart;
        log.info("定时分类任务完成: 成功 {} 张, 失败 {} 张, 总耗时 {}ms, 推送 {} 条",
                totalSuccess, totalFail, totalMs, allResults.size());
    }
}
