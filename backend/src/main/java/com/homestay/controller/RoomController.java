package com.homestay.controller;

import com.homestay.db.DBConnection;
import org.springframework.web.bind.annotation.*;
import java.sql.*;
import java.util.*;

/**
 * ROOM CONTROLLER - CRUD PHONG
 * NGUOI LAM: DUC DAI
 *
 * API endpoints:
 *   GET    /api/rooms         -> lay danh sach phong
 *   GET    /api/rooms/{id}    -> lay 1 phong
 *   POST   /api/rooms         -> them phong moi
 *   PUT    /api/rooms/{id}    -> cap nhat phong
 *   DELETE /api/rooms/{id}    -> xoa phong
 */
@RestController
@RequestMapping("/api/rooms")
@CrossOrigin(origins = "*") // Cho phep Frontend goi API
public class RoomController {

    // TODO: Duc Dai viet cac ham bên dưới

    @GetMapping
    public List<Map<String, Object>> getAllRooms() {
        // TODO: query SELECT * FROM Rooms, tra ve danh sach
        return new ArrayList<>();
    }

    @PostMapping
    public Map<String, Object> createRoom(@RequestBody Map<String, Object> body) {
        // TODO: INSERT INTO Rooms ...
        return Map.of("success", false, "message", "Chua implement");
    }

    @PutMapping("/{id}")
    public Map<String, Object> updateRoom(@PathVariable int id, @RequestBody Map<String, Object> body) {
        // TODO: UPDATE Rooms SET ... WHERE room_id = id
        return Map.of("success", false, "message", "Chua implement");
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> deleteRoom(@PathVariable int id) {
        // TODO: DELETE FROM Rooms WHERE room_id = id
        return Map.of("success", false, "message", "Chua implement");
    }
}
