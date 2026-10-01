package com.homestay.controller;

import com.homestay.db.DBConnection;
import org.springframework.web.bind.annotation.*;
import java.sql.*;
import java.util.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ServiceStatController {

    private long parseLong(Object val, long defaultVal) {
        if (val == null) return defaultVal;
        if (val instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(val.toString().trim());
        } catch (Exception e) {
            return defaultVal;
        }
    }

    // ==================== SERVICES ====================

    // GET /api/services
    // Return: [ { serviceId, serviceName, unitPrice, status } ]
    @GetMapping("/services")
    public List<Map<String, Object>> getAllServices() {
        List<Map<String, Object>> result = new ArrayList<>();
        String sql = "SELECT service_id, service_name, unit_price, status FROM Services ORDER BY service_id ASC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("serviceId", rs.getInt("service_id"));
                row.put("serviceName", rs.getString("service_name"));
                row.put("unitPrice", rs.getLong("unit_price"));
                row.put("status", rs.getString("status"));
                result.add(row);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;
    }

    // GET /api/services/{id}
    @GetMapping("/services/{id}")
    public Map<String, Object> getServiceById(@PathVariable int id) {
        String sql = "SELECT service_id, service_name, unit_price, status FROM Services WHERE service_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("serviceId", rs.getInt("service_id"));
                    row.put("serviceName", rs.getString("service_name"));
                    row.put("unitPrice", rs.getLong("unit_price"));
                    row.put("status", rs.getString("status"));
                    return row;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Map.of("success", false, "message", "Không tìm thấy dịch vụ!");
    }

    // POST /api/services
    // Body: { "serviceName": "...", "unitPrice": 100000, "status": "Hoạt động" }
    @PostMapping("/services")
    public Map<String, Object> createService(@RequestBody Map<String, Object> body) {
        String serviceName = body.get("serviceName") != null ? body.get("serviceName").toString().trim() : "";
        long unitPrice = parseLong(body.get("unitPrice"), 0);
        String status = body.get("status") != null && !body.get("status").toString().trim().isEmpty()
                ? body.get("status").toString().trim() : "Hoạt động";

        if (serviceName.isEmpty() || unitPrice < 0) {
            return Map.of("success", false, "message", "Tên dịch vụ và đơn giá không hợp lệ!");
        }

        String checkSql = "SELECT COUNT(*) FROM Services WHERE service_name = ?";
        String insertSql = "INSERT INTO Services (service_name, unit_price, status) VALUES (?, ?, ?)";

        try (Connection conn = DBConnection.getConnection()) {
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setString(1, serviceName);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        return Map.of("success", false, "message", "Tên dịch vụ đã tồn tại trong danh mục!");
                    }
                }
            }

            try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                stmt.setString(1, serviceName);
                stmt.setLong(2, unitPrice);
                stmt.setString(3, status);
                stmt.executeUpdate();

                return Map.of("success", true, "message", "Thêm dịch vụ thành công!");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return Map.of("success", false, "message", "Lỗi lưu dịch vụ: " + e.getMessage());
        }
    }

    // PUT /api/services/{id}
    @PutMapping("/services/{id}")
    public Map<String, Object> updateService(@PathVariable int id, @RequestBody Map<String, Object> body) {
        String serviceName = body.get("serviceName") != null ? body.get("serviceName").toString().trim() : "";
        long unitPrice = parseLong(body.get("unitPrice"), 0);
        String status = body.get("status") != null ? body.get("status").toString().trim() : "Hoạt động";

        if (serviceName.isEmpty() || unitPrice < 0) {
            return Map.of("success", false, "message", "Tên dịch vụ và đơn giá không được để trống!");
        }

        try (Connection conn = DBConnection.getConnection()) {
            String checkSql = "SELECT COUNT(*) FROM Services WHERE service_name = ? AND service_id <> ?";
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setString(1, serviceName);
                checkStmt.setInt(2, id);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        return Map.of("success", false, "message", "Tên dịch vụ đã bị trùng với dịch vụ khác!");
                    }
                }
            }

            String updateSql = "UPDATE Services SET service_name = ?, unit_price = ?, status = ? WHERE service_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(updateSql)) {
                stmt.setString(1, serviceName);
                stmt.setLong(2, unitPrice);
                stmt.setString(3, status);
                stmt.setInt(4, id);

                int affected = stmt.executeUpdate();
                if (affected > 0) {
                    return Map.of("success", true, "message", "Cập nhật dịch vụ thành công!");
                } else {
                    return Map.of("success", false, "message", "Không tìm thấy dịch vụ để cập nhật!");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return Map.of("success", false, "message", "Lỗi cập nhật dịch vụ: " + e.getMessage());
        }
    }

    // DELETE /api/services/{id}
    @DeleteMapping("/services/{id}")
    public Map<String, Object> deleteService(@PathVariable int id) {
        try (Connection conn = DBConnection.getConnection()) {
            // Kiểm tra dịch vụ có đang được dùng trong Booking_Services không
            String checkSql = "SELECT COUNT(*) FROM Booking_Services WHERE service_id = ?";
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setInt(1, id);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        return Map.of("success", false, "message", "Không thể xóa dịch vụ đã có dữ liệu sử dụng trong đặt phòng!");
                    }
                }
            }

            String deleteSql = "DELETE FROM Services WHERE service_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
                stmt.setInt(1, id);
                int affected = stmt.executeUpdate();
                if (affected > 0) {
                    return Map.of("success", true, "message", "Xóa dịch vụ thành công!");
                } else {
                    return Map.of("success", false, "message", "Không tìm thấy dịch vụ để xóa!");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return Map.of("success", false, "message", "Lỗi xóa dịch vụ: " + e.getMessage());
        }
    }

    // ==================== STATISTICS ====================

    // GET /api/statistics/overview
    // Return: { totalRooms, availableRooms, bookedRooms, occupiedRooms, maintenanceRooms, totalCustomers, totalBookingsThisMonth, revenueThisMonth }
    @GetMapping("/statistics/overview")
    public Map<String, Object> getOverview() {
        Map<String, Object> result = new LinkedHashMap<>();

        try (Connection conn = DBConnection.getConnection()) {
            // Query 1 - Thống kê phòng
            String roomSql = """
                SELECT
                    COUNT(*) AS total_rooms,
                    SUM(CASE WHEN status = N'Trống' THEN 1 ELSE 0 END) AS available_rooms,
                    SUM(CASE WHEN status = N'Đã đặt' THEN 1 ELSE 0 END) AS booked_rooms,
                    SUM(CASE WHEN status = N'Đang sử dụng' THEN 1 ELSE 0 END) AS occupied_rooms,
                    SUM(CASE WHEN status = N'Bảo trì' THEN 1 ELSE 0 END) AS maintenance_rooms
                FROM Rooms
                """;
            try (PreparedStatement stmt = conn.prepareStatement(roomSql);
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    result.put("totalRooms", rs.getInt("total_rooms"));
                    result.put("availableRooms", rs.getInt("available_rooms"));
                    result.put("bookedRooms", rs.getInt("booked_rooms"));
                    result.put("occupiedRooms", rs.getInt("occupied_rooms"));
                    result.put("maintenanceRooms", rs.getInt("maintenance_rooms"));
                }
            }

            // Query 2 - Tổng số khách hàng
            String customerSql = "SELECT COUNT(*) AS total_customers FROM Customers";
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
                WHERE MONTH(created_at) = MONTH(GETDATE())
                  AND YEAR(created_at)  = YEAR(GETDATE())
                """;
            try (PreparedStatement stmt = conn.prepareStatement(bookingSql);
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    result.put("totalBookingsThisMonth", rs.getInt("total_bookings_this_month"));
                }
            }

            // Query 4 - Doanh thu tháng này
            String revenueSql = """
                SELECT ISNULL(SUM(grand_total), 0) AS revenue_this_month
                FROM Payments
                WHERE MONTH(paid_at) = MONTH(GETDATE())
                  AND YEAR(paid_at)  = YEAR(GETDATE())
                  AND payment_status = N'Đã thanh toán'
                """;
            try (PreparedStatement stmt = conn.prepareStatement(revenueSql);
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    result.put("revenueThisMonth", rs.getLong("revenue_this_month"));
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return result;
    }

    // GET /api/statistics/revenue?month=9&year=2026
    // Return: [ { paymentId, customerName, roomCode, roomTotal, serviceTotal, grandTotal, paymentMethod, paidAt } ]
    @GetMapping("/statistics/revenue")
    public List<Map<String, Object>> getRevenue(@RequestParam int month, @RequestParam int year) {
        List<Map<String, Object>> result = new ArrayList<>();

        String sql = """
            SELECT
                p.payment_id,
                c.full_name AS customer_name,
                r.room_code,
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
            ORDER BY p.paid_at DESC
            """;

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
            e.printStackTrace();
        }

        return result;
    }
}