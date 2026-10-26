package com.homestay.controller;

import com.homestay.db.DBConnection;
import com.homestay.api.*;
import org.springframework.web.bind.annotation.*;
import java.sql.*;
import java.time.Clock;
import java.util.*;

@RestController
@RequestMapping("/api/rooms")

public class RoomController {
    private final Clock clock;
    public RoomController() { this(RoomSchedule.CLOCK); }
    RoomController(Clock clock) { this.clock = clock; }

    private long parseLong(Object val, long fallback) {
        try { return new java.math.BigDecimal(String.valueOf(val)).longValueExact(); }
        catch (RuntimeException e) { return fallback; }
    }

    private String text(Map<String, Object> body, String key, String fallback) {
        return body.get(key) == null ? fallback : body.get(key).toString().trim();
    }

    public List<Map<String, Object>> getAllRooms() { return getAllRooms(null, null, null); }

    @GetMapping
    public List<Map<String, Object>> getAllRooms(@RequestParam(required=false) String type,
            @RequestParam(required=false) String status, @RequestParam(required=false) String search) {
        if (type != null && !type.isBlank()) Input.oneOf(type,Set.of("Single","Double","VIP","Family"),"Loại phòng");
        if (status != null && !status.isBlank()) Input.oneOf(status,Set.of("Trống","Đã đặt","Đang sử dụng","Bảo trì"),"Trạng thái phòng");
        String keyword = search == null ? "" : search.trim();
        if (keyword.length() > 100) throw new ApiProblem(400,"INVALID_INPUT","Tìm kiếm tối đa 100 ký tự!");
        List<Map<String, Object>> rooms = new ArrayList<>();
        // Filter the calculated status, including when the business date has changed.
        String query = "SELECT * FROM (" + RoomSchedule.selectRooms() + ") q WHERE 1=1";
        List<String> values = new ArrayList<>();
        if (type != null && !type.isBlank()) { query += " AND room_type=?"; values.add(type); }
        if (status != null && !status.isBlank()) { query += " AND status=?"; values.add(status); }
        if (!keyword.isEmpty()) {
            query += " AND (CHARINDEX(?,room_code)>0 OR CHARINDEX(?,room_name)>0)";
            values.add(keyword); values.add(keyword);
        }
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query + " ORDER BY room_code")) {
            RoomSchedule.bindToday(stmt, RoomSchedule.today(clock));
            for (int i=0;i<values.size();i++) stmt.setString(i+3,values.get(i));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) rooms.add(RoomSchedule.roomRow(rs));
            }
        } catch (SQLException e) { throw new RoomSchedule.QueryFailure(e); }
        return rooms;
    }

    @PutMapping("/{id}/status")
    public Map<String, Object> updateStatus(@PathVariable int id, @RequestBody Map<String,Object> body) {
        return ApiErrors.guard(() -> {
            Input.id(id);
            String requested = Input.oneOf(Input.text(body,"status",30,true,null),Set.of("Trống","Bảo trì"),"Trạng thái phòng");
            return RoomSchedule.transaction(conn -> {
                RoomSchedule.LockedRoom room = RoomSchedule.lockRoom(conn,id);
                String actual = RoomSchedule.requestedStatus(conn,id,room.status(),requested,RoomSchedule.today(clock));
                try (PreparedStatement stmt = conn.prepareStatement("UPDATE Rooms SET status=? WHERE room_id=?")) {
                    stmt.setString(1,actual); stmt.setInt(2,id); stmt.executeUpdate();
                }
                return Map.of("success",true,"status",actual,"message","Đã cập nhật trạng thái phòng!");
            },"Không thể cập nhật trạng thái phòng!");
        });
    }

    @GetMapping("/{id}")
    public Map<String, Object> getRoomById(@PathVariable int id) {
        return ApiErrors.guard(() -> {
            Input.id(id);
            try (Connection conn = DBConnection.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(RoomSchedule.selectRooms() + " WHERE r.room_id=?")) {
                RoomSchedule.bindToday(stmt, RoomSchedule.today(clock));
                stmt.setInt(3, id);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) return RoomSchedule.roomRow(rs);
                }
            } catch (SQLException e) { throw new RoomSchedule.QueryFailure(e); }
            return ApiErrors.missing("Không tìm thấy phòng!");


        });
    }

    @PostMapping
    public Map<String, Object> createRoom(@RequestBody Map<String, Object> body) {
        return ApiErrors.guard(() -> {
            String code = Input.text(body,"roomCode",20,true,null);
            String name = Input.text(body,"roomName",100,true,null);
            String type = Input.oneOf(Input.text(body,"roomType",50,true,null),Set.of("Single","Double","VIP","Family"),"Loại phòng");
            long price = Input.money(body,"price",true);
            String status = Input.text(body,"status",30,false,"Trống");
            String description = Input.text(body,"description",500,false,"");
            if (status.isEmpty()) status = "Trống";
            if (code.isEmpty() || name.isEmpty() || price <= 0 || price > 999999999999L) {
                return ApiErrors.bad("Vui lòng nhập mã, tên phòng và giá nguyên hợp lệ!");
            }
            if (!Set.of("Trống","Bảo trì").contains(status)) {
                return ApiErrors.bad("Phòng mới chỉ có thể Trống hoặc Bảo trì!");
            }
            try (Connection conn = DBConnection.getConnection(); PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO Rooms(room_code,room_name,room_type,price,status,description) VALUES(?,?,?,?,?,?)")) {
                stmt.setString(1,code);
                stmt.setString(2,name);
                stmt.setString(3,type);
                stmt.setLong(4,price);
                stmt.setString(5,status);
                stmt.setString(6,description);
                stmt.executeUpdate();
                return Map.of("success",true,"message","Thêm phòng thành công!");
            } catch (SQLException e) {
                return ApiErrors.database(e);
            }


        });
    }

    @PutMapping("/{id}")
    public Map<String, Object> updateRoom(@PathVariable int id, @RequestBody Map<String, Object> body) {
        return ApiErrors.guard(() -> {
            Input.id(id);
            String code = Input.text(body,"roomCode",20,false,"");
            String name = Input.text(body,"roomName",100,true,null);
            String type = Input.oneOf(Input.text(body,"roomType",50,true,null),Set.of("Single","Double","VIP","Family"),"Loại phòng");
            long price = Input.money(body,"price",true);
            String requested=Input.text(body,"status",30,false,null);
            if(requested!=null) Input.oneOf(requested,Set.of("Trống","Bảo trì"),"Trạng thái phòng (chỉ Trống hoặc Bảo trì)");
            String description=Input.text(body,"description",500,false,"");
            if (name.isEmpty() || price <= 0 || price > 999999999999L) {
                return ApiErrors.bad("Tên phòng và giá nguyên không hợp lệ!");
            }
            return RoomSchedule.transaction(conn -> {
                RoomSchedule.LockedRoom room = RoomSchedule.lockRoom(conn,id);
                String status = RoomSchedule.requestedStatus(conn,id,room.status(),requested,RoomSchedule.today(clock));
                if (!code.isEmpty()) {
                    try (PreparedStatement stmt = conn.prepareStatement("SELECT room_id FROM Rooms WHERE room_code=? AND room_id<>?")) {
                        stmt.setString(1,code);
                        stmt.setInt(2,id);
                        try (ResultSet rs = stmt.executeQuery()) {
                            if (rs.next()) throw new RoomSchedule.Conflict("Mã phòng đã được sử dụng!");
                        }
                    }
                }
                String query = code.isEmpty()
                        ? "UPDATE Rooms SET room_name=?,room_type=?,price=?,status=?,description=? WHERE room_id=?"
                        : "UPDATE Rooms SET room_code=?,room_name=?,room_type=?,price=?,status=?,description=? WHERE room_id=?";
                try (PreparedStatement stmt = conn.prepareStatement(query)) {
                    int index=1;
                    if (!code.isEmpty()) stmt.setString(index++,code);
                    stmt.setString(index++,name);
                    stmt.setString(index++,type);
                    stmt.setLong(index++,price);
                    stmt.setString(index++,status);
                    stmt.setString(index++,description);
                    stmt.setInt(index,id);
                    if (stmt.executeUpdate()!=1) throw new SQLException("Room update failed");
                }
                return Map.of("success",true,"message","Cập nhật phòng thành công!","status",status);
            },"Không thể cập nhật phòng. Vui lòng tải lại!");


        });
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> deleteRoom(@PathVariable int id) {
        return ApiErrors.guard(() -> {
            Input.id(id);
            return RoomSchedule.transaction(conn -> {
                RoomSchedule.lockRoom(conn,id);
                try (PreparedStatement stmt = conn.prepareStatement("SELECT booking_id FROM Bookings WHERE room_id=?")) {
                    stmt.setInt(1,id);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) throw new RoomSchedule.Conflict("Không thể xóa phòng đã có lịch sử đặt phòng!");
                    }
                }
                try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM Rooms WHERE room_id=?")) {
                    stmt.setInt(1,id);
                    if (stmt.executeUpdate()!=1) throw new SQLException("Room delete failed");
                }
                return Map.of("success",true,"message","Xóa phòng thành công!");
            },"Không thể xóa phòng!");


        });
    }
}
