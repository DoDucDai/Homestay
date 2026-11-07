package com.homestay.db;

import java.nio.charset.StandardCharsets;
import java.sql.*;

// NOTE BACKEND: Chạy SQL bổ sung cấu trúc phục vụ đăng ký, email, mã xác thực và hạn mức yêu cầu tài khoản.
public final class AccountRegistrationMigration {
    private AccountRegistrationMigration() {}
    // NOTE BACKEND: Đọc script /db/008_account_registration.sql đã đóng gói trong JAR rồi gọi bộ thực thi chung.
    public static void migrate(Connection connection) throws Exception {
        runScript(connection,"/db/008_account_registration.sql");
    }
    // NOTE BACKEND: Tách script theo GO, thực thi và đọc hết các tập kết quả; nếu lỗi thì rollback giao dịch SQL còn mở và khôi phục chế độ auto-commit ban đầu.
    static void runScript(Connection connection,String resource) throws Exception {
        boolean autoCommit=connection.getAutoCommit();
        if(!autoCommit) { connection.commit();connection.setAutoCommit(true); }
        try(var input=AccountRegistrationMigration.class.getResourceAsStream(resource)) {
            if(input==null)throw new IllegalStateException("Account migration missing: "+resource);
            try(var statement=connection.createStatement()) {
                for(String batch:new String(input.readAllBytes(),StandardCharsets.UTF_8).split("(?im)^\\s*GO\\s*$")) {
                    if(batch.isBlank())continue;
                    boolean rows=statement.execute(batch);
                    while(rows || statement.getUpdateCount()!=-1) {
                        if(rows)try(var result=statement.getResultSet()) { while(result.next()) {} }
                        rows=statement.getMoreResults();
                    }
                }
            }
        } catch(Exception error) {
            try(var rollback=connection.createStatement()) { rollback.execute("IF @@TRANCOUNT>0 ROLLBACK TRANSACTION"); }
            catch(SQLException failure) { error.addSuppressed(failure); }
            throw error;
        } finally { connection.setAutoCommit(autoCommit); }
    }
}
