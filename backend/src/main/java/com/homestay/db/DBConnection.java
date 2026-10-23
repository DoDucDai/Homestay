package com.homestay.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Ket noi SQL Server bang JDBC
 * NGUOI LAM: DUC DAI (LEADER)
 *
 * Config duoc doc tu file .env o thu muc goc du an.
 * Neu khong co .env, dung gia tri mac dinh trong application.properties.
 *
 * De test ket noi: chay ham main() duoi day.
 */
public class DBConnection {

    private static final java.util.Map<String, String> ENV_FILE_VARS = loadEnvFile();

    private static java.util.Map<String, String> loadEnvFile() {
        java.util.Map<String, String> map = new java.util.HashMap<>();
        java.io.File[] files = new java.io.File[] {
            new java.io.File(".env"),
            new java.io.File("../.env"),
            new java.io.File("backend/.env")
        };
        for (java.io.File file : files) {
            if (file.exists() && file.isFile()) {
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.FileReader(file, java.nio.charset.StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) continue;
                        int idx = line.indexOf('=');
                        String k = line.substring(0, idx).trim();
                        String v = line.substring(idx + 1).trim();
                        if (v.startsWith("\"") && v.endsWith("\"") && v.length() >= 2) {
                            v = v.substring(1, v.length() - 1);
                        } else if (v.startsWith("'") && v.endsWith("'") && v.length() >= 2) {
                            v = v.substring(1, v.length() - 1);
                        }
                        map.putIfAbsent(k, v);
                    }
                } catch (Exception ignored) {}
            }
        }
        return map;
    }

    private static String getEnv(String key, String defaultValue) {
        String val = System.getenv(key);
        if (val != null && !val.trim().isEmpty()) return val;
        val = System.getProperty(key);
        if (val != null && !val.trim().isEmpty()) return val;
        val = ENV_FILE_VARS.get(key);
        if (val != null && !val.trim().isEmpty()) return val;
        return defaultValue;
    }

    // Doc tu bien moi truong (duoc nap tu file .env)
    private static final String DB_HOST = getEnv("DB_HOST", "localhost");
    private static final String DB_PORT = getEnv("DB_PORT", "56537");
    private static final String DB_NAME = getEnv("DB_NAME", "HomestayDB");
    private static final String DB_USER = getEnv("DB_USER", "sa");
    private static final String DB_PASSWORD = getEnv("DB_PASSWORD", "123456");

    private static final String DB_URL =
        "jdbc:sqlserver://" + DB_HOST + ":" + DB_PORT
        + ";databaseName=" + DB_NAME
        + ";encrypt=true;trustServerCertificate=true";

    public static Connection getConnection() throws SQLException {
        try {
            Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
            return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
        } catch (ClassNotFoundException e) {
            throw new SQLException("Khong tim thay SQL Server JDBC Driver!", e);
        }
    }

    // Test ket noi - chay ham main nay de kiem tra
    public static void main(String[] args) {
        System.out.println(">>> Dang ket noi toi: " + DB_URL);
        System.out.println(">>> User: " + DB_USER);
        try (Connection conn = getConnection()) {
            System.out.println(">>> Ket noi SQL Server THANH CONG!");
        } catch (SQLException e) {
            System.out.println(">>> LOI ket noi: " + e.getMessage());
            System.out.println(">>> Kiem tra lai file .env va SQL Server co dang chay khong.");
        }
    }
}
