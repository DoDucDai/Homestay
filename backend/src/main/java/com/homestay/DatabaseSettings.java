package com.homestay;

import com.homestay.db.DBConnection;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
// NOTE BACKEND: Nạp cấu hình kết nối SQL Server vào DBConnection khi Spring tạo thành phần này; không truy vấn dữ liệu ở constructor.
public class DatabaseSettings {
    public DatabaseSettings(Environment environment) {DBConnection.configure(environment);}
}
