package com.greenhouse.onnx.controller;

import com.greenhouse.common.Result;
import com.greenhouse.onnx.service.ClassifierService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 农作物图像分类接口
 * 支持上传图片预测 / 本地路径预测 / MinIO 对象存储预测 / 查询类别 / 健康检查
 */
@RestController
@RequestMapping("/api/classify")
public class ClassifierController {

    @Autowired
    private ClassifierService service;

    /**
     * POST /api/classify
     * 上传图片进行预测，form-data key: file
     */
    @PostMapping
    public Result<Map<String, Object>> classify(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return Result.fail("文件为空");
        }
        try {
            return Result.ok(service.predict(file));
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }

    /**
     * POST /api/classify/path
     * 通过本地图片路径进行预测，body: { "imagePath": "/path/to/image.jpg" }
     */
    @PostMapping("/path")
    public Result<Map<String, Object>> classifyByPath(@RequestBody Map<String, String> body) {
        String imagePath = body.get("imagePath");
        if (imagePath == null || imagePath.isBlank()) {
            return Result.fail("imagePath 不能为空");
        }
        try {
            return Result.ok(service.predict(imagePath));
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }

    /**
     * POST /api/classify/minio
     * 从 MinIO 对象存储拉取图片并预测
     * body: { "bucket": "crops", "objectName": "test/cotton_fruit/aug_0_6761.png" }
     */
    @PostMapping("/minio")
    public Result<Map<String, Object>> classifyFromMinio(@RequestBody Map<String, String> body) {
        String bucket = body.get("bucket");
        String objectName = body.get("objectName");

        if (objectName == null || objectName.isBlank()) {
            return Result.fail("objectName 不能为空");
        }
        try {
            return Result.ok(service.predictFromMinio(bucket, objectName));
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }

    /**
     * GET /api/classify/classes
     * 获取支持的 14 个类别列表
     */
    @GetMapping("/classes")
    public Result<Map<String, Object>> getClasses() {
        return Result.ok(service.getClasses());
    }

    /**
     * GET /api/classify/health
     * 健康检查
     */
    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        return Result.ok(Map.of("status", "ok", "model", "loaded"));
    }
}
