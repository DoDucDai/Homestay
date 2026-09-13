package com.homestay.controller;

import com.homestay.db.DBConnection;
import org.springframework.web.bind.annotation.*;
import java.sql.*;
import java.util.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class BookingController {

    // GET /api/bookings?status=Confirmed
    // status co the la: Confirmed | CheckedIn | CheckedOut | Cancelled | (bo trong de lay tat ca)
    // Return: [ { bookingId, customerName, roomCode, checkinDate, checkoutDate, status, note } ]
    @GetMapping("/bookings")
    public List<Map<String, Object>> getBookings(@RequestParam(required = false) String status) {
        // TODO: –?c –?i (Leader) vi?t
        // SELECT b.*, c.full_name, r.room_code, r.price
        //   FROM Bookings b
        //   JOIN Customers c ON b.customer_id = c.customer_id
        //   JOIN Rooms r ON b.room_id = r.room_id
        //   WHERE b.status = ? (neu co truyen status)
        return new ArrayList<>();
    }

    // POST /api/bookings
    // Body: { "customerId": 1, "roomId": 2, "checkinDate": "2026-09-15", "checkoutDate": "2026-09-18", "note": "..." }
    // Return: { "success": true, "bookingId": 10 }
    @PostMapping("/bookings")
    public Map<String, Object> createBooking(@RequestBody Map<String, Object> body) {
        // TODO: –?c –?i (Leader) vi?t
        // 1. Kiem tra phong co status='Tr·ªëng' khong
        // 2. INSERT INTO Bookings (customer_id, room_id, checkin_date, checkout_date, note) VALUES (?,?,?,?,?)
        // 3. UPDATE Rooms SET status=N'ƒê√£ ƒë·∫∑t' WHERE room_id=?
        return Map.of("success", false, "message", "Chua implement");
    }

    // PUT /api/checkin/{bookingId}
    // Return: { "success": true }
    @PutMapping("/checkin/{bookingId}")
    public Map<String, Object> checkIn(@PathVariable int bookingId) {
        // TODO: –?c –?i (Leader) vi?t
        // 1. UPDATE Bookings SET status='CheckedIn' WHERE booking_id=?
        // 2. UPDATE Rooms SET status=N'ƒêang s·ª≠ d·ª•ng' WHERE room_id = (SELECT room_id FROM Bookings WHERE booking_id=?)
        return Map.of("success", false, "message", "Chua implement");
    }

    // POST /api/checkout/{bookingId}
    // Body: { "paymentMethod": "Ti·ªÅn m·∫∑t" }
    // Return: { "success": true, "invoice": { roomTotal, serviceTotal, grandTotal } }
    @PostMapping("/checkout/{bookingId}")
    public Map<String, Object> checkOut(@PathVariable int bookingId, @RequestBody Map<String, Object> body) {
        // TODO: –?c –?i (Leader) vi?t
        // 1. Lay thong tin booking + phong (price, so dem)
        // 2. Tinh roomTotal = price * so_dem
        // 3. Tinh serviceTotal = SUM(total_price) FROM Booking_Services WHERE booking_id=?
        // 4. INSERT INTO Payments (booking_id, room_total, service_total, grand_total, payment_method)
        // 5. UPDATE Bookings SET status='CheckedOut' WHERE booking_id=?
        // 6. UPDATE Rooms SET status=N'Tr·ªëng' WHERE room_id=?
        return Map.of("success", false, "message", "Chua implement");
    }
}
