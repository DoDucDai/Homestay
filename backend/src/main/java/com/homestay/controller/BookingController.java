package com.homestay.controller;
import com.homestay.db.DBConnection;
import org.springframework.web.bind.annotation.*;
import java.sql.*; import java.util.*;

/**
 * BOOKING + CHECKIN CONTROLLER
 * NGUOI LAM: BAN (LEADER) - Booking API
 *            DUC DAI       - CheckIn API
 */
@RestController @RequestMapping("/api") @CrossOrigin(origins = "*")
public class BookingController {
    // GET /api/bookings?status=X -> lay booking theo trang thai
    @GetMapping("/bookings") public List<Map<String,Object>> getBookings(@RequestParam(required=false) String status) { return new ArrayList<>(); }
    // POST /api/bookings -> tao booking + kiem tra phong trong - BAN LAM
    @PostMapping("/bookings") public Map<String,Object> createBooking(@RequestBody Map<String,Object> b) { return Map.of("success",false); }
    // PUT /api/checkin/{id} -> check-in - DUC LAM
    @PutMapping("/checkin/{id}") public Map<String,Object> checkIn(@PathVariable int id) { return Map.of("success",false); }
    // POST /api/checkout/{id} -> check-out + tinh tien - BAN LAM
    @PostMapping("/checkout/{id}") public Map<String,Object> checkOut(@PathVariable int id, @RequestBody Map<String,Object> b) { return Map.of("success",false); }
}
