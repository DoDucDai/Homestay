package com.homestay.controller;

import com.homestay.api.*;
import com.homestay.db.DBConnection;
import org.springframework.web.bind.annotation.*;
import java.sql.*;
import java.util.*;

@RestController
@RequestMapping("/api/public/properties")
public class PropertyController {
    @GetMapping public List<Map<String,Object>> list() {
        try(var conn=DBConnection.getConnection();var st=conn.createStatement();var rs=st.executeQuery("SELECT property_id,property_name,address FROM dbo.Properties ORDER BY property_id")) {
            List<Map<String,Object>> rows=new ArrayList<>();
            while(rs.next())rows.add(Map.of("propertyId",rs.getInt(1),"propertyName",rs.getString(2),"propertyAddress",rs.getString(3)));
            return rows;
        } catch(SQLException error){throw ApiErrors.databaseFailure(error);}
    }
    static void require(Connection conn,int id)throws SQLException {
        Input.id(id);
        try(var st=conn.prepareStatement("SELECT 1 FROM dbo.Properties WHERE property_id=?")) {
            st.setInt(1,id);try(var rs=st.executeQuery()){if(!rs.next())throw ApiErrors.invalid("Cơ sở không tồn tại.");}
        }
    }
    // Integer is validated before interpolation; no user-supplied SQL identifiers are accepted.
    static String roomScope(Integer id,String column) {
        return id==null?"":" AND "+column+" IN (SELECT room_id FROM dbo.Rooms WHERE property_id="+Input.id(id)+")";
    }
    static String paymentScope(Integer id,String column) {
        return id==null?"":" AND "+column+" IN (SELECT b.booking_id FROM dbo.Bookings b JOIN dbo.Rooms r ON r.room_id=b.room_id WHERE r.property_id="+Input.id(id)+")";
    }
}
