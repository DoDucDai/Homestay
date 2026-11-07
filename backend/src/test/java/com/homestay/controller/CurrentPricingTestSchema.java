package com.homestay.controller;

import com.homestay.db.HourlyPricingMigration;
import java.nio.file.*;
import java.sql.*;

/** Bring disposable legacy fixtures up to the pricing schema used at application startup. */
// NOTE KIỂM THỬ: Hàm hỗ trợ nâng schema giá của dữ liệu kiểm thử cũ lên phiên bản mà ứng dụng đang sử dụng.
// File này thuộc src/test, không phải endpoint và không nằm trong mã nghiệp vụ chạy phục vụ người dùng.
final class CurrentPricingTestSchema {
    private CurrentPricingTestSchema() {}
    static void execute(Statement statement,String batch) throws SQLException {
        boolean results=statement.execute(batch);
        while(results || statement.getUpdateCount()!=-1) {
            if(results)try(var rows=statement.getResultSet()) { while(rows.next()) {} }
            results=statement.getMoreResults();
        }
    }
    static void migrate(Connection connection) throws Exception {
        if(!connection.getCatalog().matches("Homestay(?:Fix02|Core0[123])Test_[a-f0-9]{32}"))
            throw new IllegalStateException("Refusing migration outside disposable test database");
        boolean autoCommit=connection.getAutoCommit();
        try {
            try(var statement=connection.createStatement()) {
                for(String batch:Files.readString(Path.of("../database/migrations/006_hourly_bookings.sql")).split("(?im)^\\s*GO\\s*$")) {
                    if(batch.isBlank())continue;
                    execute(statement,batch);
                }
            }
            HourlyPricingMigration.migrate(connection);
        } finally { connection.setAutoCommit(autoCommit); }
    }
}
