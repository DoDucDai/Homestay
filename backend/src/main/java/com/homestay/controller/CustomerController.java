package com.homestay.controller;

import com.homestay.db.DBConnection;
import com.homestay.api.*;
import org.springframework.web.bind.annotation.*;
import java.sql.*;
import java.util.*;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {
    private static final String SELECT="SELECT customer_id,full_name,document_type,cccd,phone,email,address,created_at FROM Customers";
    private String retainedText(Map<String,Object> body,String key,int max,String previous) {
        if(body!=null && previous.equals(body.get(key))) return previous;
        return Input.text(body,key,max,false,previous);
    }
    private Map<String,Object> row(ResultSet rs) throws SQLException {
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("customerId",rs.getInt("customer_id"));result.put("fullName",rs.getString("full_name"));
        result.put("documentType",rs.getString("document_type"));result.put("cccd",rs.getString("cccd"));
        result.put("phone",rs.getString("phone"));result.put("email",rs.getString("email"));result.put("address",rs.getString("address"));
        result.put("createdAt",rs.getString("created_at"));
        List<String> warnings=new ArrayList<>();
        if(!Input.documentValid(rs.getString("document_type"),rs.getString("cccd"))) warnings.add("Cần xác định lại loại và số giấy tờ.");
        if(!rs.getString("phone").matches("0[0-9]{9}")) warnings.add("Số điện thoại cũ cần chuẩn hóa.");
        String email=rs.getString("email");
        if(email!=null && !email.isEmpty() && !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) warnings.add("Email cũ cần chuẩn hóa.");
        result.put("validationWarnings",warnings);return result;
    }
    public List<Map<String,Object>> getAllCustomers() {
        return getAllCustomers(null);
    }
    @GetMapping
    public List<Map<String,Object>> getAllCustomers(@RequestParam(required=false) String search) {
        String keyword=search==null ? "" : search.trim();
        if(keyword.length()>100) throw new ApiProblem(400,"INVALID_INPUT","Tìm kiếm tối đa 100 ký tự!");
        List<Map<String,Object>> result=new ArrayList<>();
        String filter=keyword.isEmpty() ? "" : " WHERE CHARINDEX(?,full_name)>0 OR CHARINDEX(?,cccd)>0 OR CHARINDEX(?,phone)>0";
        try(Connection conn=DBConnection.getConnection();PreparedStatement stmt=conn.prepareStatement(SELECT+filter+" ORDER BY customer_id DESC")) {
            if(!keyword.isEmpty()) for(int i=1;i<=3;i++) stmt.setString(i,keyword);
            try(ResultSet rs=stmt.executeQuery()) { while(rs.next()) result.add(row(rs)); }
        } catch(SQLException e) { throw ApiErrors.databaseFailure(e); }
        return result;
    }
    @GetMapping("/{id}/bookings")
    public Object getCustomerBookings(@PathVariable int id) {
        Map<String,Object> history=getHistory(id);
        return Boolean.TRUE.equals(history.get("success")) ? history.get("bookings") : history;
    }
    @GetMapping("/{id}/history")
    public Map<String,Object> getHistory(@PathVariable int id) {
        return ApiErrors.guard(() -> {
            Input.id(id);
            try(Connection conn=DBConnection.getConnection()) {
                Map<String,Object> customer;
                try(PreparedStatement stmt=conn.prepareStatement(SELECT+" WHERE customer_id=?")) {
                    stmt.setInt(1,id);
                    try(ResultSet rs=stmt.executeQuery()) {
                        if(!rs.next()) throw ApiErrors.notFound("Không tìm thấy khách hàng!");
                        customer=row(rs);
                    }
                }
                List<Map<String,Object>> bookings=new ArrayList<>();
                java.math.BigDecimal total=java.math.BigDecimal.ZERO;
                // Payments is unique per booking. Never join service lines into this query.
                try(PreparedStatement stmt=conn.prepareStatement("""
                    SELECT b.booking_id,b.checkin_date,b.checkout_date,b.status,b.note,
                           DATEDIFF(day,b.checkin_date,b.checkout_date) AS nights,r.room_code,r.room_name,
                           p.payment_id,p.grand_total,p.payment_method,p.paid_at
                    FROM Bookings b JOIN Rooms r ON r.room_id=b.room_id
                    LEFT JOIN Payments p ON p.booking_id=b.booking_id AND p.payment_status=N'Đã thanh toán'
                    WHERE b.customer_id=? ORDER BY b.checkin_date DESC,b.booking_id DESC
                    """)) {
                    stmt.setInt(1,id);
                    try(ResultSet rs=stmt.executeQuery()) {
                        while(rs.next()) {
                            Map<String,Object> booking=new LinkedHashMap<>();
                            booking.put("bookingId",rs.getInt("booking_id"));
                            booking.put("roomCode",rs.getString("room_code"));booking.put("roomName",rs.getString("room_name"));
                            booking.put("checkinDate",rs.getDate("checkin_date").toString());booking.put("checkoutDate",rs.getDate("checkout_date").toString());
                            booking.put("nights",rs.getInt("nights"));booking.put("status",rs.getString("status"));booking.put("note",rs.getString("note"));
                            int paymentId=rs.getInt("payment_id");boolean paid=!rs.wasNull();
                            java.math.BigDecimal amount=paid ? BookingInvoiceSnapshot.money(rs.getBigDecimal("grand_total")) : java.math.BigDecimal.ZERO;
                            booking.put("paymentId",paid ? paymentId : null);booking.put("paidTotal",amount);
                            booking.put("paymentStatus",paid ? "Đã thanh toán" : "Chưa thanh toán");
                            booking.put("paymentMethod",paid ? rs.getString("payment_method") : null);
                            Timestamp paidAt=rs.getTimestamp("paid_at");booking.put("paidAt",paidAt==null ? null : paidAt.toLocalDateTime().toString());
                            total=total.add(amount);bookings.add(booking);
                        }
                    }
                }
                // A customer's lifetime aggregate can exceed JavaScript's exact number range.
                return Map.of("success",true,"customer",customer,"bookings",bookings,"totalPaid",total.toPlainString());
            } catch(SQLException e) { return ApiErrors.database(e); }
        });
    }
    @GetMapping("/{id}")
    public Map<String,Object> getCustomerById(@PathVariable int id) {
        return ApiErrors.guard(() -> {
            Input.id(id);
            try(Connection conn=DBConnection.getConnection();PreparedStatement stmt=conn.prepareStatement(SELECT+" WHERE customer_id=?")) {
                stmt.setInt(1,id);
                try(ResultSet rs=stmt.executeQuery()) { if(rs.next()) return row(rs); }
            } catch(SQLException e) { return ApiErrors.database(e); }
            return ApiErrors.missing("Không tìm thấy khách hàng!");


        });
    }
    @PostMapping
    public Map<String,Object> createCustomer(@RequestBody Map<String,Object> body) {
        return ApiErrors.guard(() -> {
            String name=Input.text(body,"fullName",100,true,null);
            String type=Input.oneOf(Input.text(body,"documentType",12,false,"CCCD"),Set.of("CCCD","PASSPORT"),"Loại giấy tờ");
            String document=Input.text(body,"cccd",20,true,null);Input.document(type,document);
            String phone=Input.phone(Input.text(body,"phone",15,true,null));
            String email=Input.email(Input.text(body,"email",100,false,""));
            String address=Input.text(body,"address",200,false,"");
            try(Connection conn=DBConnection.getConnection();PreparedStatement stmt=conn.prepareStatement(
                    "INSERT INTO Customers(full_name,document_type,cccd,phone,email,address) VALUES(?,?,?,?,?,?)")) {
                stmt.setString(1,name);stmt.setString(2,type);stmt.setString(3,document);stmt.setString(4,phone);stmt.setString(5,email);stmt.setString(6,address);
                stmt.executeUpdate();return Map.of("success",true,"message","Thêm khách hàng thành công!");
            } catch(SQLException e) { return ApiErrors.database(e); }


        });
    }
    @PutMapping("/{id}")
    public Map<String,Object> updateCustomer(@PathVariable int id,@RequestBody Map<String,Object> body) {
        return ApiErrors.guard(() -> {
            Input.id(id);
            return RoomSchedule.transaction(conn -> {
                Map<String,Object> existing;
                try(PreparedStatement stmt=conn.prepareStatement(SELECT+" WITH(UPDLOCK,HOLDLOCK) WHERE customer_id=?")) {
                    stmt.setInt(1,id);try(ResultSet rs=stmt.executeQuery()) {
                        if(!rs.next()) throw ApiErrors.notFound("Không tìm thấy khách hàng!");existing=row(rs);
                    }
                }
                String name=Input.text(body,"fullName",100,true,null);
                String type=Input.text(body,"documentType",12,false,(String)existing.get("documentType"));
                String document=retainedText(body,"cccd",20,(String)existing.get("cccd"));
                if(!Objects.equals(type,existing.get("documentType")) || !Objects.equals(document,existing.get("cccd"))) {
                    Input.oneOf(type,Set.of("CCCD","PASSPORT"),"Loại giấy tờ");Input.document(type,document);
                }
                String phone=retainedText(body,"phone",15,(String)existing.get("phone"));
                if(!Objects.equals(phone,existing.get("phone"))) Input.phone(phone);
                String oldEmail=Objects.toString(existing.get("email"),"");
                String email=retainedText(body,"email",100,oldEmail);
                if(!email.equals(oldEmail)) Input.email(email);
                String address=Input.text(body,"address",200,false,Objects.toString(existing.get("address"),""));
                try(PreparedStatement stmt=conn.prepareStatement("UPDATE Customers SET full_name=?,document_type=?,cccd=?,phone=?,email=?,address=? WHERE customer_id=?")) {
                    stmt.setString(1,name);stmt.setString(2,type);stmt.setString(3,document);stmt.setString(4,phone);stmt.setString(5,email);stmt.setString(6,address);stmt.setInt(7,id);
                    if(stmt.executeUpdate()!=1) throw ApiErrors.notFound("Không tìm thấy khách hàng!");
                }
                return Map.of("success",true,"message","Cập nhật khách hàng thành công!");
            },"Không thể cập nhật khách hàng!");


        });
    }
    @DeleteMapping("/{id}")
    public Map<String,Object> deleteCustomer(@PathVariable int id) {
        return ApiErrors.guard(() -> {
            Input.id(id);
            return RoomSchedule.transaction(conn -> {
                try(PreparedStatement stmt=conn.prepareStatement("SELECT customer_id FROM Customers WITH(UPDLOCK,HOLDLOCK) WHERE customer_id=?")) {
                    stmt.setInt(1,id);try(ResultSet rs=stmt.executeQuery()) { if(!rs.next()) throw ApiErrors.notFound("Không tìm thấy khách hàng!"); }
                }
                try(PreparedStatement stmt=conn.prepareStatement("SELECT booking_id FROM Bookings WHERE customer_id=?")) {
                    stmt.setInt(1,id);try(ResultSet rs=stmt.executeQuery()) {
                        if(rs.next()) throw new RoomSchedule.Conflict("Không thể xóa khách hàng đã có lịch sử đặt phòng!");
                    }
                }
                try(PreparedStatement stmt=conn.prepareStatement("DELETE FROM Customers WHERE customer_id=?")) {
                    stmt.setInt(1,id);if(stmt.executeUpdate()!=1) throw ApiErrors.notFound("Không tìm thấy khách hàng!");
                }
                return Map.of("success",true,"message","Xóa khách hàng thành công!");
            },"Không thể xóa khách hàng!");


        });
    }
}
