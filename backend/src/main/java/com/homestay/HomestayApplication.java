package com.homestay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * DIEM KHOI DAU CUA BACKEND
 * NGUOI LAM: DUC DAI (LEADER)
 *
 * Cach chay:
 * 1. Mo terminal trong thu muc backend/
 * 2. Chay lenh: mvn spring-boot:run
 * 3. Server se chay tai: http://localhost:8080
 */
@SpringBootApplication
// NOTE BACKEND: Điểm bắt đầu chạy Java; Spring Boot tạo các thành phần, nạp cấu hình và mở web server.
public class HomestayApplication {
    // NOTE BACKEND: Khởi động ứng dụng với tham số dòng lệnh; các controller, cấu hình và runner được Spring tự tìm trong com.homestay.
    public static void main(String[] args) {
        SpringApplication.run(HomestayApplication.class, args);
    }
}
