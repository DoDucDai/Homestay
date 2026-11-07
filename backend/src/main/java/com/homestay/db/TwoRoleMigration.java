package com.homestay.db;

import java.sql.Connection;

/** Retire legacy roles without changing credentials, guest records or invoices. */
// NOTE BACKEND: Chuyển quyền cũ staff/member sang customer, giữ tài khoản và dữ liệu nghiệp vụ; database chỉ chấp nhận admin/customer.
public final class TwoRoleMigration {
    private TwoRoleMigration() {}
    // NOTE BACKEND: Chạy /db/009_two_roles.sql; tài khoản đổi quyền tăng auth_version để phiên đăng nhập cũ mất hiệu lực.
    public static void migrate(Connection connection) throws Exception {
        AccountRegistrationMigration.runScript(connection,"/db/009_two_roles.sql");
    }
    // NOTE BACKEND: Chạy migration từ dòng lệnh rồi in số tài khoản theo quyền, không in thông tin đăng nhập.
    public static void main(String[] args) throws Exception {
        try(var connection=DBConnection.getConnection()) {
            migrate(connection);
            try(var statement=connection.createStatement();var rows=statement.executeQuery("SELECT role,COUNT(*) FROM Users GROUP BY role")) {
                while(rows.next())System.out.println(rows.getString(1)+": "+rows.getInt(2));
            }
        }
    }
}
