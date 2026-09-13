package com.homestay.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Ket noi SQL Server bang JDBC
 * NGUOI LAM: DUC DAI
 *
 * Sau khi tao database trong SQL Server, chinh lai:
 * - DB_URL: ten server va ten database
 * - DB_USER, DB_PASSWORD: tai khoan SQL Server
 */
public class DBConnection {

    // === CHINH LAI 3 DONG NAY CHO DUNG VOI MAY TINH CUA BAN ===
    private static final String DB_URL      = "jdbc:sqlserver://localhost:1433;databaseName=HomestayDB;encrypt=true;trustServerCertificate=true";
    private static final String DB_USER     = "sa";
    private static final String DB_PASSWORD = "123456"; // doi lai mat khau SQL Server cua ban

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
        try (Connection conn = getConnection()) {
            System.out.println(">>> Ket noi SQL Server THANH CONG!");
        } catch (SQLException e) {
            System.out.println(">>> LOI ket noi: " + e.getMessage());
        }
    }
}
