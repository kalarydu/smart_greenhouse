package com.greenhouse.onnx.service;

import com.greenhouse.onnx.CropClassifierONNX;
import com.greenhouse.service.MinioService;
import ai.onnxruntime.OrtException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

/**
 * 农作物图像分类服务
 * 负责加载 ONNX 模型并提供预测能力
 * 支持本地文件 / 上传文件 / MinIO 对象存储三种图片来源
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClassifierService {

    private final MinioService minioService;

    @Value("classpath:models/best.onnx")
    private Resource modelResource;

    @Value("${crop.image.width:320}")
    private int imgWidth;

    @Value("${crop.image.height:320}")
    private int imgHeight;

    private CropClassifierONNX classifier;

    @PostConstruct
    public void init() throws Exception {
        if (!modelResource.exists()) {
            throw new IllegalStateException("ONNX 模型文件不存在: " + modelResource.getDescription());
        }
        // 使用字节数组加载，兼容开发环境（文件系统）和生产环境（JAR 内打包）
        byte[] modelBytes = StreamUtils.copyToByteArray(modelResource.getInputStream());
        classifier = new CropClassifierONNX(modelBytes, imgWidth, imgHeight);
        log.info("ONNX 模型加载成功，支持 {} 个类别", classifier.getClassNames().length);
    }

    /**
     * 上传文件预测
     */
    public Map<String, Object> predict(MultipartFile file) throws Exception {
        CropClassifierONNX.PredictionResult result = classifier.predict(file.getBytes());
        return buildResponse(result, true);
    }

    /**
     * 本地图片路径预测
     */
    public Map<String, Object> predict(String imagePath) throws Exception {
        CropClassifierONNX.PredictionResult result = classifier.predict(imagePath);
        return buildResponse(result, false);
    }

    /**
     * 从 MinIO 对象存储拉取图片并预测
     *
     * @param bucket     MinIO 桶名称
     * @param objectName 对象路径，如 "test/cotton_fruit/aug_0_6761.png"
     */
    public Map<String, Object> predictFromMinio(String bucket, String objectName) throws Exception {
        byte[] imageBytes = minioService.getImageBytes(bucket, objectName);
        CropClassifierONNX.PredictionResult result = classifier.predict(imageBytes);
        Map<String, Object> response = buildResponse(result, false);
        response.put("source", "minio");
        response.put("bucket", bucket);
        response.put("objectName", objectName);
        return response;
    }

    /**
     * 获取所有支持的类别列表
     */
    public Map<String, Object> getClasses() {
        String[] en = classifier.getClassNames();
        String[] cn = classifier.getClassNamesCn();
        List<Map<String, String>> list = new ArrayList<>();
        for (int i = 0; i < en.length; i++) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("id", String.valueOf(i));
            item.put("nameEn", en[i]);
            item.put("nameCn", cn[i]);
            list.add(item);
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("total", en.length);
        response.put("classes", list);
        return response;
    }

    private Map<String, Object> buildResponse(CropClassifierONNX.PredictionResult result, boolean includeTop3) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("classId", result.classId);
        response.put("classNameEn", result.classNameEn);
        response.put("classNameCn", result.classNameCn);
        response.put("confidence", Math.round(result.confidence * 10000.0) / 10000.0);

        if (includeTop3) {
            List<Map<String, Object>> top3 = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("classId", result.top3Indices[i]);
                item.put("classNameCn", classifier.getClassNamesCn()[result.top3Indices[i]]);
                item.put("probability", Math.round(result.top3Probs[i] * 10000.0) / 10000.0);
                top3.add(item);
            }
            response.put("top3", top3);
        }
        return response;
    }

    @PreDestroy
    public void destroy() throws OrtException {
        if (classifier != null) classifier.close();
        log.info("ONNX 模型已释放");
    }
}
