package com.homestay.controller;
import com.homestay.db.DBConnection;
import org.springframework.web.bind.annotation.*;
import java.sql.*; import java.util.*;

/** CUSTOMER CONTROLLER - NGUOI LAM: DUC DAI */
@RestController @RequestMapping("/api/customers") @CrossOrigin(origins = "*")
public class CustomerController {
    @GetMapping    public List<Map<String,Object>> getAll()                                     { return new ArrayList<>(); }
    @PostMapping   public Map<String,Object> create(@RequestBody Map<String,Object> b)         { return Map.of("success",false); }
    @PutMapping("/{id}") public Map<String,Object> update(@PathVariable int id,@RequestBody Map<String,Object> b) { return Map.of("success",false); }
    @DeleteMapping("/{id}") public Map<String,Object> delete(@PathVariable int id)             { return Map.of("success",false); }
}
