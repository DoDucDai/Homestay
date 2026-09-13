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
        // TODO: Ð?c Ð?i (Leader) vi?t
        // SELECT * FROM Customers ORDER BY full_name
        return new ArrayList<>();
    }

    // POST /api/customers
    // Body: { "fullName": "...", "cccd": "...", "phone": "...", "email": "...", "address": "..." }
    // Return: { "success": true }
    @PostMapping
    public Map<String, Object> createCustomer(@RequestBody Map<String, Object> body) {
        // TODO: Ð?c Ð?i (Leader) vi?t
        // INSERT INTO Customers (full_name, cccd, phone, email, address) VALUES (?,?,?,?,?)
        return Map.of("success", false, "message", "Chua implement");
    }

    // PUT /api/customers/{id}
    // Body: { "fullName": "...", "phone": "...", "email": "...", "address": "..." }
    // Return: { "success": true }
    @PutMapping("/{id}")
    public Map<String, Object> updateCustomer(@PathVariable int id, @RequestBody Map<String, Object> body) {
        // TODO: Ð?c Ð?i (Leader) vi?t
        // UPDATE Customers SET full_name=?, phone=?, email=?, address=? WHERE customer_id=?
        return Map.of("success", false, "message", "Chua implement");
    }

    // DELETE /api/customers/{id}
    // Return: { "success": true }
    @DeleteMapping("/{id}")
    public Map<String, Object> deleteCustomer(@PathVariable int id) {
        // TODO: Ð?c Ð?i (Leader) vi?t
        // DELETE FROM Customers WHERE customer_id=?
        return Map.of("success", false, "message", "Chua implement");
    }
}
