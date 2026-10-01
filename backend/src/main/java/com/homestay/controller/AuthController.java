package com.homestay.controller;

import com.homestay.db.DBConnection;
import org.springframework.web.bind.annotation.*;
import java.sql.*;
import java.util.*;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    // POST /api/auth/login
    // Body: { "username": "admin", "password": "123" }
    // Return: { "success": true, "user": { "userId": 1, "username": "...", "fullName": "...", "role": "admin" } }
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, Object> body) {
        String username = body.get("username") != null ? body.get("username").toString().trim() : "";
        String password = body.get("password") != null ? body.get("password").toString().trim() : "";

        if (username.isEmpty() || password.isEmpty()) {
            return Map.of("success", false, "message", "Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu!");
        }

        String sql = "SELECT user_id, username, full_name, role FROM Users WHERE username = ? AND password = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);
            stmt.setString(2, password);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> user = new LinkedHashMap<>();
                    user.put("userId", rs.getInt("user_id"));
                    user.put("username", rs.getString("username"));
                    user.put("fullName", rs.getString("full_name"));
                    user.put("role", rs.getString("role"));

                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("success", true);
                    response.put("message", "Đăng nhập thành công!");
                    response.put("user", user);
                    return response;
                } else {
                    return Map.of("success", false, "message", "Sai tên đăng nhập hoặc mật khẩu!");
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return Map.of("success", false, "message", "Lỗi cơ sở dữ liệu: " + e.getMessage());
        }
    }
}