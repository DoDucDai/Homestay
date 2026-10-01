package com.homestay.controller;

import com.homestay.db.DBConnection;
import org.springframework.web.bind.annotation.*;
import java.sql.*;
import java.util.*;

@RestController
@RequestMapping("/api/customers")
@CrossOrigin(origins = "*")
public class CustomerController {

    // GET /api/customers
    // Return: [ { customerId, fullName, cccd, phone, email, address } ]
    @GetMapping
    public List<Map<String, Object>> getAllCustomers() {
        List<Map<String, Object>> result = new ArrayList<>();
        String sql = "SELECT customer_id, full_name, cccd, phone, email, address, created_at FROM Customers ORDER BY customer_id DESC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("customerId", rs.getInt("customer_id"));
                row.put("fullName", rs.getString("full_name"));
                row.put("cccd", rs.getString("cccd"));
                row.put("phone", rs.getString("phone"));
                row.put("email", rs.getString("email"));
                row.put("address", rs.getString("address"));
                row.put("createdAt", rs.getString("created_at"));
                result.add(row);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;
    }

    // GET /api/customers/{id}
    @GetMapping("/{id}")
    public Map<String, Object> getCustomerById(@PathVariable int id) {
        String sql = "SELECT customer_id, full_name, cccd, phone, email, address FROM Customers WHERE customer_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("customerId", rs.getInt("customer_id"));
                    row.put("fullName", rs.getString("full_name"));
                    row.put("cccd", rs.getString("cccd"));
                    row.put("phone", rs.getString("phone"));
                    row.put("email", rs.getString("email"));
                    row.put("address", rs.getString("address"));
                    return row;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Map.of("success", false, "message", "Không tìm thấy khách hàng!");
    }

    // POST /api/customers
    // Body: { "fullName": "...", "cccd": "...", "phone": "...", "email": "...", "address": "..." }
    @PostMapping
    public Map<String, Object> createCustomer(@RequestBody Map<String, Object> body) {
        String fullName = body.get("fullName") != null ? body.get("fullName").toString().trim() : "";
        String cccd = body.get("cccd") != null ? body.get("cccd").toString().trim() : "";
        String phone = body.get("phone") != null ? body.get("phone").toString().trim() : "";
        String email = body.get("email") != null ? body.get("email").toString().trim() : "";
        String address = body.get("address") != null ? body.get("address").toString().trim() : "";

        if (fullName.isEmpty() || cccd.isEmpty() || phone.isEmpty()) {
            return Map.of("success", false, "message", "Vui lòng nhập đầy đủ họ tên, CCCD/Passport và số điện thoại!");
        }

        String checkSql = "SELECT COUNT(*) FROM Customers WHERE cccd = ?";
        String insertSql = "INSERT INTO Customers (full_name, cccd, phone, email, address) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DBConnection.getConnection()) {
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setString(1, cccd);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        return Map.of("success", false, "message", "Số CCCD/Passport này đã tồn tại trong hệ thống!");
                    }
                }
            }

            try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                stmt.setString(1, fullName);
                stmt.setString(2, cccd);
                stmt.setString(3, phone);
                stmt.setString(4, email);
                stmt.setString(5, address);
                stmt.executeUpdate();

                return Map.of("success", true, "message", "Thêm khách hàng thành công!");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return Map.of("success", false, "message", "Lỗi lưu khách hàng: " + e.getMessage());
        }
    }

    // PUT /api/customers/{id}
    @PutMapping("/{id}")
    public Map<String, Object> updateCustomer(@PathVariable int id, @RequestBody Map<String, Object> body) {
        String fullName = body.get("fullName") != null ? body.get("fullName").toString().trim() : "";
        String cccd = body.get("cccd") != null ? body.get("cccd").toString().trim() : "";
        String phone = body.get("phone") != null ? body.get("phone").toString().trim() : "";
        String email = body.get("email") != null ? body.get("email").toString().trim() : "";
        String address = body.get("address") != null ? body.get("address").toString().trim() : "";

        if (fullName.isEmpty() || phone.isEmpty()) {
            return Map.of("success", false, "message", "Họ tên và số điện thoại không được để trống!");
        }

        try (Connection conn = DBConnection.getConnection()) {
            // Kiểm tra trùng CCCD với khách hàng khác nếu có thay đổi cccd
            if (!cccd.isEmpty()) {
                String checkSql = "SELECT COUNT(*) FROM Customers WHERE cccd = ? AND customer_id <> ?";
                try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                    checkStmt.setString(1, cccd);
                    checkStmt.setInt(2, id);
                    try (ResultSet rs = checkStmt.executeQuery()) {
                        if (rs.next() && rs.getInt(1) > 0) {
                            return Map.of("success", false, "message", "Số CCCD/Passport đã thuộc về khách hàng khác!");
                        }
                    }
                }
            }

            String updateSql = !cccd.isEmpty()
                    ? "UPDATE Customers SET full_name = ?, cccd = ?, phone = ?, email = ?, address = ? WHERE customer_id = ?"
                    : "UPDATE Customers SET full_name = ?, phone = ?, email = ?, address = ? WHERE customer_id = ?";

            try (PreparedStatement stmt = conn.prepareStatement(updateSql)) {
                int idx = 1;
                stmt.setString(idx++, fullName);
                if (!cccd.isEmpty()) {
                    stmt.setString(idx++, cccd);
                }
                stmt.setString(idx++, phone);
                stmt.setString(idx++, email);
                stmt.setString(idx++, address);
                stmt.setInt(idx, id);

                int affected = stmt.executeUpdate();
                if (affected > 0) {
                    return Map.of("success", true, "message", "Cập nhật khách hàng thành công!");
                } else {
                    return Map.of("success", false, "message", "Không tìm thấy khách hàng để cập nhật!");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return Map.of("success", false, "message", "Lỗi cập nhật: " + e.getMessage());
        }
    }

    // DELETE /api/customers/{id}
    @DeleteMapping("/{id}")
    public Map<String, Object> deleteCustomer(@PathVariable int id) {
        try (Connection conn = DBConnection.getConnection()) {
            // Kiểm tra ràng buộc với bảng Bookings
            String checkSql = "SELECT COUNT(*) FROM Bookings WHERE customer_id = ?";
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setInt(1, id);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        return Map.of("success", false, "message", "Không thể xóa khách hàng đã có lịch sử đặt phòng!");
                    }
                }
            }

            String deleteSql = "DELETE FROM Customers WHERE customer_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
                stmt.setInt(1, id);
                int affected = stmt.executeUpdate();
                if (affected > 0) {
                    return Map.of("success", true, "message", "Xóa khách hàng thành công!");
                } else {
                    return Map.of("success", false, "message", "Không tìm thấy khách hàng để xóa!");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return Map.of("success", false, "message", "Lỗi xóa khách hàng: " + e.getMessage());
        }
    }
}