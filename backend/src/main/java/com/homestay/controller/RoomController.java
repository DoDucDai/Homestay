package com.homestay.controller;

import com.homestay.db.DBConnection;
import org.springframework.web.bind.annotation.*;
import java.sql.*;
import java.util.*;

@RestController
@RequestMapping("/api/rooms")
@CrossOrigin(origins = "*")
public class RoomController {

    private long parseLong(Object val, long defaultVal) {
        if (val == null) return defaultVal;
        if (val instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(val.toString().trim());
        } catch (Exception e) {
            return defaultVal;
        }
    }

    // GET /api/rooms
    // Return: [ { roomId, roomCode, roomName, roomType, price, status, description } ]
    @GetMapping
    public List<Map<String, Object>> getAllRooms() {
        List<Map<String, Object>> result = new ArrayList<>();
        String sql = "SELECT room_id, room_code, room_name, room_type, price, status, description FROM Rooms ORDER BY room_code ASC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("roomId", rs.getInt("room_id"));
                row.put("roomCode", rs.getString("room_code"));
                row.put("roomName", rs.getString("room_name"));
                row.put("roomType", rs.getString("room_type"));
                row.put("price", rs.getLong("price"));
                row.put("status", rs.getString("status"));
                row.put("description", rs.getString("description"));
                result.add(row);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;
    }

    // GET /api/rooms/{id}
    @GetMapping("/{id}")
    public Map<String, Object> getRoomById(@PathVariable int id) {
        String sql = "SELECT room_id, room_code, room_name, room_type, price, status, description FROM Rooms WHERE room_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("roomId", rs.getInt("room_id"));
                    row.put("roomCode", rs.getString("room_code"));
                    row.put("roomName", rs.getString("room_name"));
                    row.put("roomType", rs.getString("room_type"));
                    row.put("price", rs.getLong("price"));
                    row.put("status", rs.getString("status"));
                    row.put("description", rs.getString("description"));
                    return row;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Map.of("success", false, "message", "Không tìm thấy phòng!");
    }

    // POST /api/rooms
    // Body: { "roomCode": "P101", "roomName": "...", "roomType": "Single", "price": 300000, "status": "Trống", "description": "..." }
    @PostMapping
    public Map<String, Object> createRoom(@RequestBody Map<String, Object> body) {
        String roomCode = body.get("roomCode") != null ? body.get("roomCode").toString().trim() : "";
        String roomName = body.get("roomName") != null ? body.get("roomName").toString().trim() : "";
        String roomType = body.get("roomType") != null ? body.get("roomType").toString().trim() : "Standard";
        long price = parseLong(body.get("price"), 0);
        String status = body.get("status") != null && !body.get("status").toString().trim().isEmpty()
                ? body.get("status").toString().trim() : "Trống";
        String description = body.get("description") != null ? body.get("description").toString().trim() : "";

        if (roomCode.isEmpty() || roomName.isEmpty() || price <= 0) {
            return Map.of("success", false, "message", "Vui lòng nhập đầy đủ mã phòng, tên phòng và giá hợp lệ!");
        }

        // Kiểm tra trùng mã phòng
        String checkSql = "SELECT COUNT(*) FROM Rooms WHERE room_code = ?";
        String insertSql = "INSERT INTO Rooms (room_code, room_name, room_type, price, status, description) VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = DBConnection.getConnection()) {
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setString(1, roomCode);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        return Map.of("success", false, "message", "Mã phòng đã tồn tại trong hệ thống!");
                    }
                }
            }

            try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                stmt.setString(1, roomCode);
                stmt.setString(2, roomName);
                stmt.setString(3, roomType);
                stmt.setLong(4, price);
                stmt.setString(5, status);
                stmt.setString(6, description);
                stmt.executeUpdate();

                return Map.of("success", true, "message", "Thêm phòng thành công!");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return Map.of("success", false, "message", "Lỗi lưu phòng: " + e.getMessage());
        }
    }

    // PUT /api/rooms/{id}
    @PutMapping("/{id}")
    public Map<String, Object> updateRoom(@PathVariable int id, @RequestBody Map<String, Object> body) {
        String roomCode = body.get("roomCode") != null ? body.get("roomCode").toString().trim() : "";
        String roomName = body.get("roomName") != null ? body.get("roomName").toString().trim() : "";
        String roomType = body.get("roomType") != null ? body.get("roomType").toString().trim() : "Standard";
        long price = parseLong(body.get("price"), 0);
        String status = body.get("status") != null ? body.get("status").toString().trim() : "Trống";
        String description = body.get("description") != null ? body.get("description").toString().trim() : "";

        if (roomName.isEmpty() || price <= 0) {
            return Map.of("success", false, "message", "Tên phòng và giá phòng không được để trống!");
        }

        try (Connection conn = DBConnection.getConnection()) {
            // Kiểm tra trùng mã phòng với phòng khác nếu roomCode được truyền vào
            if (!roomCode.isEmpty()) {
                String checkSql = "SELECT COUNT(*) FROM Rooms WHERE room_code = ? AND room_id <> ?";
                try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                    checkStmt.setString(1, roomCode);
                    checkStmt.setInt(2, id);
                    try (ResultSet rs = checkStmt.executeQuery()) {
                        if (rs.next() && rs.getInt(1) > 0) {
                            return Map.of("success", false, "message", "Mã phòng đã được sử dụng bởi phòng khác!");
                        }
                    }
                }
            }

            String updateSql = !roomCode.isEmpty()
                    ? "UPDATE Rooms SET room_code = ?, room_name = ?, room_type = ?, price = ?, status = ?, description = ? WHERE room_id = ?"
                    : "UPDATE Rooms SET room_name = ?, room_type = ?, price = ?, status = ?, description = ? WHERE room_id = ?";

            try (PreparedStatement stmt = conn.prepareStatement(updateSql)) {
                int idx = 1;
                if (!roomCode.isEmpty()) {
                    stmt.setString(idx++, roomCode);
                }
                stmt.setString(idx++, roomName);
                stmt.setString(idx++, roomType);
                stmt.setLong(idx++, price);
                stmt.setString(idx++, status);
                stmt.setString(idx++, description);
                stmt.setInt(idx, id);

                int affected = stmt.executeUpdate();
                if (affected > 0) {
                    return Map.of("success", true, "message", "Cập nhật phòng thành công!");
                } else {
                    return Map.of("success", false, "message", "Không tìm thấy phòng để cập nhật!");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return Map.of("success", false, "message", "Lỗi cập nhật: " + e.getMessage());
        }
    }

    // DELETE /api/rooms/{id}
    @DeleteMapping("/{id}")
    public Map<String, Object> deleteRoom(@PathVariable int id) {
        try (Connection conn = DBConnection.getConnection()) {
            // Kiểm tra ràng buộc khóa ngoại với bảng Bookings
            String checkSql = "SELECT COUNT(*) FROM Bookings WHERE room_id = ?";
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setInt(1, id);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        return Map.of("success", false, "message", "Không thể xóa phòng đã có lịch sử đặt phòng!");
                    }
                }
            }

            String deleteSql = "DELETE FROM Rooms WHERE room_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
                stmt.setInt(1, id);
                int affected = stmt.executeUpdate();
                if (affected > 0) {
                    return Map.of("success", true, "message", "Xóa phòng thành công!");
                } else {
                    return Map.of("success", false, "message", "Không tìm thấy phòng để xóa!");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return Map.of("success", false, "message", "Lỗi xóa phòng: " + e.getMessage());
        }
    }
}