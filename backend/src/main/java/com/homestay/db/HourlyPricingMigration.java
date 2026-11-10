package com.homestay.db;

import java.sql.*;

/** Additive migration; poster prices are imported once, never on subsequent restarts. */
// NOTE BACKEND: Bổ sung cột giá gói giờ, giá cuối tuần và bản lưu bảng giá của booking; dữ liệu giá mẫu chỉ gán ở lần thêm cột đầu tiên.
public final class HourlyPricingMigration {
    private HourlyPricingMigration() {}
    // NOTE BACKEND: Khóa bảng phòng trong giao dịch, thêm cột còn thiếu và áp dụng giá mẫu ban đầu; có lỗi thì rollback, chạy lại không ghi đè giá đã sửa.
    public static void migrate(Connection conn) throws SQLException {
        conn.setAutoCommit(false);
        try(var statement=conn.createStatement()) {
            statement.execute("SET XACT_ABORT ON");
            statement.execute("SET LOCK_TIMEOUT 30000");
            try(var lock=statement.executeQuery("SELECT COUNT(*) FROM dbo.Rooms WITH(TABLOCKX,HOLDLOCK)")){lock.next();}
            boolean seed;
            try(var rs=statement.executeQuery("SELECT CASE WHEN COL_LENGTH('dbo.Rooms','hourly_package_2') IS NULL THEN 1 ELSE 0 END")){rs.next();seed=rs.getInt(1)==1;}
            statement.execute("IF COL_LENGTH('dbo.Rooms','hourly_package_2') IS NULL ALTER TABLE dbo.Rooms ADD hourly_package_2 DECIMAL(12,0) NULL, hourly_package_4 DECIMAL(12,0) NULL, hourly_package_8 DECIMAL(12,0) NULL;");
            statement.execute("IF COL_LENGTH('dbo.Bookings','hourly_packages_snapshot') IS NULL ALTER TABLE dbo.Bookings ADD hourly_packages_snapshot NVARCHAR(500) NULL;");
            if(seed)statement.execute("UPDATE dbo.Rooms SET hourly_price=80000,hourly_package_2=250000,hourly_package_4=350000,hourly_package_8=550000 WHERE room_code IN ('P101','P102','P103','P201','P202','P301','P401','P402','P403','P404','P405','P406','P407','DEMO-P101','DEMO-P102','DEMO-P103','DEMO-P201','DEMO-P202','DEMO-P301','DEMO-P401','DEMO-P402','DEMO-P403','DEMO-P404','DEMO-P405','DEMO-P406','DEMO-P407');");
            boolean seedNightly;
            try(var rs=statement.executeQuery("SELECT CASE WHEN COL_LENGTH('dbo.Rooms','weekend_price') IS NULL THEN 1 ELSE 0 END")){rs.next();seedNightly=rs.getInt(1)==1;}
            statement.execute("IF COL_LENGTH('dbo.Rooms','weekend_price') IS NULL ALTER TABLE dbo.Rooms ADD weekend_price DECIMAL(12,0) NULL, weekend_days VARCHAR(13) NULL;");
            statement.execute("IF COL_LENGTH('dbo.Bookings','nightly_pricing_snapshot') IS NULL ALTER TABLE dbo.Bookings ADD nightly_pricing_snapshot NVARCHAR(250) NULL;");
            if(seedNightly)statement.execute("UPDATE dbo.Rooms SET price=400000,weekend_price=450000,weekend_days='6,7' WHERE room_code IN ('P101','P102','P103','P201','P202','P301','P401','P402','P403','P404','P405','DEMO-P101','DEMO-P102','DEMO-P103','DEMO-P201','DEMO-P202','DEMO-P301','DEMO-P401','DEMO-P402','DEMO-P403','DEMO-P404','DEMO-P405');");
            conn.commit();
        } catch(SQLException e) {try{conn.rollback();}catch(SQLException rollback){e.addSuppressed(rollback);}throw e;}
    }
}
