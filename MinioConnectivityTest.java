import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.Result;
import io.minio.messages.Item;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * MinIO 连通性 & 图片拉取测试（纯 Java，不依赖 Spring Boot，直接用 MinIO SDK）
 *
 * 编译：javac -cp "target/dependency/*" MinioConnectivityTest.java
 * 运行：java -cp ".;target/dependency/*" MinioConnectivityTest
 */
public class MinioConnectivityTest {

    static final String ENDPOINT = "http://10.190.83.10:9000";
    static final String ACCESS_KEY = "minioadmin";
    static final String SECRET_KEY = "minioadmin";

    public static void main(String[] args) {
        System.out.println("========== MinIO 连通性测试 ==========\n");

        MinioClient client = MinioClient.builder()
                .endpoint(ENDPOINT)
                .credentials(ACCESS_KEY, SECRET_KEY)
                .build();

        try {
            // 1. 列举所有 bucket
            System.out.println("【1】Bucket 列表：");
            client.listBuckets().forEach(b -> System.out.println("  📁 " + b.name()));

            // 2. 列举 crops/test 前缀的对象
            System.out.println("\n【2】crops/test/ 下的对象（前20个）：");
            int count = 0;
            for (Result<Item> r : client.listObjects(
                    ListObjectsArgs.builder().bucket("crops").prefix("test/").recursive(true).build())) {
                Item item = r.get();
                if (!item.isDir()) {
                    System.out.printf("  📄 %s (%,d bytes)%n", item.objectName(), item.size());
                    count++;
                    if (count >= 20) {
                        System.out.println("  ... (仅显示前20个)");
                        break;
                    }
                }
            }
            System.out.println("  共列举 " + count + " 个文件");

            // 3. 尝试拉取第一张图片到内存
            if (count > 0) {
                // 先拿到第一个对象名
                String firstObject = null;
                for (Result<Item> r : client.listObjects(
                        ListObjectsArgs.builder().bucket("crops").prefix("test/").recursive(true).build())) {
                    Item item = r.get();
                    if (!item.isDir()) {
                        firstObject = item.objectName();
                        break;
                    }
                }

                System.out.println("\n【3】拉取单张图片到内存：");
                System.out.println("  对象: " + firstObject);
                long start = System.currentTimeMillis();
                try (InputStream is = client.getObject(
                        GetObjectArgs.builder().bucket("crops").object(firstObject).build())) {
                    ByteArrayOutputStream buf = new ByteArrayOutputStream();
                    byte[] tmp = new byte[8192];
                    int n;
                    while ((n = is.read(tmp)) != -1) buf.write(tmp, 0, n);
                    byte[] data = buf.toByteArray();
                    long elapsed = System.currentTimeMillis() - start;
                    System.out.printf("  ✅ 成功！大小: %,d bytes，耗时: %d ms%n", data.length, elapsed);
                    // 验证文件头是不是 PNG
                    if (data.length >= 8) {
                        String hex = String.format("%02X %02X %02X %02X", data[0], data[1], data[2], data[3]);
                        System.out.println("  文件头: " + hex + " (PNG = 89 50 4E 47)");
                    }
                }
            }

            System.out.println("\n========== 测试完成 ✅ ==========");

        } catch (Exception e) {
            System.err.println("❌ 测试失败: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
