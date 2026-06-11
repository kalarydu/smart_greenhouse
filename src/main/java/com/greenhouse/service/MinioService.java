package com.greenhouse.service;

import com.greenhouse.config.MinioConfig;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.Result;
import io.minio.messages.Item;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * MinIO 图片服务
 * 负责从 MinIO 对象存储中获取农作物图片，供 ONNX 分类模型使用
 *
 * 使用方式：
 *   byte[] imgBytes = minioService.getImageBytes("crops", "test/cotton_fruit/aug_0_6761.png");
 *   // 喂给 CropClassifierONNX.predict(imgBytes)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MinioService {

    private final MinioConfig minioConfig;

    private MinioClient client;

    @PostConstruct
    public void init() {
        client = connect();
        log.info("MinIO 客户端初始化完成");
    }

    /**
     * 按顺序尝试所有端点，使用第一个可连接的
     */
    private MinioClient connect() {
        List<String> endpoints = minioConfig.getEndpoints();
        if (endpoints == null || endpoints.isEmpty()) {
            throw new IllegalStateException("minio.endpoints 未配置");
        }

        String protocol = minioConfig.isSecure() ? "https://" : "http://";

        for (String ep : endpoints) {
            try {
                MinioClient c = MinioClient.builder()
                        .endpoint(protocol + ep)
                        .credentials(minioConfig.getAccessKey(), minioConfig.getSecretKey())
                        .build();
                // 验证连通性
                c.listBuckets();
                log.info("MinIO 连接成功: {}", ep);
                return c;
            } catch (Exception e) {
                log.warn("MinIO 端点 {} 不可达: {}", ep, e.getMessage());
            }
        }
        throw new IllegalStateException("所有 MinIO 端点均不可达: " + endpoints);
    }

    // ==================== 核心方法 ====================

    /**
     * 获取单张图片的字节数组（不写磁盘，直接用于模型推理）
     *
     * @param bucket    桶名称
     * @param objectName 对象路径，如 "test/cotton_fruit/aug_0_6761.png"
     * @return 图片字节数组
     */
    public byte[] getImageBytes(String bucket, String objectName) {
        try (InputStream stream = client.getObject(
                GetObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectName)
                        .build())) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[8192];
            int n;
            while ((n = stream.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, n);
            }
            byte[] result = buffer.toByteArray();
            log.debug("获取图片: {}/{} ({} bytes)", bucket, objectName, result.length);
            return result;
        } catch (Exception e) {
            log.error("获取图片失败: {}/{} — {}", bucket, objectName, e.getMessage());
            throw new RuntimeException("从 MinIO 获取图片失败: " + objectName, e);
        }
    }

    /**
     * 使用默认 bucket 获取图片
     */
    public byte[] getImageBytes(String objectName) {
        return getImageBytes(minioConfig.getDefaultBucket(), objectName);
    }

    /**
     * 列举 bucket 中指定前缀下的所有对象名称
     *
     * @param bucket    桶名称
     * @param prefix    前缀过滤，如 "test/cotton_fruit"
     * @param recursive 是否递归列举
     * @return 对象名称列表
     */
    public List<String> listObjects(String bucket, String prefix, boolean recursive) {
        List<String> names = new ArrayList<>();
        try {
            Iterable<Result<Item>> results = client.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucket)
                            .prefix(prefix)
                            .recursive(recursive)
                            .build());
            for (Result<Item> result : results) {
                Item item = result.get();
                if (!item.isDir()) {
                    names.add(item.objectName());
                }
            }
            log.info("列举 {}/{}: {} 个对象", bucket, prefix, names.size());
        } catch (Exception e) {
            log.error("列举对象失败: {}/{} — {}", bucket, prefix, e.getMessage());
            throw new RuntimeException("从 MinIO 列举对象失败", e);
        }
        return names;
    }

    /**
     * 列举对象（默认递归）
     */
    public List<String> listObjects(String bucket, String prefix) {
        return listObjects(bucket, prefix, true);
    }

    /**
     * 批量获取图片字节（用于批量推理场景）
     *
     * @param bucket   桶名称
     * @param prefix   前缀过滤
     * @param maxFiles 最大拉取数量，0 表示不限制
     * @return 图片数据列表
     */
    public List<ImageData> getImagesBatch(String bucket, String prefix, int maxFiles) {
        List<String> names = listObjects(bucket, prefix);
        if (maxFiles > 0 && maxFiles < names.size()) {
            names = names.subList(0, maxFiles);
        }
        log.info("批量获取 {} 张图片 (bucket={}, prefix={})", names.size(), bucket, prefix);

        List<ImageData> results = new ArrayList<>();
        for (String name : names) {
            try {
                byte[] data = getImageBytes(bucket, name);
                results.add(new ImageData(name, data));
            } catch (Exception e) {
                log.warn("跳过无法获取的图片: {}", name);
            }
        }
        return results;
    }

    // ==================== 数据类 ====================

    /**
     * 封装对象名和字节内容
     */
    public static class ImageData {
        private final String objectName;
        private final byte[] data;

        public ImageData(String objectName, byte[] data) {
            this.objectName = objectName;
            this.data = data;
        }

        public String getObjectName() { return objectName; }
        public byte[] getData() { return data; }
        public int getSize() { return data.length; }
    }
}
