package com.homestay.controller;

import java.util.Map;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Readiness after startup checks complete; avoids polling/waking the SQL database. */
// NOTE API: API kiểm tra ứng dụng đã sẵn sàng sau các bước khởi động; công khai để hệ thống theo dõi trạng thái.
// @...Mapping xác định phương thức HTTP và đường dẫn; @RequestBody đọc JSON,
// @PathVariable đọc ID trong URL, @RequestParam đọc tham số sau dấu ?. Quyền truy cập nằm trong SecurityConfig.
@RestController
public class HealthController {
    private final ApplicationAvailability availability;
    public HealthController(ApplicationAvailability availability) { this.availability=availability; }
    // NOTE API: GET /api/health trả 200/ready khi ứng dụng sẵn sàng, ngược lại trả 503/starting.
    // Chỉ đọc trạng thái Spring Boot, không thực hiện truy vấn SQL mỗi lần kiểm tra.
    @GetMapping("/api/health")
    public ResponseEntity<Map<String,Object>> health() {
        boolean ready=availability.getReadinessState()==ReadinessState.ACCEPTING_TRAFFIC;
        return ResponseEntity.status(ready?200:503).body(Map.of("success",ready,"status",ready?"ready":"starting"));
    }
}
