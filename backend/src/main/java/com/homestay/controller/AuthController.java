package com.homestay.controller;
import com.homestay.db.DBConnection;
import org.springframework.web.bind.annotation.*;
import java.sql.*; import java.util.*;

/** AUTH CONTROLLER - NGUOI LAM: DUC DAI */
@RestController @RequestMapping("/api/auth") @CrossOrigin(origins = "*")
public class AuthController {
    // POST /api/auth/login -> kiem tra username/password trong bang Users
    @PostMapping("/login")
    public Map<String,Object> login(@RequestBody Map<String,Object> body) {
        // TODO: SELECT * FROM Users WHERE username=? AND password=?
        return Map.of("success", false, "message", "Chua implement");
    }
}
