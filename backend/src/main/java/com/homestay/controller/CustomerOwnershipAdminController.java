package com.homestay.controller;

import com.homestay.api.*;
import com.homestay.security.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** Explicit linking after an administrator has verified an existing guest profile. */
@RestController
@RequestMapping("/api/admin/customer-links")
public class CustomerOwnershipAdminController {
    @PutMapping("/{userId}")
    public Map<String,Object> link(@PathVariable int userId,@RequestBody Map<String,Object> body) {
        CustomerOwnership.allowedFields(body,Set.of("customerId"));
        int id=Input.integer(body,"customerId",1,Integer.MAX_VALUE);
        return AccountStore.transaction(connection->{
            CustomerOwnership.linkExisting(connection,userId,id);
            return Map.of("success",true,"message","Đã liên kết tài khoản với hồ sơ được kiểm tra.");
        });
    }
}
