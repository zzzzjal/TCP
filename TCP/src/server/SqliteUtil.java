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
    private static final String DB_FILE = "data/messages.db";
    private static final String DB_URL = "jdbc:sqlite:" + DB_FILE;
    private static final String CREATE_CHAT_LOG_TABLE =
            "CREATE TABLE IF NOT EXISTS chat_logs (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "client_address TEXT NOT NULL, " +
                    "message TEXT NOT NULL, " +
                    "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP" +
                    ");";

    private static final String CREATE_FILE_LOG_TABLE =
            "CREATE TABLE IF NOT EXISTS file_logs (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "client_address TEXT NOT NULL, " +
                    "filename TEXT NOT NULL, " +
                    "file_path TEXT NOT NULL, " +
                    "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP" +
                    ");";

    static {
        try {
            Class.forName("org.sqlite.JDBC");
            Files.createDirectories(Paths.get("data")); // Ensure data directory exists
        } catch (ClassNotFoundException e) {
            System.err.println("Error: SQLite JDBC driver not found.");
            System.exit(1);
        } catch (IOException e) {
            System.err.println("Error creating data directory: " + e.getMessage());
        }
    }

    private SqliteUtil() {} // Prevents instantiation

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL + "?busy_timeout=3000");
    }

    public static void initDatabase() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(CREATE_CHAT_LOG_TABLE);
            stmt.execute(CREATE_FILE_LOG_TABLE);
            System.out.println("SQLite database initialized successfully.");
        } catch (SQLException e) {
            System.err.println("Error initializing database: " + e.getMessage());
        }
    }
}