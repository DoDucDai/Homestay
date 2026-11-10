package com.homestay.security;

import com.homestay.api.*;
import com.homestay.db.DBConnection;
import java.sql.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Database transactions shared by account ownership, email claims and durable throttles. */
// NOTE BACKEND: Các giao dịch dùng chung cho hồ sơ tài khoản, quyền giữ email duy nhất và hạn mức được lưu bền trong database.
public final class AccountStore {
    private AccountStore() {}
    public interface Work<T> { T run(Connection connection) throws SQLException; }
    // NOTE BACKEND: Mở kết nối và giao dịch, commit kết quả hoặc rollback khi lỗi; lỗi SQL được đổi thành lỗi API thống nhất.
    public static <T> T transaction(Work<T> work) {
        try(var connection=DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try { T result=work.run(connection);connection.commit();return result; }
            catch(Exception e) { try { connection.rollback(); } catch(SQLException failure) { e.addSuppressed(failure); }throw e; }
        } catch(SQLException e) { throw ApiErrors.databaseFailure(e); }
    }
    // NOTE BACKEND: Bỏ khoảng trắng và viết thường email để kiểm tra trùng nhất quán.
    public static String normalize(String email) { return email.trim().toLowerCase(Locale.ROOT); }
    // NOTE BACKEND: Tạo SHA-256 cho tên khóa hoặc bộ đếm; hàm này không thay thế PBKDF2 dùng cho mật khẩu.
    public static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    // NOTE BACKEND: Dùng khóa ứng dụng SQL Server theo tài nguyên trong phạm vi giao dịch, ví dụ một email hoặc bộ đếm hạn mức.
    public static void lock(Connection connection,String resource) throws SQLException {
        // SQL Server starts a JDBC implicit transaction on a table access, not on an RPC alone.
        try(var begin=connection.prepareStatement("SELECT COUNT(*) FROM Users WHERE user_id=-1");var rows=begin.executeQuery()) {
            if(!rows.next()) throw new SQLException("Could not start account transaction.");
        }
        try(var statement=connection.prepareCall("{? = call sys.sp_getapplock(?,?,?,?)}")) {
            statement.registerOutParameter(1,Types.INTEGER);statement.setString(2,"account:"+hash(resource));statement.setString(3,"Exclusive");statement.setString(4,"Transaction");statement.setInt(5,10000);statement.execute();
            if(statement.getInt(1)<0)throw new ApiProblem(409,"ACCOUNT_BUSY","Tài khoản đang được cập nhật. Vui lòng thử lại.");
        }
    }
    // NOTE BACKEND: Khóa email, kiểm tra không thuộc tài khoản khác rồi cập nhật bản ghi sở hữu trong AccountEmails.
    public static void claimEmail(Connection connection,int userId,String email) throws SQLException {
        lock(connection,"email:"+email);
        try(var statement=connection.prepareStatement("SELECT COUNT(*) FROM Users WHERE LOWER(LTRIM(RTRIM(email)))=? AND user_id<>?")) {
            statement.setString(1,email);statement.setInt(2,userId);
            try(var rows=statement.executeQuery()) { rows.next();if(rows.getInt(1)>0)throw new ApiProblem(409,"EMAIL_IN_USE","Email đã được sử dụng. Vui lòng dùng địa chỉ khác hoặc khôi phục tài khoản."); }
        }
        try(var statement=connection.prepareStatement("DELETE AccountEmails WHERE user_id=?; INSERT AccountEmails(email,user_id) VALUES(?,?)")) {
            statement.setInt(1,userId);statement.setString(2,email);statement.setInt(3,userId);statement.executeUpdate();
        }
    }
    // NOTE BACKEND: Tăng số yêu cầu theo khóa và cửa sổ thời gian trong database; vượt hạn mức trả 429, restart ứng dụng không xóa bộ đếm.
    public static void limit(String key,int maximum,int seconds) {
        boolean allowed=transaction(connection->{
            lock(connection,"rate:"+key);
            try(var statement=connection.prepareStatement("DELETE AccountRateLimits WHERE bucket_hash=? AND window_until<=SYSUTCDATETIME(); IF NOT EXISTS(SELECT 1 FROM AccountRateLimits WHERE bucket_hash=?) INSERT AccountRateLimits(bucket_hash,window_until,requests) VALUES(?,DATEADD(second,?,SYSUTCDATETIME()),0); UPDATE AccountRateLimits SET requests=requests+1 OUTPUT INSERTED.requests WHERE bucket_hash=?")) {
                String bucket=hash(key);statement.setString(1,bucket);statement.setString(2,bucket);statement.setString(3,bucket);statement.setInt(4,seconds);statement.setString(5,bucket);
                boolean result=statement.execute();
                while(!result && statement.getUpdateCount()!=-1)result=statement.getMoreResults();
                try(var rows=statement.getResultSet()) { return rows!=null && rows.next() && rows.getInt(1)<=maximum; }
            }
        });
        if(!allowed)throw new ApiProblem(429,"RATE_LIMITED","Bạn đã thử quá nhiều lần. Vui lòng chờ rồi thử lại.");
    }
    // NOTE BACKEND: Cập nhật hồ sơ của đúng phiên hiện tại; đổi email sẽ tăng email_version, xóa xác thực và vô hiệu mã cũ; đồng bộ hồ sơ khách nếu đã liên kết.
    public static void updateProfile(AppUser user,String name,String phone,String email,java.time.LocalDate birth,String city) {
        String normalized=normalize(email);
        transaction(connection->{
            lock(connection,"email:"+normalized);
            String oldEmail;int customerId;
            try(var statement=connection.prepareStatement("SELECT email,auth_version,customer_id FROM Users WITH(UPDLOCK,HOLDLOCK) WHERE user_id=?")) {
                statement.setInt(1,user.userId());
                try(var rows=statement.executeQuery()) {
                    if(!rows.next() || rows.getInt("auth_version")!=user.authVersion())throw new ApiProblem(401,"UNAUTHENTICATED","Vui lòng đăng nhập lại!");
                    oldEmail=normalize(Objects.toString(rows.getString("email"),""));customerId=rows.getInt("customer_id");
                }
            }
            claimEmail(connection,user.userId(),normalized);
            boolean changed=!oldEmail.equals(normalized);
            try(var statement=connection.prepareStatement("UPDATE Users SET full_name=?,phone=?,email=?,birth_date=?,city=?,email_version=email_version+?,email_verified_at=CASE WHEN ?=1 THEN NULL ELSE email_verified_at END WHERE user_id=?")) {
                statement.setString(1,name);statement.setString(2,phone);statement.setString(3,normalized);statement.setDate(4,java.sql.Date.valueOf(birth));statement.setString(5,city);statement.setInt(6,changed?1:0);statement.setBoolean(7,changed);statement.setInt(8,user.userId());statement.executeUpdate();
            }
            if(changed)try(var statement=connection.prepareStatement("UPDATE AccountChallenges SET used_at=SYSUTCDATETIME() WHERE user_id=? AND used_at IS NULL")) { statement.setInt(1,user.userId());statement.executeUpdate(); }
            if(customerId>0)try(var statement=connection.prepareStatement("UPDATE Customers SET full_name=?,phone=?,email=? WHERE customer_id=?")) {
                statement.setString(1,name);statement.setString(2,phone);statement.setString(3,normalized);statement.setInt(4,customerId);statement.executeUpdate();
            }
            return null;
        });
    }
}
