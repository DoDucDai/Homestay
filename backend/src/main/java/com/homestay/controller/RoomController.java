package com.homestay.controller;

import com.homestay.db.DBConnection;
import org.springframework.web.bind.annotation.*;
import java.sql.*;
import java.util.*;

@RestController
@RequestMapping("/api/rooms")
@CrossOrigin(origins = "*")
public class RoomController {

    // GET /api/rooms
    // Return: [ { roomId, roomCode, roomName, roomType, price, status, description } ]
    @GetMapping
    public List<Map<String, Object>> getAllRooms() {
        // TODO: Ð?c Ð?i (Leader) vi?t
        // SELECT * FROM Rooms ORDER BY room_code
        return new ArrayList<>();
    }

    // POST /api/rooms
    // Body: { "roomCode": "P101", "roomName": "...", "roomType": "Single", "price": 300000, "description": "..." }
    // Return: { "success": true, "message": "Them phong thanh cong" }
    @PostMapping
    public Map<String, Object> createRoom(@RequestBody Map<String, Object> body) {
        // TODO: Ð?c Ð?i (Leader) vi?t
        // INSERT INTO Rooms (room_code, room_name, room_type, price, description) VALUES (?,?,?,?,?)
        return Map.of("success", false, "message", "Chua implement");
    }

    // PUT /api/rooms/{id}
    // Body: { "roomName": "...", "roomType": "...", "price": ..., "status": "...", "description": "..." }
    // Return: { "success": true }
    @PutMapping("/{id}")
    public Map<String, Object> updateRoom(@PathVariable int id, @RequestBody Map<String, Object> body) {
        // TODO: Ð?c Ð?i (Leader) vi?t
        // UPDATE Rooms SET room_name=?, room_type=?, price=?, status=?, description=? WHERE room_id=?
        return Map.of("success", false, "message", "Chua implement");
    }

    // DELETE /api/rooms/{id}
    // Return: { "success": true }
    @DeleteMapping("/{id}")
    public Map<String, Object> deleteRoom(@PathVariable int id) {
        // TODO: Ð?c Ð?i (Leader) vi?t
        // DELETE FROM Rooms WHERE room_id=?
        return Map.of("success", false, "message", "Chua implement");
    }
}
