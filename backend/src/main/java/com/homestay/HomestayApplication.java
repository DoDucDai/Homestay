package com.homestay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * DIEM KHOI DAU CUA BACKEND
 * NGUOI LAM: DUC DAI
 *
 * Cach chay:
 * 1. Mo terminal trong thu muc backend/
 * 2. Chay lenh: mvn spring-boot:run
 * 3. Server se chay tai: http://localhost:8080
 */
@SpringBootApplication
public class HomestayApplication {
    public static void main(String[] args) {
        SpringApplication.run(HomestayApplication.class, args);
    }
}
