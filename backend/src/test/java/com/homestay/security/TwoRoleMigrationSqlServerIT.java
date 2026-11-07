package com.homestay.security;

import com.homestay.db.*;
import java.sql.*;
import java.util.UUID;

/** Exercise legacy conversion against real SQL in a disposable database. */
// NOTE KIỂM THỬ: Kiểm tra chuyển staff/member sang customer, giữ ID/mật khẩu, tăng phiên bản phiên đúng một lần và chặn quyền đã bỏ.
// File này thuộc src/test, không phải endpoint và không nằm trong mã nghiệp vụ chạy phục vụ người dùng.
public final class TwoRoleMigrationSqlServerIT {
    private static void require(boolean value,String message) {
        if(!value)throw new AssertionError(message);
    }
    // NOTE KIỂM THỬ: Điểm chạy nhóm kiểm tra; dùng AssertionError/kết thúc khác 0 để báo điều kiện không đạt.
    public static void main(String[] args) throws Exception {
        String database="HomestayRolesTest_"+UUID.randomUUID().toString().replace("-","");
        try(var connection=DBConnection.getConnection();var statement=connection.createStatement()) {
            statement.execute("CREATE DATABASE ["+database+"]");
            try {
                statement.execute("USE ["+database+"]");
                statement.execute("CREATE TABLE Users(user_id INT PRIMARY KEY,role NVARCHAR(20) NOT NULL DEFAULT 'staff',auth_version INT NOT NULL DEFAULT 0,password VARCHAR(100)); INSERT Users VALUES(1,'admin',4,'admin-hash'),(2,'staff',5,'staff-hash'),(3,'member',6,'member-hash'),(4,'customer',7,'customer-hash')");
                TwoRoleMigration.migrate(connection);
                TwoRoleMigration.migrate(connection);
                try(var rows=statement.executeQuery("SELECT user_id,role,auth_version,password FROM Users ORDER BY user_id")) {
                    String[] passwords={"admin-hash","staff-hash","member-hash","customer-hash"};
                    int[] versions={4,6,7,7};
                    int count=0;
                    while(rows.next()) {
                        require(rows.getInt(1)==count+1,"Account id changed");
                        require((count==0?"admin":"customer").equals(rows.getString(2)),"Legacy role survived");
                        require(rows.getInt(3)==versions[count],"Session version changed incorrectly or twice");
                        require(passwords[count].equals(rows.getString(4)),"Password changed");count++;
                    }
                    require(count==4,"Account lost during migration");
                }
                statement.execute("INSERT Users(user_id) VALUES(5)");
                try(var rows=statement.executeQuery("SELECT role FROM Users WHERE user_id=5")) {
                    require(rows.next() && "customer".equals(rows.getString(1)),"Legacy role default survived");
                }
                for(String role:new String[]{"staff","member","owner"}) {
                    boolean rejected=false;
                    try { statement.execute("INSERT Users(user_id,role) VALUES(6,'"+role+"')"); }
                    catch(SQLException expected) { rejected=true; }
                    require(rejected,"Database accepted retired or unknown role: "+role);
                    require(!new AppUser(6,"legacy","hash","Legacy",role,0).isEnabled(),"Authentication accepted retired role");
                }
                for(String role:new String[]{"admin","customer"})
                    require(new AppUser(1,"active","hash","Active",role,0).isEnabled(),"Active role disabled");
                System.out.println("PASS two-role migration preserves ids/passwords, revokes legacy sessions once, updates default and rejects retired roles");
            } finally {
                statement.execute("USE master");
                statement.execute("ALTER DATABASE ["+database+"] SET SINGLE_USER WITH ROLLBACK IMMEDIATE");
                statement.execute("DROP DATABASE ["+database+"]");
                System.out.println("Disposed isolated role migration database.");
            }
        }
    }
}
