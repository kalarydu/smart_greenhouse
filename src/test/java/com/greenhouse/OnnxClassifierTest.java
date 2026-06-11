package com.greenhouse;

import com.greenhouse.onnx.service.ClassifierService;
import com.greenhouse.service.MinioService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ONNX 农作物图像分类测试
 *
 * <h3>使用方式</h3>
 * <pre>
 *   # 运行全部测试（不含定时模式）
 *   ./mvnw test -Dtest=OnnxClassifierTest
 *
 *   # 只跑 MinIO 单张图片测试
 *   ./mvnw test -Dtest=OnnxClassifierTest#testMinioSingleImage
 *
 *   # 只跑 MinIO 批量测试
 *   ./mvnw test -Dtest=OnnxClassifierTest#testMinioBatch
 *
 *   # ⭐ 定时自动拉取模式（后端持续运行，按间隔轮询 MinIO）
 *   ./mvnw exec:java -Dexec.mainClass=com.greenhouse.OnnxClassifierTest \
 *     -Dexec.classpathScope=test -Dexec.args="--batch-size=5 --interval=10 --rounds=3"
 * </pre>
 *
 * <h3>定时模式参数说明</h3>
 * <ul>
 *   <li>{@code --batch-size=N}  每轮拉取多少张图片（默认 5，防止一次性拿太多）</li>
 *   <li>{@code --interval=N}    轮询间隔秒数（默认 30）</li>
 *   <li>{@code --rounds=N}      总共跑几轮（默认 0 = 无限循环直到 Ctrl+C）</li>
 *   <li>{@code --bucket=xxx}    MinIO bucket 名称（默认 crops）</li>
 *   <li>{@code --prefix=xxx}    MinIO 前缀（默认 test/）</li>
 * </ul>
 */
@SpringBootTest(properties = {
    // 测试环境关闭 MQTT 连接，避免华为云 MQTT 线程阻止 JVM 优雅关闭
    "spring.mqtt.host=disabled",
    // 关闭 SQL 日志避免刷屏
    "mybatis-plus.configuration.log-impl="
})
class OnnxClassifierTest {

    @Autowired
    private ClassifierService classifierService;

    @Autowired
    private MinioService minioService;

    // ═══════════════════════════════════════════════════════════
    // 本地文件测试配置（原有）
    // ═══════════════════════════════════════════════════════════

    private static final String SINGLE_IMAGE_PATH =
            "F:\\internship\\javaworkspace\\demo\\src\\test\\test_images\\cotton\\flowering.png";

    private static final String SCAN_DIR_PATH =
            "F:\\internship\\javaworkspace\\demo\\src\\test\\test_images";

    private static final int SCAN_MAX_IMAGES = 0;

    // ═══════════════════════════════════════════════════════════
    // MinIO 测试配置
    // ═══════════════════════════════════════════════════════════

    private static final String MINIO_BUCKET = "crops";
    private static final String MINIO_PREFIX = "test/";
    private static final int MINIO_BATCH_SIZE = 10;        // 每次最多拉取数量
    private static final String RESULT_DIR = "test/result"; // 结果保存目录

    private static final Set<String> IMAGE_EXTENSIONS =
            Set.of(".jpg", ".jpeg", ".png", ".bmp", ".gif");

    // ═══════════════════════════════════════════════════════════
    // 1. 健康检查 & 类别查询（原有）
    // ═══════════════════════════════════════════════════════════

    @Test
    @DisplayName("1. 健康检查 - 确认模型加载成功")
    void testHealth() {
        Map<String, Object> classes = classifierService.getClasses();
        assertNotNull(classes);
        assertEquals(14, classes.get("total"), "应支持 14 个类别");
        System.out.println("✅ 模型已加载，支持 " + classes.get("total") + " 个类别");
    }

    @Test
    @DisplayName("2. 获取类别列表")
    void testGetClasses() {
        Map<String, Object> classes = classifierService.getClasses();
        System.out.println("========== 支持的 14 个类别 ==========");
        @SuppressWarnings("unchecked")
        var list = (java.util.List<Map<String, String>>) classes.get("classes");
        for (Map<String, String> item : list) {
            System.out.printf("  [%2s] %s (%s)%n",
                    item.get("id"), item.get("nameCn"), item.get("nameEn"));
        }
        System.out.println("=======================================");
        assertEquals(14, list.size());
    }

    // ═══════════════════════════════════════════════════════════
    // 2. 本地单张图片测试（原有）
    // ═══════════════════════════════════════════════════════════

    @Test
    @DisplayName("3. 单张图片预测")
    void testSingleImage() {
        File file = new File(SINGLE_IMAGE_PATH);
        if (!file.exists()) {
            skip("图片不存在: " + SINGLE_IMAGE_PATH);
            return;
        }
        try {
            long start = System.currentTimeMillis();
            Map<String, Object> result = classifierService.predict(SINGLE_IMAGE_PATH);
            long elapsed = System.currentTimeMillis() - start;
            printSingleResult(result, file.getName(), elapsed);
            assertNotNull(result.get("classId"));
            double conf = (Double) result.get("confidence");
            assertTrue(conf >= 0.0 && conf <= 1.0, "置信度应在 [0,1] 范围内");
        } catch (Exception e) {
            fail("预测失败: " + e.getMessage(), e);
        }
    }

    @Test
    @DisplayName("4. 单张图片连续 5 次预测（稳定性验证）")
    void testSingleImageRepeat() {
        File file = new File(SINGLE_IMAGE_PATH);
        if (!file.exists()) {
            skip("图片不存在: " + SINGLE_IMAGE_PATH);
            return;
        }
        try {
            System.out.println("========== 连续 5 次预测 ==========");
            long totalMs = 0;
            for (int i = 1; i <= 5; i++) {
                long start = System.currentTimeMillis();
                Map<String, Object> result = classifierService.predict(SINGLE_IMAGE_PATH);
                long elapsed = System.currentTimeMillis() - start;
                totalMs += elapsed;
                System.out.printf("  第 %d 次 | %-14s | 置信度: %6.4f | %3dms%n",
                        i, result.get("classNameCn"), result.get("confidence"), elapsed);
            }
            System.out.printf("  平均耗时: %dms%n", totalMs / 5);
            System.out.println("====================================");
        } catch (Exception e) {
            fail("多次预测失败: " + e.getMessage(), e);
        }
    }

    // ═══════════════════════════════════════════════════════════
    // 3. 本地批量目录扫描测试（原有）
    // ═══════════════════════════════════════════════════════════

    @Test
    @DisplayName("5. 批量扫描目录预测")
    void testBatchScan() {
        File dir = new File(SCAN_DIR_PATH);
        if (!dir.exists() || !dir.isDirectory()) {
            skip("目录不存在: " + SCAN_DIR_PATH);
            return;
        }
        List<File> images = collectImages(dir);
        if (images.isEmpty()) {
            skip("目录下没有图片文件");
            return;
        }
        Collections.shuffle(images, new Random(42));
        if (SCAN_MAX_IMAGES > 0 && images.size() > SCAN_MAX_IMAGES) {
            images = images.subList(0, SCAN_MAX_IMAGES);
        }
        System.out.printf("%n========== 批量预测: %d 张图片 ==========%n", images.size());
        BatchStats stats = runBatchOnFiles(images);
        printBatchSummary(stats);
        assertEquals(0, stats.failCount, "全部预测应成功");
    }

    // ═══════════════════════════════════════════════════════════
    // 4. MinIO + YOLO 联动测试（🆕）
    // ═══════════════════════════════════════════════════════════

    @Test
    @DisplayName("6. MinIO 单张图片拉取 + 分类")
    void testMinioSingleImage() {
        System.out.println("========== MinIO 单张图片测试 ==========");

        // 先从 MinIO 列举一张图片
        List<String> objects = minioService.listObjects(MINIO_BUCKET, MINIO_PREFIX);
        if (objects.isEmpty()) {
            skip("MinIO bucket=" + MINIO_BUCKET + " prefix=" + MINIO_PREFIX + " 下无对象");
            return;
        }
        // 随机选一张
        String objectName = objects.get(new Random().nextInt(objects.size()));
        System.out.println("选定对象: " + objectName);

        try {
            long t1 = System.currentTimeMillis();
            byte[] imgBytes = minioService.getImageBytes(MINIO_BUCKET, objectName);
            long t2 = System.currentTimeMillis();
            Map<String, Object> result = classifierService.predictFromMinio(MINIO_BUCKET, objectName);
            long t3 = System.currentTimeMillis();

            System.out.printf("  拉取耗时: %dms, 推理耗时: %dms, 总耗时: %dms%n",
                    t2 - t1, t3 - t2, t3 - t1);
            System.out.printf("  图片大小: %,d bytes%n", imgBytes.length);
            printSingleResult(result, objectName, t3 - t1);

            assertNotNull(result.get("classId"));
            double conf = (Double) result.get("confidence");
            assertTrue(conf >= 0.0 && conf <= 1.0);

        } catch (Exception e) {
            fail("MinIO 单张测试失败: " + e.getMessage(), e);
        }
    }

    @Test
    @DisplayName("7. MinIO 批量拉取 + 分类 + 保存结果")
    void testMinioBatch() {
        System.out.println("========== MinIO 批量测试 ==========");

        // 1. 列举对象
        List<String> objects = minioService.listObjects(MINIO_BUCKET, MINIO_PREFIX);
        System.out.println("MinIO 中共有 " + objects.size() + " 个对象 (bucket=" + MINIO_BUCKET + ", prefix=" + MINIO_PREFIX + ")");

        if (objects.isEmpty()) {
            skip("无对象可测");
            return;
        }

        // 2. 随机打乱 + 截取 batch_size，避免每次都拿同一批
        Collections.shuffle(objects, new Random());
        int batchSize = Math.min(MINIO_BATCH_SIZE, objects.size());
        objects = objects.subList(0, batchSize);
        System.out.println("本轮随机选取 " + batchSize + " 张图片");

        // 3. 逐张拉取 + 分类
        BatchStats stats = runBatchOnMinioObjects(objects);

        // 4. 保存结果到 test/result/
        saveBatchResult(stats, objects.size());

        // 5. 打印汇总
        printBatchSummary(stats);
    }

    // ═══════════════════════════════════════════════════════════
    // 批量处理核心逻辑
    // ═══════════════════════════════════════════════════════════

    /** 对本地文件列表批量预测 */
    private BatchStats runBatchOnFiles(List<File> images) {
        BatchStats stats = new BatchStats();
        for (int i = 0; i < images.size(); i++) {
            File img = images.get(i);
            try {
                long start = System.currentTimeMillis();
                Map<String, Object> result = classifierService.predict(img.getAbsolutePath());
                long elapsed = System.currentTimeMillis() - start;
                double conf = (Double) result.get("confidence");
                String cnName = (String) result.get("classNameCn");
                stats.records.add(new BatchRecord(img.getName(), cnName, conf, elapsed, null));
                stats.successCount++;
                stats.totalMs += elapsed;
                System.out.printf("  [%3d/%3d] %-40s -> %-12s  %.4f  %4dms%n",
                        i + 1, images.size(), truncate(img.getName(), 40),
                        cnName, conf, elapsed);
            } catch (Exception e) {
                stats.failCount++;
                String err = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                stats.records.add(new BatchRecord(img.getName(), "ERR", 0, 0, err));
                System.out.printf("  [%3d/%3d] %-40s -> ❌ %s%n",
                        i + 1, images.size(), truncate(img.getName(), 40), truncate(err, 50));
            }
        }
        return stats;
    }

    /** 对 MinIO 对象列表批量拉取 + 预测 */
    private BatchStats runBatchOnMinioObjects(List<String> objectNames) {
        BatchStats stats = new BatchStats();
        for (int i = 0; i < objectNames.size(); i++) {
            String objectName = objectNames.get(i);
            String shortName = Paths.get(objectName).getFileName().toString();
            try {
                long start = System.currentTimeMillis();
                Map<String, Object> result = classifierService.predictFromMinio(MINIO_BUCKET, objectName);
                long elapsed = System.currentTimeMillis() - start;
                double conf = (Double) result.get("confidence");
                String cnName = (String) result.get("classNameCn");
                stats.records.add(new BatchRecord(shortName, cnName, conf, elapsed, null));
                stats.successCount++;
                stats.totalMs += elapsed;
                System.out.printf("  [%3d/%3d] %-40s -> %-14s  conf=%.4f  %4dms%n",
                        i + 1, objectNames.size(), truncate(shortName, 40),
                        cnName, conf, elapsed);
            } catch (Exception e) {
                stats.failCount++;
                String err = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                stats.records.add(new BatchRecord(shortName, "ERR", 0, 0, err));
                System.out.printf("  [%3d/%3d] %-40s -> ❌ %s%n",
                        i + 1, objectNames.size(), truncate(shortName, 40), truncate(err, 50));
            }
        }
        return stats;
    }

    // ═══════════════════════════════════════════════════════════
    // 结果保存
    // ═══════════════════════════════════════════════════════════

    /**
     * 将批量结果保存为 JSON 文件到 test/result/
     */
    private void saveBatchResult(BatchStats stats, int totalFetched) {
        try {
            Path resultDir = Paths.get(RESULT_DIR);
            Files.createDirectories(resultDir);

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = "result_" + timestamp + ".json";
            Path resultFile = resultDir.resolve(filename);

            StringBuilder json = new StringBuilder();
            json.append("{\n");
            json.append("  \"timestamp\": \"").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\",\n");
            json.append("  \"bucket\": \"").append(MINIO_BUCKET).append("\",\n");
            json.append("  \"prefix\": \"").append(MINIO_PREFIX).append("\",\n");
            json.append("  \"totalFetched\": ").append(totalFetched).append(",\n");
            json.append("  \"successCount\": ").append(stats.successCount).append(",\n");
            json.append("  \"failCount\": ").append(stats.failCount).append(",\n");
            json.append("  \"totalMs\": ").append(stats.totalMs).append(",\n");
            if (stats.successCount > 0) {
                json.append("  \"avgMs\": ").append(stats.totalMs / stats.successCount).append(",\n");
                double avgConf = stats.records.stream()
                        .filter(r -> r.error == null)
                        .mapToDouble(r -> r.confidence)
                        .average().orElse(0);
                json.append("  \"avgConfidence\": ").append(String.format("%.4f", avgConf)).append(",\n");

                // 类别分布
                Map<String, Long> dist = stats.records.stream()
                        .filter(r -> r.error == null)
                        .collect(Collectors.groupingBy(r -> r.classNameCn, LinkedHashMap::new, Collectors.counting()));
                json.append("  \"classDistribution\": {\n");
                List<String> entries = new ArrayList<>();
                dist.forEach((cn, count) -> entries.add("    \"" + cn + "\": " + count));
                json.append(String.join(",\n", entries));
                json.append("\n  },\n");
            }
            json.append("  \"records\": [\n");
            for (int i = 0; i < stats.records.size(); i++) {
                BatchRecord r = stats.records.get(i);
                json.append("    {");
                json.append("\"fileName\": \"").append(escapeJson(r.fileName)).append("\", ");
                json.append("\"classNameCn\": \"").append(r.classNameCn).append("\", ");
                json.append("\"confidence\": ").append(String.format("%.4f", r.confidence)).append(", ");
                json.append("\"elapsedMs\": ").append(r.elapsedMs);
                if (r.error != null) {
                    json.append(", \"error\": \"").append(escapeJson(r.error)).append("\"");
                }
                json.append("}");
                if (i < stats.records.size() - 1) json.append(",");
                json.append("\n");
            }
            json.append("  ]\n");
            json.append("}\n");

            Files.writeString(resultFile, json.toString());
            System.out.println("\n📁 结果已保存: " + resultFile.toAbsolutePath());

        } catch (IOException e) {
            System.err.println("⚠️ 保存结果文件失败: " + e.getMessage());
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // ═══════════════════════════════════════════════════════════
    // 5. 定时自动拉取模式（main 入口，非 @Test）
    // ═══════════════════════════════════════════════════════════

    /**
     * 定时自动从 MinIO 拉取图片 + YOLO 分类 + 保存结果。
     *
     * <pre>
     * 启动命令:
     *   mvn test-compile exec:java \
     *     -Dexec.mainClass=com.greenhouse.OnnxClassifierTest \
     *     -Dexec.classpathScope=test \
     *     -Dexec.args="--batch-size=5 --interval=10 --rounds=3"
     * </pre>
     */
    public static void main(String[] args) {
        // 解析参数
        int batchSize = 5;
        int intervalSec = 30;
        int rounds = 0; // 0 = 无限循环
        String bucket = MINIO_BUCKET;
        String prefix = MINIO_PREFIX;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--batch-size": batchSize = Integer.parseInt(args[++i]); break;
                case "--interval":   intervalSec = Integer.parseInt(args[++i]); break;
                case "--rounds":     rounds = Integer.parseInt(args[++i]); break;
                case "--bucket":     bucket = args[++i]; break;
                case "--prefix":     prefix = args[++i]; break;
            }
        }

        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║   MinIO + YOLO 定时自动分类任务          ║");
        System.out.println("╠══════════════════════════════════════════╣");
        System.out.printf("║  Bucket:     %-30s║%n", bucket);
        System.out.printf("║  Prefix:     %-30s║%n", prefix);
        System.out.printf("║  每轮数量:   %-3d 张                       ║%n", batchSize);
        System.out.printf("║  间隔时间:   %-3d 秒                       ║%n", intervalSec);
        System.out.printf("║  总轮次:     %s                          ║%n",
                rounds == 0 ? "无限（Ctrl+C 停止）" : rounds + " 轮");
        System.out.println("╚══════════════════════════════════════════╝");

        ConfigurableApplicationContext ctx = SpringApplication.run(DemoApplication.class, args);
        ClassifierService classifierService = ctx.getBean(ClassifierService.class);
        MinioService minioService = ctx.getBean(MinioService.class);

        int round = 0;
        String finalBucket = bucket;
        String finalPrefix = prefix;

        try {
            while (rounds == 0 || round < rounds) {
                round++;
                System.out.println();
                System.out.println("═══════════════════════════════════════════");
                System.out.println("  📍 第 " + round + " 轮" + (rounds > 0 ? "/" + rounds : "") +
                        " — " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                System.out.println("═══════════════════════════════════════════");

                try {
                    // 列举对象
                    List<String> objects = minioService.listObjects(finalBucket, finalPrefix);
                    System.out.println("  MinIO 可用对象: " + objects.size() + " 个");

                    if (!objects.isEmpty()) {
                        // 随机选取
                        Collections.shuffle(objects, new Random());
                        int take = Math.min(batchSize, objects.size());
                        List<String> selected = objects.subList(0, take);

                        // 批量预测
                        BatchStats stats = new BatchStats();
                        stats.successCount = 0;
                        stats.failCount = 0;
                        stats.totalMs = 0;
                        stats.records = new ArrayList<>();

                        for (int i = 0; i < selected.size(); i++) {
                            String objectName = selected.get(i);
                            String shortName = Paths.get(objectName).getFileName().toString();
                            try {
                                long t0 = System.currentTimeMillis();
                                Map<String, Object> result = classifierService.predictFromMinio(finalBucket, objectName);
                                long elapsed = System.currentTimeMillis() - t0;
                                double conf = (Double) result.get("confidence");
                                String cnName = (String) result.get("classNameCn");
                                stats.records.add(new BatchRecord(shortName, cnName, conf, elapsed, null));
                                stats.successCount++;
                                stats.totalMs += elapsed;
                                System.out.printf("    [%2d/%2d] %-38s -> %-14s  conf=%.4f  %4dms%n",
                                        i + 1, selected.size(), truncate(shortName, 38),
                                        cnName, conf, elapsed);
                            } catch (Exception e) {
                                stats.failCount++;
                                String err = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                                stats.records.add(new BatchRecord(shortName, "ERR", 0, 0, err));
                                System.out.printf("    [%2d/%2d] %-38s -> ❌ %s%n",
                                        i + 1, selected.size(), truncate(shortName, 38),
                                        truncate(err, 40));
                            }
                        }

                        // 每轮都保存结果
                        saveResultForRound(stats, round, selected.size(), finalBucket, finalPrefix);
                        printBatchSummary(stats);
                    } else {
                        System.out.println("  ⚠️ 无可用对象，等待下一轮...");
                    }

                } catch (Exception e) {
                    System.err.println("  ❌ 第 " + round + " 轮异常: " + e.getMessage());
                }

                // 最后一轮不等待
                if (rounds > 0 && round >= rounds) {
                    System.out.println("\n✅ 已完成全部 " + rounds + " 轮任务");
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
        } finally {
            ctx.close();
        }
    }

    private static void saveResultForRound(BatchStats stats, int round, int totalFetched,
                                            String bucket, String prefix) {
        try {
            Path resultDir = Paths.get(RESULT_DIR);
            Files.createDirectories(resultDir);

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = "round_" + String.format("%03d", round) + "_" + timestamp + ".json";
            Path resultFile = resultDir.resolve(filename);

            StringBuilder json = new StringBuilder();
            json.append("{\n");
            json.append("  \"round\": ").append(round).append(",\n");
            json.append("  \"timestamp\": \"").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\",\n");
            json.append("  \"bucket\": \"").append(bucket).append("\",\n");
            json.append("  \"prefix\": \"").append(prefix).append("\",\n");
            json.append("  \"totalFetched\": ").append(totalFetched).append(",\n");
            json.append("  \"successCount\": ").append(stats.successCount).append(",\n");
            json.append("  \"failCount\": ").append(stats.failCount).append(",\n");
            json.append("  \"totalMs\": ").append(stats.totalMs).append(",\n");
            if (stats.successCount > 0) {
                json.append("  \"avgMs\": ").append(stats.totalMs / stats.successCount).append(",\n");
                double avgConf = stats.records.stream()
                        .filter(r -> r.error == null)
                        .mapToDouble(r -> r.confidence)
                        .average().orElse(0);
                json.append("  \"avgConfidence\": ").append(String.format("%.4f", avgConf)).append(",\n");

                Map<String, Long> dist = stats.records.stream()
                        .filter(r -> r.error == null)
                        .collect(Collectors.groupingBy(r -> r.classNameCn, LinkedHashMap::new, Collectors.counting()));
                json.append("  \"classDistribution\": {\n");
                List<String> entries = new ArrayList<>();
                dist.forEach((cn, count) -> entries.add("    \"" + cn + "\": " + count));
                json.append(String.join(",\n", entries));
                json.append("\n  },\n");
            }
            json.append("  \"records\": [\n");
            for (int i = 0; i < stats.records.size(); i++) {
                BatchRecord r = stats.records.get(i);
                json.append("    {");
                json.append("\"fileName\": \"").append(r.fileName.replace("\\", "\\\\").replace("\"", "\\\"")).append("\", ");
                json.append("\"classNameCn\": \"").append(r.classNameCn).append("\", ");
                json.append("\"confidence\": ").append(String.format("%.4f", r.confidence)).append(", ");
                json.append("\"elapsedMs\": ").append(r.elapsedMs);
                if (r.error != null) {
                    json.append(", \"error\": \"").append(r.error.replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
                }
                json.append("}");
                if (i < stats.records.size() - 1) json.append(",");
                json.append("\n");
            }
            json.append("  ]\n");
            json.append("}\n");

            Files.writeString(resultFile, json.toString());
            System.out.println("  📁 结果已保存: " + resultFile.toAbsolutePath());

        } catch (IOException e) {
            System.err.println("  ⚠️ 保存失败: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════
    // 辅助方法
    // ═══════════════════════════════════════════════════════════

    private List<File> collectImages(File dir) {
        List<File> result = new ArrayList<>();
        File[] children = dir.listFiles();
        if (children == null) return result;
        for (File f : children) {
            if (f.isDirectory()) {
                result.addAll(collectImages(f));
            } else if (isImageFile(f)) {
                result.add(f);
            }
        }
        result.sort(Comparator.comparing(File::getName));
        return result;
    }

    private boolean isImageFile(File f) {
        String name = f.getName().toLowerCase();
        int dot = name.lastIndexOf('.');
        return dot >= 0 && IMAGE_EXTENSIONS.contains(name.substring(dot));
    }

    private void printSingleResult(Map<String, Object> r, String fileName, long elapsedMs) {
        System.out.println();
        System.out.println("========== 预测结果 ==========");
        System.out.printf("  文件:     %s%n", fileName);
        System.out.printf("  类别ID:   %s%n", r.get("classId"));
        System.out.printf("  英文名:   %s%n", r.get("classNameEn"));
        System.out.printf("  中文名:   %s%n", r.get("classNameCn"));
        System.out.printf("  置信度:   %.4f%n", r.get("confidence"));
        System.out.printf("  耗时:     %dms%n", elapsedMs);
        System.out.println("==============================");
        System.out.println();
    }

    private static void printBatchSummary(BatchStats stats) {
        System.out.println();
        System.out.println("========== 批量预测汇总 ==========");
        System.out.printf("  总图片数:   %d%n", stats.records.size());
        System.out.printf("  成功:       %d%n", stats.successCount);
        System.out.printf("  失败:       %d%n", stats.failCount);
        System.out.printf("  总耗时:     %dms%n", stats.totalMs);
        if (stats.successCount > 0) {
            System.out.printf("  平均耗时:   %dms/张%n", stats.totalMs / stats.successCount);
            Map<String, Long> classDist = stats.records.stream()
                    .filter(r -> r.error == null)
                    .collect(Collectors.groupingBy(r -> r.classNameCn, LinkedHashMap::new, Collectors.counting()));
            System.out.println("  类别分布:");
            classDist.forEach((cn, count) ->
                    System.out.printf("    %-16s %d 张%n", cn, count));
            double avgConf = stats.records.stream()
                    .filter(r -> r.error == null)
                    .mapToDouble(r -> r.confidence)
                    .average().orElse(0);
            System.out.printf("  平均置信度: %.4f%n", avgConf);
        }
        System.out.println("================================");
    }

    private void skip(String reason) {
        System.out.println("⚠️ 跳过测试：" + reason);
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "null";
        return s.length() <= maxLen ? s : s.substring(0, maxLen - 3) + "...";
    }

    // ═══════════════════════════════════════════════════════════
    // 内部类
    // ═══════════════════════════════════════════════════════════

    private static class BatchStats {
        List<BatchRecord> records = new ArrayList<>();
        int successCount = 0;
        int failCount = 0;
        long totalMs = 0;
    }

    private static class BatchRecord {
        final String fileName;
        final String classNameCn;
        final double confidence;
        final long elapsedMs;
        final String error;

        BatchRecord(String fileName, String classNameCn, double confidence,
                    long elapsedMs, String error) {
            this.fileName = fileName;
            this.classNameCn = classNameCn;
            this.confidence = confidence;
            this.elapsedMs = elapsedMs;
            this.error = error;
        }
    }
}
