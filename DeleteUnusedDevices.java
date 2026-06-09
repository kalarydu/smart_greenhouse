import java.sql.*;

/**
 * 临时脚本：删除多余的测试设备，只保留一号大棚的风机和补光灯
 */
public class DeleteUnusedDevices {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:mysql://localhost:3306/greenhouse?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai";
        String user = "root";
        String pass = "050516";

        Class.forName("com.mysql.cj.jdbc.Driver");

        try (Connection conn = DriverManager.getConnection(url, user, pass);
             Statement stmt = conn.createStatement()) {

            // 先看看有哪些设备
            System.out.println("=== 删除前 ===");
            ResultSet rs1 = stmt.executeQuery("SELECT id, greenhouse_id, device_name, device_type, status FROM gh_device");
            while (rs1.next()) {
                System.out.printf("id=%d 大棚=%d 名称=%s 类型=%s 状态=%d%n",
                        rs1.getLong(1), rs1.getLong(2), rs1.getString(3),
                        rs1.getString(4), rs1.getInt(5));
            }
            rs1.close();

            // 删除非一号大棚 或 非FAN/LIGHT 的设备
            int deleted = stmt.executeUpdate(
                "DELETE FROM gh_device WHERE NOT (greenhouse_id = 1 AND device_type IN ('FAN', 'LIGHT'))"
            );
            System.out.println("\n已删除 " + deleted + " 条多余设备记录");

            // 确认结果
            System.out.println("\n=== 删除后 ===");
            ResultSet rs2 = stmt.executeQuery("SELECT id, greenhouse_id, device_name, device_type, status FROM gh_device");
            while (rs2.next()) {
                System.out.printf("id=%d 大棚=%d 名称=%s 类型=%s 状态=%d%n",
                        rs2.getLong(1), rs2.getLong(2), rs2.getString(3),
                        rs2.getString(4), rs2.getInt(5));
            }
            rs2.close();
        }
    }
}
