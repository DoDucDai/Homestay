package com.homestay.controller;
import com.homestay.db.DBConnection;
import org.springframework.web.bind.annotation.*;
import java.sql.*; import java.util.*;

/**
 * SERVICES CONTROLLER - NGUOI LAM: DUC DAI
 * STATISTICS CONTROLLER - NGUOI LAM: DUC DAI (API), LONG+HUNG (SQL query)
 */
@RestController @RequestMapping("/api") @CrossOrigin(origins = "*")
public class ServiceStatController {
    @GetMapping("/services")         public List<Map<String,Object>> getServices()  { return new ArrayList<>(); }
    @PostMapping("/services")        public Map<String,Object> addService(@RequestBody Map<String,Object> b)  { return Map.of("success",false); }
    @PutMapping("/services/{id}")    public Map<String,Object> editService(@PathVariable int id, @RequestBody Map<String,Object> b)  { return Map.of("success",false); }
    @DeleteMapping("/services/{id}") public Map<String,Object> delService(@PathVariable int id)  { return Map.of("success",false); }

    // GET /api/statistics/overview -> LONG+HUNG viet SQL query
    @GetMapping("/statistics/overview") public Map<String,Object> overview() { return new HashMap<>(); }
    // GET /api/statistics/revenue?month=X&year=Y -> HUNG viet SQL query
    @GetMapping("/statistics/revenue") public List<Map<String,Object>> revenue(@RequestParam int month, @RequestParam int year) { return new ArrayList<>(); }
}
