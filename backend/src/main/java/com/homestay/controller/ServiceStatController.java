package com.homestay.controller;

import com.homestay.db.DBConnection;
import org.springframework.web.bind.annotation.*;
import java.sql.*;
import java.util.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ServiceStatController {

    // ==================== SERVICES ====================

    // GET /api/services
    // Return: [ { serviceId, serviceName, unitPrice, status } ]
    @GetMapping("/services")
    public List<Map<String, Object>> getAllServices() {
        // TODO: Ð?c Ð?i (Leader) vi?t
        // SELECT * FROM Services ORDER BY service_name
        return new ArrayList<>();
    }

    // POST /api/services
    // Body: { "serviceName": "...", "unitPrice": 100000, "status": "Hoáº¡t Ä‘á»™ng" }
    // Return: { "success": true }
    @PostMapping("/services")
    public Map<String, Object> createService(@RequestBody Map<String, Object> body) {
        // TODO: Ð?c Ð?i (Leader) vi?t
        // INSERT INTO Services (service_name, unit_price, status) VALUES (?,?,?)
        return Map.of("success", false, "message", "Chua implement");
    }

    // PUT /api/services/{id}
    // Body: { "serviceName": "...", "unitPrice": ..., "status": "..." }
    // Return: { "success": true }
    @PutMapping("/services/{id}")
    public Map<String, Object> updateService(@PathVariable int id, @RequestBody Map<String, Object> body) {
        // TODO: Ð?c Ð?i (Leader) vi?t
        // UPDATE Services SET service_name=?, unit_price=?, status=? WHERE service_id=?
        return Map.of("success", false, "message", "Chua implement");
    }

    // DELETE /api/services/{id}
    // Return: { "success": true }
    @DeleteMapping("/services/{id}")
    public Map<String, Object> deleteService(@PathVariable int id) {
        // TODO: Ð?c Ð?i (Leader) vi?t
        // DELETE FROM Services WHERE service_id=?
        return Map.of("success", false, "message", "Chua implement");
    }

    // ==================== STATISTICS ====================

    // GET /api/statistics/overview
    // Return: { totalRooms, availableRooms, totalCustomers, totalBookingsThisMonth, revenueThisMonth }
    @GetMapping("/statistics/overview")
    public Map<String, Object> getOverview() {
        // TODO: Ð?c Ð?i (Leader) vi?t API, Long + Hung viet SQL query
        // Query 1 - Long viet: SELECT COUNT(*) total, SUM(CASE WHEN status=N'Trá»‘ng' THEN 1 ELSE 0 END) available FROM Rooms
        // Query 2 - Hung viet: SELECT COUNT(*) FROM Customers
        // Query 3 - Long viet: SELECT COUNT(*) FROM Bookings WHERE MONTH(created_at)=MONTH(GETDATE())
        // Query 4 - Hung viet: SELECT SUM(grand_total) FROM Payments WHERE MONTH(paid_at)=MONTH(GETDATE())
        return new HashMap<>();
    }

    // GET /api/statistics/revenue?month=9&year=2026
    // Return: [ { paymentId, customerName, roomCode, paidAt, grandTotal } ]
    @GetMapping("/statistics/revenue")
    public List<Map<String, Object>> getRevenue(@RequestParam int month, @RequestParam int year) {
        // TODO: Ð?c Ð?i (Leader) vi?t API, Hung viet SQL query
        // SELECT p.*, c.full_name, r.room_code
        //   FROM Payments p
        //   JOIN Bookings b ON p.booking_id = b.booking_id
        //   JOIN Customers c ON b.customer_id = c.customer_id
        //   JOIN Rooms r ON b.room_id = r.room_id
        //   WHERE MONTH(p.paid_at)=? AND YEAR(p.paid_at)=?
        return new ArrayList<>();
    }
}
