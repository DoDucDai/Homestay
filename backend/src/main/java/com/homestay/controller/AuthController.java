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

// NOTE API: Đăng nhập, thông tin tài khoản và phiên đăng nhập tại /api/auth.
// @...Mapping xác định phương thức HTTP và đường dẫn; @RequestBody đọc JSON,
// @PathVariable đọc ID trong URL, @RequestParam đọc tham số sau dấu ?. Quyền truy cập nằm trong SecurityConfig.
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationManager manager;
    private final PasswordEncoder encoder;
    private final SecurityContextRepository repository;
    private final SessionAuthenticationStrategy strategy;
    private final LoginThrottle throttle;
    private final AccountSettings settings;

    public AuthController(AuthenticationManager manager,PasswordEncoder encoder,SecurityContextRepository repository,
                          SessionAuthenticationStrategy strategy,LoginThrottle throttle,AccountSettings settings) {
        this.manager=manager;this.encoder=encoder;this.repository=repository;this.strategy=strategy;this.throttle=throttle;
        this.settings=settings;
    }
    private String text(Map<String,Object> body,String key) {
        return body!=null && body.get(key) instanceof String value ? value : "";
    }
    private ResponseEntity<Map<String,Object>> error(int status,String code,String message) {
        return ResponseEntity.status(status).body(Map.of("success",false,"code",code,"message",message));
    }
    // NOTE API: GET /api/auth/csrf lấy mã chống giả mạo yêu cầu cho phiên hiện tại.
    // Trả token và headerName; frontend gửi lại mã này khi thêm, sửa hoặc xóa dữ liệu.
    @GetMapping("/csrf")
    public Map<String,Object> csrf(CsrfToken token) {
        return Map.of("success",true,"token",token.getToken(),"headerName",token.getHeaderName());
    }
    // NOTE API: POST /api/auth/login nhận JSON username, password để kiểm tra đăng nhập.
    // Giới hạn số lần thử; nếu đúng, tạo phiên trong cookie và trả thông tin user, không trả mật khẩu.
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
    // NOTE API: GET /api/auth/me trả tài khoản đang đăng nhập cùng hồ sơ và trạng thái email.
    // Danh tính lấy từ phiên trên server; không tin userId hoặc role tự gửi từ trình duyệt.
    @GetMapping("/me")
    public Map<String,Object> me(Authentication auth) throws SQLException {
        AppUser user=(AppUser)auth.getPrincipal();
        Map<String,Object> profile=new HashMap<>(user.profile());
        profile.put("emailVerificationEnabled",settings.emailVerificationEnabled());
        try(Connection conn=DBConnection.getConnection();PreparedStatement st=conn.prepareStatement(
                "SELECT full_name,phone,email,birth_date,city,email_verified_at,email_version FROM Users WHERE user_id=?")) {
            st.setInt(1,user.userId());
            try(ResultSet rs=st.executeQuery()) {
                if(rs.next()) {
                    profile.put("fullName",rs.getString("full_name"));
                    for(String key:List.of("phone","email","city")) profile.put(key,rs.getString(key));
                    profile.put("birthDate",rs.getString("birth_date"));
                    profile.put("emailVerified",rs.getTimestamp("email_verified_at")!=null);
                    profile.put("emailVersion",rs.getInt("email_version"));
                }
            }
        }
        return Map.of("success",true,"user",profile);
    }
    // NOTE API: PUT /api/auth/profile cập nhật fullName, phone, email, birthDate và city.
    // Chỉ sửa tài khoản trong phiên; kiểm tra dữ liệu rồi trả lại hồ sơ mới. Đổi email sẽ mất xác thực cũ.
    @PutMapping("/profile")
    public ResponseEntity<Map<String,Object>> updateProfile(@RequestBody(required=false) Map<String,Object> body,Authentication auth) throws SQLException {
        String name=text(body,"fullName").trim(),phone=text(body,"phone").trim(),email=text(body,"email").trim(),city=text(body,"city").trim();
        java.time.LocalDate birth;
        try { birth=java.time.LocalDate.parse(text(body,"birthDate")); }
        catch(Exception e) { return error(400,"INVALID_PROFILE","Ngày sinh không hợp lệ!"); }
        if(name.isEmpty() || name.length()>100 || !AccountPolicy.phone(phone) || !AccountPolicy.email(email)
                || city.isEmpty() || city.length()>100
                || birth.isAfter(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"))) || birth.getYear()<1900)
            return error(400,"INVALID_PROFILE","Nhập đủ họ tên, số điện thoại đúng 10 chữ số bắt đầu bằng 0, email, ngày sinh và thành phố hợp lệ!");
        AppUser user=(AppUser)auth.getPrincipal();
        try { AccountStore.updateProfile(user,name,phone,email,birth,city); }
        catch(com.homestay.api.ApiProblem problem) { return error(problem.status,problem.code,problem.getMessage()); }
        return ResponseEntity.ok(me(auth));
    }
    private void endSession(HttpServletRequest request,HttpServletResponse response,Authentication auth) {
        new SecurityContextLogoutHandler().logout(request,response,auth);
        new CookieClearingLogoutHandler("HOMESTAY_SESSION").logout(request,response,auth);
        repository.saveContext(SecurityContextHolder.createEmptyContext(),request,response);
    }
    // NOTE API: POST /api/auth/logout kết thúc phiên và xóa cookie đăng nhập.
    // Trả success/message để frontend chuyển người dùng về trang đăng nhập.
    @PostMapping("/logout")
    public Map<String,Object> logout(HttpServletRequest request,HttpServletResponse response,Authentication auth) {
        endSession(request,response,auth);
        return Map.of("success",true,"message","Đã đăng xuất!");
    }
    // NOTE API: PUT /api/auth/change-password nhận oldPassword, newPassword.
    // Kiểm tra mật khẩu cũ, lưu mật khẩu mới dưới dạng băm và thu hồi các phiên cũ; cần đăng nhập lại.
    @PutMapping("/change-password")
    public ResponseEntity<Map<String,Object>> changePassword(@RequestBody(required=false) Map<String,Object> body,
                    HttpServletRequest request,HttpServletResponse response,Authentication auth) {
        AppUser user=(AppUser)auth.getPrincipal();
        String oldPassword=text(body,"oldPassword"),newPassword=text(body,"newPassword");
        if (oldPassword.isEmpty() || oldPassword.length()>256 || !AccountPolicy.password(newPassword) || newPassword.equals(oldPassword)) {
            return error(400,"INVALID_PASSWORD","Mật khẩu mới cần 6–128 ký tự, có chữ hoa, chữ thường, chữ số, ký tự đặc biệt và khác mật khẩu cũ!");
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
