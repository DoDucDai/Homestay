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
    // Body: { "username": "...", "password": "..." }
    // Return: { "success": true, "user": { "userId": 1, "username": "...", "fullName": "...", "role": "admin" } }
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, Object> body) {
        // TODO: Duc Dai viet
        // 1. Lay username, password tu body
        // 2. SELECT * FROM Users WHERE username=? AND password=?
        // 3. Neu co ket qua -> tra ve { success: true, user: {...} }
        // 4. Neu khong co -> tra ve { success: false, message: "Sai tai khoan hoac mat khau" }
        return Map.of("success", false, "message", "Chua implement");
    }
}
