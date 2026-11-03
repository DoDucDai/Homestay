package com.homestay.controller;

import java.util.Map;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Readiness after startup checks complete; avoids polling/waking the SQL database. */
@RestController
public class HealthController {
    private final ApplicationAvailability availability;
    public HealthController(ApplicationAvailability availability) { this.availability=availability; }
    @GetMapping("/api/health")
    public ResponseEntity<Map<String,Object>> health() {
        boolean ready=availability.getReadinessState()==ReadinessState.ACCEPTING_TRAFFIC;
        return ResponseEntity.status(ready?200:503).body(Map.of("success",ready,"status",ready?"ready":"starting"));
    }
}
