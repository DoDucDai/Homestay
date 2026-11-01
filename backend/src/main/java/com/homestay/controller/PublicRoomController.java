package com.homestay.controller;

import com.homestay.api.*;
import com.homestay.db.DBConnection;
import org.springframework.web.bind.annotation.*;
import java.sql.*;
import java.util.*;

/** Public presentation fields only; booking and operational data remain protected. */
@RestController
@RequestMapping("/api/public/rooms")
public class PublicRoomController {
    @GetMapping
    public List<Map<String,Object>> list() { return read(null); }

    @GetMapping("/{id}")
    public Map<String,Object> detail(@PathVariable int id) {
        Input.id(id);
        var rows=read(id);
        if(rows.isEmpty()) throw new ApiProblem(404,"NOT_FOUND","Phòng không còn trong danh mục giới thiệu.");
        return rows.get(0);
    }
    private List<Map<String,Object>> read(Integer id) {
        String sql="SELECT room_id,room_code,room_name,room_type,price,hourly_price,description FROM Rooms WHERE status<>N'Bảo trì'"
            +(id==null?" ORDER BY room_code":" AND room_id=?");
        try(Connection conn=DBConnection.getConnection();PreparedStatement st=conn.prepareStatement(sql)) {
            if(id!=null)st.setInt(1,id);
            try(ResultSet rs=st.executeQuery()) {
                List<Map<String,Object>> rows=new ArrayList<>();
                while(rs.next()) {
                    Map<String,Object> row=new LinkedHashMap<>();
                    row.put("roomId",rs.getInt("room_id"));row.put("roomCode",rs.getString("room_code"));
                    row.put("roomName",rs.getString("room_name"));row.put("roomType",rs.getString("room_type"));
                    row.put("price",rs.getBigDecimal("price"));row.put("hourlyPrice",rs.getBigDecimal("hourly_price"));
                    row.put("description",rs.getString("description"));rows.add(row);
                }
                return rows;
            }
        } catch(SQLException error) { throw new ApiProblem(503,"CATALOG_UNAVAILABLE","Chưa tải được thông tin phòng. Vui lòng thử lại."); }
    }
}
