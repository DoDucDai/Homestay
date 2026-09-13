package com.homestay.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Ket noi SQL Server bang JDBC
 * NGUOI LAM: DUC DAI
 *
 * Config duoc doc tu file .env o thu muc goc du an.
 * Neu khong co .env, dung gia tri mac dinh trong application.properties.
 *
 * De test ket noi: chay ham main() duoi day.
 */
public class DBConnection {

    // Doc tu bien moi truong (duoc nap tu file .env qua spring-dotenv)
    private static final String DB_HOST = System.getenv("DB_HOST") != null ? System.getenv("DB_HOST") : "localhost";
    private static final String DB_PORT = System.getenv("DB_PORT") != null ? System.getenv("DB_PORT") : "1433";
    private static final String DB_NAME = System.getenv("DB_NAME") != null ? System.getenv("DB_NAME") : "HomestayDB";
    private static final String DB_USER = System.getenv("DB_USER") != null ? System.getenv("DB_USER") : "sa";
    private static final String DB_PASSWORD = System.getenv("DB_PASSWORD") != null ? System.getenv("DB_PASSWORD") : "123456";

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
