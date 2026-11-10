package com.homestay.controller;

import com.homestay.api.*;
import com.homestay.db.DBConnection;
import org.springframework.web.bind.annotation.*;
import java.sql.*;
import java.util.*;

// NOTE API: API cơ sở công khai và các điều kiện lọc SQL dùng chung cho phòng, lượt đặt và hóa đơn.
// @...Mapping xác định phương thức HTTP và đường dẫn; @RequestBody đọc JSON,
// @PathVariable đọc ID trong URL, @RequestParam đọc tham số sau dấu ?. Quyền truy cập nằm trong SecurityConfig.
@RestController
@RequestMapping("/api/public/properties")
public class PropertyController {
    // NOTE API: GET /api/public/properties trả danh sách cơ sở gồm ID, tên và địa chỉ.
    // Không cần đăng nhập; frontend dùng kết quả để tạo bộ chọn cơ sở.
    @GetMapping public List<Map<String,Object>> list() {
        try(var conn=DBConnection.getConnection();var st=conn.createStatement();var rs=st.executeQuery("SELECT property_id,property_name,address FROM dbo.Properties ORDER BY property_id")) {
            List<Map<String,Object>> rows=new ArrayList<>();
            while(rs.next())rows.add(Map.of("propertyId",rs.getInt(1),"propertyName",rs.getString(2),"propertyAddress",rs.getString(3)));
            return rows;
        } catch(SQLException error){throw ApiErrors.databaseFailure(error);}
    }
    // NOTE API: Hàm hỗ trợ kiểm tra propertyId là số dương và cơ sở đã có trong database.
    static void require(Connection conn,int id)throws SQLException {
        Input.id(id);
        try(var st=conn.prepareStatement("SELECT 1 FROM dbo.Properties WHERE property_id=?")) {
            st.setInt(1,id);try(var rs=st.executeQuery()){if(!rs.next())throw ApiErrors.invalid("Cơ sở không tồn tại.");}
        }
    }
    // Integer is validated before interpolation; no user-supplied SQL identifiers are accepted.
    // NOTE API: Tạo điều kiện SQL giới hạn các phòng thuộc cơ sở; không chọn cơ sở thì không thêm điều kiện.
    // column là tên cột do code nội bộ truyền vào, còn ID phải được kiểm tra trước khi ghép SQL.
    static String roomScope(Integer id,String column) {
        return id==null?"":" AND "+column+" IN (SELECT room_id FROM dbo.Rooms WHERE property_id="+Input.id(id)+")";
    }
    // NOTE API: Lọc hóa đơn theo cơ sở thông qua quan hệ Bookings -> Rooms, tránh trộn doanh thu giữa các cơ sở.
    static String paymentScope(Integer id,String column) {
        return id==null?"":" AND "+column+" IN (SELECT b.booking_id FROM dbo.Bookings b JOIN dbo.Rooms r ON r.room_id=b.room_id WHERE r.property_id="+Input.id(id)+")";
    }
}
