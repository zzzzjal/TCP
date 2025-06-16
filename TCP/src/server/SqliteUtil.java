package server;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class SqliteUtil {
    private static final String DB_URL = "jdbc:sqlite:data/messages.db";
    private static final String CREATE_CHAT_LOG_TABLE =
            "CREATE TABLE IF NOT EXISTS chat_log (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "client_addr TEXT NOT NULL, " +
                    "message TEXT NOT NULL, " +
                    "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP" +
                    ");";

    private static final String CREATE_FILE_LOG_TABLE =
            "CREATE TABLE IF NOT EXISTS file_log (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "client_addr TEXT NOT NULL, " +
                    "filename TEXT NOT NULL, " +
                    "path TEXT NOT NULL, " +
                    "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP" +
                    ");";

    static {
        try {
            Class.forName("org.sqlite.JDBC");
            Files.createDirectories(Paths.get("data"));
        } catch (ClassNotFoundException e) {
            System.err.println("找不到SQLite JDBC驱动");
            System.exit(1);
        } catch (IOException e) {
            System.err.println("无法创建数据目录: " + e.getMessage());
        }
    }

    private SqliteUtil() {} // 防止实例化

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL + "?busy_timeout=3000");
    }

    public static void initDatabase() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute(CREATE_CHAT_LOG_TABLE);
            stmt.execute(CREATE_FILE_LOG_TABLE);

            System.out.println("数据库表初始化完成");
        } catch (SQLException e) {
            System.err.println("数据库初始化失败: " + e.getMessage());
        }
    }
}