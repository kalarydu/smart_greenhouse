import java.sql.*;

public class CleanSensorData {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:mysql://localhost:3306/greenhouse?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai";
        String user = "root";
        String pass = "050516";

        Class.forName("com.mysql.cj.jdbc.Driver");
        try (Connection conn = DriverManager.getConnection(url, user, pass);
             Statement stmt = conn.createStatement()) {

            // 查看当前数量
            ResultSet rs1 = stmt.executeQuery("SELECT COUNT(*) FROM gh_sensor_data WHERE greenhouse_id = 1");
            rs1.next();
            int before = rs1.getInt(1);
            rs1.close();
            System.out.println("一号大棚删除前: " + before + " 条");

            // 删除
            int deleted = stmt.executeUpdate("DELETE FROM gh_sensor_data WHERE greenhouse_id = 1");
            System.out.println("已删除: " + deleted + " 条");

            // 确认
            ResultSet rs2 = stmt.executeQuery("SELECT COUNT(*) FROM gh_sensor_data WHERE greenhouse_id = 1");
            rs2.next();
            System.out.println("一号大棚剩余: " + rs2.getInt(1) + " 条");
            rs2.close();
        }
    }
}
