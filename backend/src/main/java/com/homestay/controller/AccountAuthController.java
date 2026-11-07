package com.homestay.controller;

import com.homestay.api.*;
import com.homestay.security.*;
import jakarta.servlet.http.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.sql.*;
import java.util.*;

@RestController
@RequestMapping("/api/auth")
public class AccountAuthController {
    private final AuthController auth;
    private final AccountEmailActions email;
    public AccountAuthController(AuthController auth,AccountEmailActions email) { this.auth=auth;this.email=email; }
    @PostMapping("/register")
    public ResponseEntity<Map<String,Object>> register(@RequestBody Map<String,Object> body,HttpServletRequest request,HttpServletResponse response) {
        AccountStore.limit("register:"+request.getRemoteAddr(),10,3600);
        String username=Input.text(body,"username",50,true,null),password=Input.text(body,"password",128,true,null);
        String name=Input.text(body,"fullName",100,true,null),phone=Input.text(body,"phone",10,true,null);
        String address=AccountStore.normalize(Input.text(body,"email",254,true,null));
        if(!username.matches("[A-Za-z0-9_.-]{6,50}") || !AccountPolicy.password(password) || !AccountPolicy.phone(phone) || !AccountPolicy.email(address))throw ApiErrors.invalid("Kiểm tra họ tên, tên đăng nhập (6–50 ký tự), số điện thoại, email và mật khẩu.");
        // Passwords are never trimmed, and registration never accepts client-selected roles.
        String raw=body.get("password") instanceof String value?value:"";
        if(!AccountPolicy.password(raw))throw ApiErrors.invalid("Mật khẩu không đúng chính sách.");
        String hash=PasswordHashing.encoder().encode(raw);
        AccountStore.transaction(connection->{
            AccountStore.lock(connection,"email:"+address);
            try(var statement=connection.prepareStatement("SELECT COUNT(*) FROM Users WHERE LOWER(LTRIM(RTRIM(email)))=?")) {
                statement.setString(1,address);try(var rows=statement.executeQuery()) { rows.next();if(rows.getInt(1)>0)throw new ApiProblem(409,"EMAIL_IN_USE","Email đã được sử dụng. Bạn có thể đăng nhập hoặc khôi phục tài khoản."); }
            }
            int id;
            try(var statement=connection.prepareStatement("INSERT Users(username,password,password_scheme,full_name,role,phone,email) VALUES(?,?,?,?,'customer',?,?)",Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1,username);statement.setString(2,hash);statement.setString(3,PasswordHashing.SCHEME);statement.setString(4,name);statement.setString(5,phone);statement.setString(6,address);statement.executeUpdate();
                try(var keys=statement.getGeneratedKeys()) { if(!keys.next())throw new SQLException("Missing user key");id=keys.getInt(1); }
            }
            AccountStore.claimEmail(connection,id,address);return id;
        });
        return auth.login(Map.of("username",username,"password",raw),request,response);
    }
    @PostMapping("/email-verification/request")
    public Map<String,Object> requestVerification(Authentication authentication,HttpServletRequest request) {
        return email.requestVerification((AppUser)authentication.getPrincipal(),request.getRemoteAddr());
    }
    @PostMapping("/email-verification/confirm")
    public Map<String,Object> confirmVerification(@RequestBody Map<String,Object> body,Authentication authentication,HttpServletRequest request) {
        return email.confirmVerification((AppUser)authentication.getPrincipal(),Input.text(body,"code",6,true,null),request.getRemoteAddr());
    }
    @PostMapping("/forgot-password")
    public Map<String,Object> forgotPassword(@RequestBody Map<String,Object> body,HttpServletRequest request) {
        return email.forgotPassword(Input.text(body,"email",254,true,null),request.getRemoteAddr());
    }
    @PostMapping("/reset-password")
    public Map<String,Object> resetPassword(@RequestBody Map<String,Object> body,HttpServletRequest request,HttpServletResponse response,Authentication authentication) {
        AccountStore.limit("reset-attempt:"+request.getRemoteAddr(),20,900);
        String password=body.get("password") instanceof String value?value:"";
        var result=email.resetPassword(Input.text(body,"token",80,true,null),password);
        auth.logout(request,response,authentication);return result;
    }
}
