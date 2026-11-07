package com.homestay.controller;

import com.homestay.api.*;
import com.homestay.db.DBConnection;
import com.homestay.db.RoomCatalogMigration;
import org.springframework.web.bind.annotation.*;
import java.sql.*;
import java.util.*;

/** Public presentation fields only; booking and operational data remain protected. */
// NOTE API: Danh mục phòng công khai cho trang giới thiệu; chỉ trả trường cần trình bày, không trả khách hoặc booking.
// @...Mapping xác định phương thức HTTP và đường dẫn; @RequestBody đọc JSON,
// @PathVariable đọc ID trong URL, @RequestParam đọc tham số sau dấu ?. Quyền truy cập nằm trong SecurityConfig.
@RestController
@RequestMapping("/api/public/rooms")
public class PublicRoomController {
    // NOTE API: GET /api/public/rooms xem danh sách phòng giới thiệu mà không cần đăng nhập.
    // Ẩn phòng bảo trì; dữ liệu gồm tên, loại, giá, mô tả và cơ sở, không phải cam kết phòng trống theo lịch.
    @GetMapping
    public List<Map<String,Object>> list() { return read(null); }

    // NOTE API: GET /api/public/rooms/{id} xem chi tiết công khai của một phòng.
    // Trả lỗi 404 nếu phòng không còn thuộc danh mục giới thiệu; không cho sửa dữ liệu qua API này.
    @GetMapping("/{id}")
    public Map<String,Object> detail(@PathVariable int id) {
        Input.id(id);
        var rows=read(id);
        if(rows.isEmpty()) throw new ApiProblem(404,"NOT_FOUND","Phòng không còn trong danh mục giới thiệu.");
        return rows.get(0);
    }
    private List<Map<String,Object>> read(Integer id) {
        String sql="SELECT r.*,p.property_name,p.address AS property_address FROM Rooms r JOIN Properties p ON p.property_id=r.property_id WHERE r.status<>N'Bảo trì'"
            +(id==null?" ORDER BY r.room_code":" AND r.room_id=?");
        try(Connection conn=DBConnection.getConnection();PreparedStatement st=conn.prepareStatement(sql)) {
            if(id!=null)st.setInt(1,RoomCatalogMigration.resolveId(conn,id));
            try(ResultSet rs=st.executeQuery()) {
                List<Map<String,Object>> rows=new ArrayList<>();
                while(rs.next()) {
                    Map<String,Object> row=new LinkedHashMap<>();
                    row.put("roomId",rs.getInt("room_id"));row.put("roomCode",rs.getString("room_code"));
                    row.put("roomName",rs.getString("room_name"));row.put("roomType",rs.getString("room_type"));
                    row.put("propertyId",rs.getInt("property_id"));row.put("propertyName",rs.getString("property_name"));row.put("propertyAddress",rs.getString("property_address"));
                    row.put("price",rs.getBigDecimal("price"));row.put("hourlyPrice",rs.getBigDecimal("hourly_price"));
                    row.put("hourlyPackages",HourlyPricing.packages(rs));
                    row.put("nightlyPricing",NightlyPricing.room(rs));
                    row.put("description",rs.getString("description"));rows.add(row);
                }
                return rows;
            }
        } catch(SQLException error) { throw new ApiProblem(503,"CATALOG_UNAVAILABLE","Chưa tải được thông tin phòng. Vui lòng thử lại."); }
    }
}
