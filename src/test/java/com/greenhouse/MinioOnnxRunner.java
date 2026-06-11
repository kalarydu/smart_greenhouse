package com.greenhouse;

import com.greenhouse.onnx.CropClassifierONNX;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.Result;
import io.minio.messages.Item;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * MinIO + YOLO 独立测试启动器（不依赖 Spring 上下文）
 *
 * <h3>使用方式</h3>
 * <pre>
 *   # 编译
 *   mvn test-compile
 *
 *   # 单次批量拉取 + 分类（取 10 张）
 *   java -cp "target/test-classes;target/classes;..." com.greenhouse.MinioOnnxRunner \
 *       --batch-size=10
 *
 *   # 定时自动拉取（每 30 秒，跑 3 轮）
 *   java -cp ... com.greenhouse.MinioOnnxRunner \
 *       --batch-size=5 --interval=30 --rounds=3
 *
 *   # 使用 Maven 一键运行（推荐）
 *   mvn test-compile exec:java \
 *     -Dexec.mainClass=com.greenhouse.MinioOnnxRunner \
 *     -Dexec.classpathScope=test \
 *     -Dexec.args="--batch-size=5 --interval=30 --rounds=3"
 * </pre>
 *
 * <h3>参数说明</h3>
 * <ul>
 *   <li>{@code --batch-size=N}  每轮拉取数量（默认 5，别太多）</li>
 *   <li>{@code --interval=N}    轮询间隔秒数（默认 30）</li>
 *   <li>{@code --rounds=N}      总轮数（0 = 无限直到 Ctrl+C）</li>
 *   <li>{@code --bucket=xxx}    MinIO bucket（默认 crops）</li>
 *   <li>{@code --prefix=xxx}    MinIO 前缀（默认 test/）</li>
 *   <li>{@code --endpoint=xxx}  MinIO 地址（默认 10.190.83.10:9000）</li>
 * </ul>
 */
public class MinioOnnxRunner {

    // ====== 默认配置 ======
    private static final String MODEL_PATH = "src/main/resources/models/best.onnx";
    private static final int IMG_WIDTH = 320;
    private static final int IMG_HEIGHT = 320;
    private static final String RESULT_DIR = "test/result";

    public static void main(String[] args) {
        // 解析参数
        int batchSize = 5;
        int intervalSec = 30;
        int rounds = 3;
        String bucket = "crops";
        String prefix = "test/";
        String endpoint = "http://10.190.83.10:9000";
        String accessKey = "minioadmin";
        String secretKey = "minioadmin";

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            // 支持 --key=value 和 --key value 两种格式
            String val = null;
            if (arg.contains("=")) {
                String[] parts = arg.split("=", 2);
                val = parts[1];
                arg = parts[0];
            } else if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                val = args[++i];
            }
            switch (arg) {
                case "--batch-size": batchSize = Integer.parseInt(val); break;
                case "--interval":   intervalSec = Integer.parseInt(val); break;
                case "--rounds":     rounds = Integer.parseInt(val); break;
                case "--bucket":     bucket = val; break;
                case "--prefix":     prefix = val; break;
                case "--endpoint":   endpoint = val; break;
                case "--access-key": accessKey = val; break;
                case "--secret-key": secretKey = val; break;
            }
        }

        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║   MinIO + YOLO 自动分类测试启动器         ║");
        System.out.println("╠══════════════════════════════════════════╣");
        System.out.printf("║  Endpoint:   %-30s║%n", endpoint);
        System.out.printf("║  Bucket:     %-30s║%n", bucket);
        System.out.printf("║  Prefix:     %-30s║%n", prefix);
        System.out.printf("║  每轮数量:   %-3d 张                       ║%n", batchSize);
        System.out.printf("║  间隔:       %-3d 秒                       ║%n", intervalSec);
        System.out.printf("║  总轮次:     %s                          ║%n",
                rounds == 0 ? "无限（Ctrl+C 停止）" : rounds + " 轮");
        System.out.printf("║  结果目录:   %-30s║%n", RESULT_DIR);
        System.out.println("╚══════════════════════════════════════════╝");

        try {
            // 1. 初始化 MinIO 客户端
            System.out.println("\n[1/2] 连接 MinIO...");
            MinioClient minioClient = MinioClient.builder()
                    .endpoint(endpoint)
                    .credentials(accessKey, secretKey)
                    .build();
            minioClient.listBuckets(); // 验证连通
            System.out.println("  ✅ MinIO 连接成功");

            // 2. 加载 ONNX 模型
            System.out.println("[2/2] 加载 YOLO 模型...");
            byte[] modelBytes = Files.readAllBytes(Paths.get(MODEL_PATH));
            CropClassifierONNX classifier = new CropClassifierONNX(modelBytes, IMG_WIDTH, IMG_HEIGHT);
            System.out.println("  ✅ 模型加载成功，支持 " + classifier.getClassNames().length + " 个类别");
            System.out.println();

            // 3. 循环拉取 + 分类
            int round = 0;
            while (rounds == 0 || round < rounds) {
                round++;
                System.out.println("═══════════════════════════════════════════");
                System.out.println("  📍 第 " + round + " 轮" + (rounds > 0 ? "/" + rounds : "") +
                        " — " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                System.out.println("═══════════════════════════════════════════");

                // 列举对象
                List<String> objects = listObjects(minioClient, bucket, prefix);
                System.out.println("  MinIO 可用对象: " + objects.size() + " 个");

                if (objects.isEmpty()) {
                    System.out.println("  ⚠️ 无可用对象，等待下一轮...");
                } else {
                    // 随机选取（打乱避免总拿同一批）
                    Collections.shuffle(objects, new Random());
                    int take = Math.min(batchSize, objects.size());
                    List<String> selected = objects.subList(0, take);

                    long totalMs = 0;
                    List<PredictionRecord> records = new ArrayList<>();
                    int successCount = 0, failCount = 0;

                    for (int i = 0; i < selected.size(); i++) {
                        String objectName = selected.get(i);
                        String shortName = Paths.get(objectName).getFileName().toString();
                        try {
                            long t0 = System.currentTimeMillis();

                            // 从 MinIO 拉取图片字节
                            byte[] imgBytes = getImageBytes(minioClient, bucket, objectName);

                            // YOLO 推理
                            CropClassifierONNX.PredictionResult result = classifier.predict(imgBytes);

                            long elapsed = System.currentTimeMillis() - t0;
                            totalMs += elapsed;
                            successCount++;

                            records.add(new PredictionRecord(
                                    shortName, result.classNameCn, result.confidence, elapsed, objectName));

                            System.out.printf("    [%2d/%2d] %-38s → %-14s  conf=%.4f  %4dms%n",
                                    i + 1, selected.size(), truncate(shortName, 38),
                                    result.classNameCn, result.confidence, elapsed);

                        } catch (Exception e) {
                            failCount++;
                            String err = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                            records.add(new PredictionRecord(shortName, "ERR", 0, 0, objectName, err));
                            System.out.printf("    [%2d/%2d] %-38s → ❌ %s%n",
                                    i + 1, selected.size(), truncate(shortName, 38), truncate(err, 40));
                        }
                    }

                    // 每轮保存结果 JSON
                    saveRoundResult(records, round, batchSize, successCount, failCount,
                            totalMs, bucket, prefix);

                    // 打印汇总
                    if (successCount > 0) {
                        System.out.printf("%n    📊 成功: %d, 失败: %d, 平均耗时: %dms, 平均置信度: %.4f%n",
                                successCount, failCount,
                                totalMs / successCount,
                                records.stream()
                                        .filter(r -> r.error == null)
                                        .mapToDouble(r -> r.confidence)
                                        .average().orElse(0));
                    }
                }

                // 最后一轮不等待
                if (rounds > 0 && round >= rounds) {
                    System.out.println("\n✅ 已完成全部 " + rounds + " 轮");
                    break;
                }

                // 等待间隔
                System.out.print("\n  ⏳ 等待 " + intervalSec + " 秒...");
                for (int s = intervalSec; s > 0; s--) {
                    try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                    if (s % 10 == 0) System.out.print(" " + s + "s");
                }
                System.out.println();
            }

            classifier.close();
            System.out.println("✅ 全部完成！结果保存在 " + RESULT_DIR + "/");

        } catch (Exception e) {
            System.err.println("❌ 启动失败: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    // ==================== MinIO 操作 ====================

    private static byte[] getImageBytes(MinioClient client, String bucket, String objectName) throws Exception {
        try (InputStream stream = client.getObject(
                GetObjectArgs.builder().bucket(bucket).object(objectName).build())) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] tmp = new byte[8192];
            int n;
            while ((n = stream.read(tmp)) != -1) buf.write(tmp, 0, n);
            return buf.toByteArray();
        }
    }

    private static List<String> listObjects(MinioClient client, String bucket, String prefix) throws Exception {
        List<String> names = new ArrayList<>();
        Iterable<Result<Item>> results = client.listObjects(
                ListObjectsArgs.builder().bucket(bucket).prefix(prefix).recursive(true).build());
        for (Result<Item> r : results) {
            Item item = r.get();
            if (!item.isDir()) names.add(item.objectName());
        }
        return names;
    }

    // ==================== 结果保存 ====================

    private static void saveRoundResult(List<PredictionRecord> records, int round,
                                         int totalFetched, int success, int fail, long totalMs,
                                         String bucket, String prefix) {
        try {
            Path resultDir = Paths.get(RESULT_DIR);
            Files.createDirectories(resultDir);

            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            Path file = resultDir.resolve("round_" + String.format("%03d", round) + "_" + ts + ".json");

            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            sb.append("  \"round\": ").append(round).append(",\n");
            sb.append("  \"timestamp\": \"").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\",\n");
            sb.append("  \"bucket\": \"").append(jsonSafe(bucket)).append("\",\n");
            sb.append("  \"prefix\": \"").append(jsonSafe(prefix)).append("\",\n");
            sb.append("  \"totalFetched\": ").append(totalFetched).append(",\n");
            sb.append("  \"successCount\": ").append(success).append(",\n");
            sb.append("  \"failCount\": ").append(fail).append(",\n");
            sb.append("  \"totalMs\": ").append(totalMs).append(",\n");
            if (success > 0) {
                sb.append("  \"avgMs\": ").append(totalMs / success).append(",\n");
                double avgConf = records.stream()
                        .filter(r -> r.error == null)
                        .mapToDouble(r -> r.confidence)
                        .average().orElse(0);
                sb.append("  \"avgConfidence\": ").append(String.format("%.4f", avgConf)).append(",\n");

                // 类别分布
                Map<String, Long> dist = records.stream()
                        .filter(r -> r.error == null)
                        .collect(Collectors.groupingBy(r -> r.classNameCn, LinkedHashMap::new, Collectors.counting()));
                sb.append("  \"classDistribution\": {\n");
                List<String> entries = new ArrayList<>();
                dist.forEach((cn, count) -> entries.add("    \"" + jsonSafe(cn) + "\": " + count));
                sb.append(String.join(",\n", entries));
                sb.append("\n  },\n");
            }
            sb.append("  \"records\": [\n");
            for (int i = 0; i < records.size(); i++) {
                PredictionRecord r = records.get(i);
                sb.append("    {\"fileName\": \"").append(jsonSafe(r.fileName))
                        .append("\", \"objectName\": \"").append(jsonSafe(r.objectName))
                        .append("\", \"classNameCn\": \"").append(jsonSafe(r.classNameCn))
                        .append("\", \"confidence\": ").append(String.format("%.4f", r.confidence))
                        .append(", \"elapsedMs\": ").append(r.elapsedMs);
                if (r.error != null) {
                    sb.append(", \"error\": \"").append(jsonSafe(r.error)).append("\"");
                }
                sb.append("}");
                if (i < records.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("  ]\n");
            sb.append("}\n");

            Files.writeString(file, sb.toString());
            System.out.println("  📁 结果已保存: " + file.toAbsolutePath());

        } catch (Exception e) {
            System.err.println("  ⚠️ 保存结果失败: " + e.getMessage());
        }
    }

    // ==================== 工具方法 ====================

    private static String jsonSafe(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static String truncate(String s, int max) {
        if (s == null) return "null";
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }

    // ==================== 数据类 ====================

    private static class PredictionRecord {
        final String fileName;
        final String classNameCn;
        final double confidence;
        final long elapsedMs;
        final String objectName;
        final String error;

        PredictionRecord(String fileName, String classNameCn, double confidence,
                         long elapsedMs, String objectName) {
            this(fileName, classNameCn, confidence, elapsedMs, objectName, null);
        }

        PredictionRecord(String fileName, String classNameCn, double confidence,
                         long elapsedMs, String objectName, String error) {
            this.fileName = fileName;
            this.classNameCn = classNameCn;
            this.confidence = confidence;
            this.elapsedMs = elapsedMs;
            this.objectName = objectName;
            this.error = error;
        }
    }
}
