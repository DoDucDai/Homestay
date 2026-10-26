package com.homestay.controller;

import com.homestay.db.DBConnection;
import com.homestay.security.*;
import jakarta.servlet.http.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.*;
import org.springframework.security.core.*;
import org.springframework.security.core.context.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.logout.*;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import java.sql.*;
import java.util.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationManager manager;
    private final PasswordEncoder encoder;
    private final SecurityContextRepository repository;
    private final SessionAuthenticationStrategy strategy;
    private final LoginThrottle throttle;

    public AuthController(AuthenticationManager manager,PasswordEncoder encoder,SecurityContextRepository repository,
                          SessionAuthenticationStrategy strategy,LoginThrottle throttle) {
        this.manager=manager;this.encoder=encoder;this.repository=repository;this.strategy=strategy;this.throttle=throttle;
    }
    private String text(Map<String,Object> body,String key) {
        return body!=null && body.get(key) instanceof String value ? value : "";
    }
    private ResponseEntity<Map<String,Object>> error(int status,String code,String message) {
        return ResponseEntity.status(status).body(Map.of("success",false,"code",code,"message",message));
    }
    @GetMapping("/csrf")
    public Map<String,Object> csrf(CsrfToken token) {
        return Map.of("success",true,"token",token.getToken(),"headerName",token.getHeaderName());
    }
    @PostMapping("/login")
    public ResponseEntity<Map<String,Object>> login(@RequestBody(required=false) Map<String,Object> body,
                                                   HttpServletRequest request,HttpServletResponse response) {
        String username=text(body,"username").trim();
        String password=text(body,"password");
        if (!throttle.allow(request.getRemoteAddr(),username.length()>50 ? "invalid-username" : username)) {
            response.setHeader("Retry-After","900");
            return error(429,"RATE_LIMITED","Đã thử đăng nhập quá nhiều lần. Vui lòng thử lại sau 15 phút!");
        }
        if (username.isEmpty() || username.length()>50 || password.isEmpty() || password.length()>256) {
            return error(401,"INVALID_CREDENTIALS","Sai tên đăng nhập hoặc mật khẩu!");
        }
        try {
            Authentication auth=manager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(username,password));
            strategy.onAuthentication(auth,request,response);
            SecurityContext context=SecurityContextHolder.createEmptyContext();context.setAuthentication(auth);
            SecurityContextHolder.setContext(context);repository.saveContext(context,request,response);
            throttle.succeeded(username);
            return ResponseEntity.ok(Map.of("success",true,"message","Đăng nhập thành công!","user",((AppUser)auth.getPrincipal()).profile()));
        } catch (InternalAuthenticationServiceException e) {
            return error(503,"AUTH_UNAVAILABLE","Không thể đăng nhập lúc này. Vui lòng thử lại!");
        } catch (AuthenticationException e) {
            return error(401,"INVALID_CREDENTIALS","Sai tên đăng nhập hoặc mật khẩu!");
        }
    }
    @GetMapping("/me")
    public Map<String,Object> me(Authentication auth) {
        return Map.of("success",true,"user",((AppUser)auth.getPrincipal()).profile());
    }
    private void endSession(HttpServletRequest request,HttpServletResponse response,Authentication auth) {
        new SecurityContextLogoutHandler().logout(request,response,auth);
        new CookieClearingLogoutHandler("HOMESTAY_SESSION").logout(request,response,auth);
        repository.saveContext(SecurityContextHolder.createEmptyContext(),request,response);
    }
    @PostMapping("/logout")
    public Map<String,Object> logout(HttpServletRequest request,HttpServletResponse response,Authentication auth) {
        endSession(request,response,auth);
        return Map.of("success",true,"message","Đã đăng xuất!");
    }
    @PutMapping("/change-password")
    public ResponseEntity<Map<String,Object>> changePassword(@RequestBody(required=false) Map<String,Object> body,
                    HttpServletRequest request,HttpServletResponse response,Authentication auth) {
        AppUser user=(AppUser)auth.getPrincipal();
        String oldPassword=text(body,"oldPassword"),newPassword=text(body,"newPassword");
        if (oldPassword.isEmpty() || oldPassword.length()>256 || newPassword.length()<12 || newPassword.length()>128
                || newPassword.isBlank() || newPassword.equals(oldPassword)) {
            return error(400,"INVALID_PASSWORD","Mật khẩu mới cần 12–128 ký tự, không chỉ khoảng trắng và khác mật khẩu cũ!");
        }
        String limitKey="change:"+user.userId();
        if (!throttle.allow(request.getRemoteAddr(),limitKey)) return error(429,"RATE_LIMITED","Đã thử quá nhiều lần. Vui lòng thử lại sau 15 phút!");
        try(Connection conn=DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try(PreparedStatement stmt=conn.prepareStatement("SELECT password,auth_version,password_scheme FROM Users WITH(UPDLOCK,HOLDLOCK) WHERE user_id=?")) {
                    stmt.setInt(1,user.userId());
                    try(ResultSet rs=stmt.executeQuery()) {
                        if (!rs.next() || rs.getInt("auth_version")!=user.authVersion()) {
                            conn.rollback();return error(401,"UNAUTHENTICATED","Vui lòng đăng nhập lại!");
                        }
                        if (!PasswordHashing.SCHEME.equals(rs.getString("password_scheme")) || !encoder.matches(oldPassword,rs.getString("password"))) {
                            conn.rollback();return error(400,"INVALID_OLD_PASSWORD","Mật khẩu hiện tại không chính xác!");
                        }
                    }
                }
                String hash=encoder.encode(newPassword);
                try(PreparedStatement stmt=conn.prepareStatement("UPDATE Users SET password=?,auth_version=auth_version+1 WHERE user_id=? AND auth_version=?")) {
                    stmt.setString(1,hash);stmt.setInt(2,user.userId());stmt.setInt(3,user.authVersion());
                    if (stmt.executeUpdate()!=1) throw new SQLException("Concurrent credential update");
                }
                conn.commit();
            } catch(Exception e) {
                try { conn.rollback(); } catch(SQLException ignored) {}
                return error(503,"AUTH_UNAVAILABLE","Không thể đổi mật khẩu lúc này. Vui lòng thử lại!");
            }
        } catch(SQLException e) { return error(503,"AUTH_UNAVAILABLE","Không thể đổi mật khẩu lúc này. Vui lòng thử lại!"); }
        throttle.succeeded(limitKey);
        endSession(request,response,auth);
        return ResponseEntity.ok(Map.of("success",true,"message","Đã đổi mật khẩu. Vui lòng đăng nhập lại!"));
    }
}
