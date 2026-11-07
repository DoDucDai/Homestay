package com.homestay.controller;

import com.homestay.db.DBConnection;
import com.homestay.api.*;
import org.springframework.web.bind.annotation.*;
import java.sql.*;
import java.util.*;

// NOTE API: Danh mục dịch vụ dùng chung cho admin/customer; các API /api/statistics chỉ dành cho admin.
// @...Mapping xác định phương thức HTTP và đường dẫn; @RequestBody đọc JSON,
// @PathVariable đọc ID trong URL, @RequestParam đọc tham số sau dấu ?. Quyền truy cập nằm trong SecurityConfig.
@RestController
@RequestMapping("/api")
public class ServiceStatController {
    private final java.time.Clock clock;
    public ServiceStatController() { this(RoomSchedule.CLOCK); }
    ServiceStatController(java.time.Clock clock) { this.clock=clock; }
    private record ServiceValues(String name,long price,String status) {}
    // NOTE BACKEND: Kiểm tra tên, đơn giá và trạng thái dịch vụ rồi gom thành một đối tượng dùng khi thêm/sửa.
    private ServiceValues values(Map<String,Object> body) {
        return new ServiceValues(Input.text(body,"serviceName",100,true,null),Input.money(body,"unitPrice",false),
            Input.oneOf(Input.text(body,"status",20,false,"Hoạt động"),Set.of("Hoạt động","Ngừng"),"Trạng thái dịch vụ"));
    }
    // NOTE BACKEND: Đổi tên cột SQL sang các trường serviceId/serviceName/unitPrice/status mà frontend sử dụng.
    private Map<String,Object> row(ResultSet rs) throws SQLException {
        Map<String,Object> result=new LinkedHashMap<>();result.put("serviceId",rs.getInt("service_id"));
        result.put("serviceName",rs.getString("service_name"));result.put("unitPrice",rs.getBigDecimal("unit_price"));
        result.put("status",rs.getString("status"));return result;
    }
    // NOTE API: GET /api/services trả danh mục dịch vụ gồm ID, tên, đơn giá và trạng thái.
    // Dùng cho trang dịch vụ và lựa chọn dịch vụ khi khách đang lưu trú.
    @GetMapping("/services")
    public List<Map<String,Object>> getAllServices() {
        List<Map<String,Object>> result=new ArrayList<>();
        try(Connection conn=DBConnection.getConnection();PreparedStatement stmt=conn.prepareStatement("SELECT service_id,service_name,unit_price,status FROM Services ORDER BY service_id");ResultSet rs=stmt.executeQuery()) {
            while(rs.next()) result.add(row(rs));
        } catch(SQLException e) { throw ApiErrors.databaseFailure(e); }
        return result;
    }
    // NOTE API: GET /api/services/{id} trả chi tiết một dịch vụ theo ID.
    // Trả lỗi 404 khi ID hợp lệ nhưng không có dịch vụ tương ứng.
    @GetMapping("/services/{id}")
    public Map<String,Object> getServiceById(@PathVariable int id) {
        return ApiErrors.guard(() -> {
            Input.id(id);
            try(Connection conn=DBConnection.getConnection();PreparedStatement stmt=conn.prepareStatement("SELECT service_id,service_name,unit_price,status FROM Services WHERE service_id=?")) {
                stmt.setInt(1,id);try(ResultSet rs=stmt.executeQuery()) { if(rs.next()) return row(rs); }
            } catch(SQLException e) { return ApiErrors.database(e); }
            return ApiErrors.missing("Không tìm thấy dịch vụ!");


        });
    }
    // NOTE API: POST /api/services nhận serviceName, unitPrice và status để tạo dịch vụ.
    // Kiểm tra đơn giá nguyên không âm và trạng thái Hoạt động/Ngừng trước khi lưu.
    @PostMapping("/services")
    public Map<String,Object> createService(@RequestBody Map<String,Object> body) {
        return ApiErrors.guard(() -> {
            ServiceValues value=values(body);
            try(Connection conn=DBConnection.getConnection();PreparedStatement stmt=conn.prepareStatement("INSERT INTO Services(service_name,unit_price,status) VALUES(?,?,?)")) {
                stmt.setString(1,value.name());stmt.setLong(2,value.price());stmt.setString(3,value.status());stmt.executeUpdate();
                return Map.of("success",true,"message","Thêm dịch vụ thành công!");
            } catch(SQLException e) { return ApiErrors.database(e); }


        });
    }
    // NOTE API: PUT /api/services/{id} đổi tên, đơn giá hoặc trạng thái dịch vụ.
    // Chỉ đổi danh mục hiện tại; giá và tên đã chụp trong các lần sử dụng trước vẫn được giữ.
    @PutMapping("/services/{id}")
    public Map<String,Object> updateService(@PathVariable int id,@RequestBody Map<String,Object> body) {
        return ApiErrors.guard(() -> {
            Input.id(id);ServiceValues value=values(body);
            try(Connection conn=DBConnection.getConnection();PreparedStatement stmt=conn.prepareStatement("UPDATE Services SET service_name=?,unit_price=?,status=? WHERE service_id=?")) {
                stmt.setString(1,value.name());stmt.setLong(2,value.price());stmt.setString(3,value.status());stmt.setInt(4,id);
                if(stmt.executeUpdate()!=1) return ApiErrors.missing("Không tìm thấy dịch vụ!");
                return Map.of("success",true,"message","Cập nhật dịch vụ thành công!");
            } catch(SQLException e) { return ApiErrors.database(e); }


        });
    }
    // NOTE API: DELETE /api/services/{id} xóa dịch vụ chưa có dữ liệu sử dụng.
    // Nếu dịch vụ đã được gọi trong booking thì trả lỗi xung đột; có thể đổi sang Ngừng thay vì xóa.
    @DeleteMapping("/services/{id}")
    public Map<String,Object> deleteService(@PathVariable int id) {
        return ApiErrors.guard(() -> {
            Input.id(id);
            return RoomSchedule.transaction(conn -> {
                try(PreparedStatement stmt=conn.prepareStatement("SELECT service_id FROM Services WITH(UPDLOCK,HOLDLOCK) WHERE service_id=?")) {
                    stmt.setInt(1,id);try(ResultSet rs=stmt.executeQuery()) { if(!rs.next()) throw ApiErrors.notFound("Không tìm thấy dịch vụ!"); }
                }
                try(PreparedStatement stmt=conn.prepareStatement("SELECT id FROM Booking_Services WHERE service_id=?")) {
                    stmt.setInt(1,id);try(ResultSet rs=stmt.executeQuery()) { if(rs.next()) throw new RoomSchedule.Conflict("Không thể xóa dịch vụ đã có dữ liệu sử dụng!"); }
                }
                try(PreparedStatement stmt=conn.prepareStatement("DELETE FROM Services WHERE service_id=?")) {
                    stmt.setInt(1,id);if(stmt.executeUpdate()!=1) throw ApiErrors.notFound("Không tìm thấy dịch vụ!");
                }
                return Map.of("success",true,"message","Xóa dịch vụ thành công!");
            },"Không thể xóa dịch vụ!");


        });
    }

    // ==================== STATISTICS ====================

    // GET /api/statistics/overview
    // Return: { totalRooms, availableRooms, bookedRooms, occupiedRooms, maintenanceRooms, totalCustomers, totalBookingsThisMonth, revenueThisMonth }
    public Map<String,Object> getOverview(){return getOverview(null);}
    // NOTE API: GET /api/statistics/overview trả tổng quan phòng, khách, booking và doanh thu tháng hiện tại.
    // Query propertyId dùng để giới hạn cơ sở; doanh thu chỉ cộng hóa đơn đã thanh toán.
    @GetMapping("/statistics/overview")
    public Map<String, Object> getOverview(@RequestParam(required=false) Integer propertyId) {
        if(propertyId!=null)Input.id(propertyId);
        return ApiErrors.guard(() -> {
            Map<String, Object> result = new LinkedHashMap<>();
            java.time.LocalDate today=RoomSchedule.today(clock);

            try (Connection conn = DBConnection.getConnection()) {
                // Query 1 - Thống kê phòng
                String roomSql = """
                    SELECT
                        COUNT(*) AS total_rooms,
                        SUM(CASE WHEN status = N'Trống' THEN 1 ELSE 0 END) AS available_rooms,
                        SUM(CASE WHEN status = N'Đã đặt' THEN 1 ELSE 0 END) AS booked_rooms,
                        SUM(CASE WHEN status = N'Đang sử dụng' THEN 1 ELSE 0 END) AS occupied_rooms,
                        SUM(CASE WHEN status = N'Bảo trì' THEN 1 ELSE 0 END) AS maintenance_rooms
                    FROM (
                    """ + RoomSchedule.selectRooms() + ") room_snapshot"+(propertyId==null?"":" WHERE property_id="+propertyId);
                try (PreparedStatement stmt = conn.prepareStatement(roomSql)) {
                    RoomSchedule.bindToday(stmt, RoomSchedule.today(clock));
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            result.put("totalRooms", rs.getInt("total_rooms"));
                            result.put("availableRooms", rs.getInt("available_rooms"));
                            result.put("bookedRooms", rs.getInt("booked_rooms"));
                            result.put("occupiedRooms", rs.getInt("occupied_rooms"));
                            result.put("maintenanceRooms", rs.getInt("maintenance_rooms"));
                        }
                    }
                }

                // Query 2 - Tổng số khách hàng
                String customerSql = "SELECT COUNT(*) AS total_customers FROM Customers"+(propertyId==null?"":" WHERE customer_id IN (SELECT customer_id FROM Bookings WHERE room_id IN (SELECT room_id FROM Rooms WHERE property_id="+propertyId+"))");
                try (PreparedStatement stmt = conn.prepareStatement(customerSql);
                     ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        result.put("totalCustomers", rs.getInt("total_customers"));
                    }
                }

                // Query 3 - Tổng số booking tháng này
                String bookingSql = """
                    SELECT COUNT(*) AS total_bookings_this_month
                    FROM Bookings
                    WHERE MONTH(created_at) = ? AND YEAR(created_at) = ?
                    """+PropertyController.roomScope(propertyId,"room_id");
                try (PreparedStatement stmt = conn.prepareStatement(bookingSql)) {
                    stmt.setInt(1,today.getMonthValue());stmt.setInt(2,today.getYear());
                    try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        result.put("totalBookingsThisMonth", rs.getInt("total_bookings_this_month"));
                    }
                    }
                }

                // Query 4 - Doanh thu tháng này
                String revenueSql = """
                    SELECT ISNULL(SUM(grand_total), 0) AS revenue_this_month
                    FROM Payments
                    WHERE MONTH(paid_at) = ? AND YEAR(paid_at) = ?
                      AND payment_status = N'Đã thanh toán'
                    """+PropertyController.paymentScope(propertyId,"booking_id");
                try (PreparedStatement stmt = conn.prepareStatement(revenueSql)) {
                    stmt.setInt(1,today.getMonthValue());stmt.setInt(2,today.getYear());
                    try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        result.put("revenueThisMonth", rs.getLong("revenue_this_month"));
                    }
                    }
                }

            } catch (SQLException e) {
                throw ApiErrors.databaseFailure(e);
            }

            return result;


        });
    }

    // GET /api/statistics/revenue?month=9&year=2026
    // Return: [ { paymentId, customerName, roomCode, roomTotal, serviceTotal, grandTotal, paymentMethod, paidAt } ]
    public List<Map<String, Object>> getRevenue(@RequestParam int month, @RequestParam int year) {
        return getRevenue(month,year,null);
    }
    // NOTE API: GET /api/statistics/revenue nhận month, year và propertyId tùy chọn.
    // Trả các hóa đơn đã thanh toán trong tháng; ưu tiên tên khách/mã phòng từ bản chụp hóa đơn cũ.
    @GetMapping("/statistics/revenue")
    public List<Map<String,Object>> getRevenue(@RequestParam int month,@RequestParam int year,@RequestParam(required=false) Integer propertyId) {
        if(month<1 || month>12 || year<1 || year>9999) throw ApiErrors.invalid("Tháng cần từ 1–12, năm cần từ 1–9999!");
        List<Map<String, Object>> result = new ArrayList<>();

        String sql = """
            SELECT
                p.payment_id,
                COALESCE(JSON_VALUE(p.invoice_snapshot,'$.customerName'),c.full_name) AS customer_name,
                COALESCE(JSON_VALUE(p.invoice_snapshot,'$.roomCode'),r.room_code) AS room_code,
                p.room_total,
                p.service_total,
                p.grand_total,
                p.payment_method,
                p.paid_at
            FROM Payments p
            JOIN Bookings b ON p.booking_id = b.booking_id
            JOIN Customers c ON b.customer_id = c.customer_id
            JOIN Rooms r ON b.room_id = r.room_id
            WHERE MONTH(p.paid_at) = ? AND YEAR(p.paid_at) = ?
              AND p.payment_status = N'Đã thanh toán'
            """+PropertyController.roomScope(propertyId,"r.room_id")+" ORDER BY p.paid_at DESC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, month);
            stmt.setInt(2, year);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("paymentId", rs.getInt("payment_id"));
                    row.put("customerName", rs.getString("customer_name"));
                    row.put("roomCode", rs.getString("room_code"));
                    row.put("roomTotal", rs.getLong("room_total"));
                    row.put("serviceTotal", rs.getLong("service_total"));
                    row.put("grandTotal", rs.getLong("grand_total"));
                    row.put("paymentMethod", rs.getString("payment_method"));
                    row.put("paidAt", rs.getString("paid_at"));
                    result.add(row);
                }
            }
        } catch (SQLException e) {
            throw ApiErrors.databaseFailure(e);
        }

        return result;
    }

    public List<java.math.BigDecimal> getYearly(int year){return getYearly(year,null);}
    // NOTE API: GET /api/statistics/yearly nhận year và propertyId tùy chọn để tổng hợp doanh thu năm.
    // Trả mảng đúng 12 phần tử theo tháng 1 đến 12; tháng không có thanh toán nhận giá trị 0.
    @GetMapping("/statistics/yearly")
    public List<java.math.BigDecimal> getYearly(@RequestParam int year,@RequestParam(required=false) Integer propertyId) {
        if(year<1 || year>9999) throw ApiErrors.invalid("Năm cần từ 1–9999!");
        List<java.math.BigDecimal> months=new ArrayList<>(Collections.nCopies(12,java.math.BigDecimal.ZERO));
        try(Connection conn=DBConnection.getConnection();PreparedStatement stmt=conn.prepareStatement("""
            SELECT MONTH(paid_at) AS month,SUM(grand_total) AS amount FROM Payments
            WHERE YEAR(paid_at)=? AND payment_status=N'Đã thanh toán'
            """+PropertyController.paymentScope(propertyId,"booking_id")+" GROUP BY MONTH(paid_at)")) {
            stmt.setInt(1,year);try(ResultSet rs=stmt.executeQuery()) {
                while(rs.next()) months.set(rs.getInt("month")-1,rs.getBigDecimal("amount"));
            }
        } catch(SQLException e) {throw ApiErrors.databaseFailure(e);}
        return months;
    }
}
