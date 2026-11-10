package com.homestay.security;

import org.springframework.security.core.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.*;

// NOTE BACKEND: Thông tin định danh Spring Security giữ trong phiên, kèm role và authVersion để kiểm tra quyền và độ mới của phiên.
public record AppUser(int userId, String username, String password, String fullName,
                      String role, int authVersion) implements UserDetails {
    // NOTE BACKEND: Trả thông tin an toàn cho frontend, chỉ gồm ID, tên đăng nhập, họ tên và quyền; không đưa chuỗi băm mật khẩu vào JSON.
    public Map<String,Object> profile() {
        return Map.of("userId",userId,"username",username,"fullName",fullName,"role",role);
    }
    @Override public String getUsername() { return username; }
    @Override public String getPassword() { return password; }
    // NOTE BACKEND: Chỉ mô tả ID và quyền khi đối tượng được ghi ra log để tránh lộ mật khẩu.
    @Override public String toString() { return "AppUser[userId="+userId+", role="+role+"]"; }
    // NOTE BACKEND: Đổi admin/customer thành ROLE_ADMIN/ROLE_CUSTOMER cho các kiểm tra hasRole của Spring.
    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_"+role.toUpperCase(Locale.ROOT)));
    }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    // NOTE BACKEND: Chỉ bật đăng nhập với hai quyền đang hỗ trợ; tài khoản còn quyền cũ hoặc quyền lạ không được kích hoạt.
    @Override public boolean isEnabled() { return Set.of("admin","customer").contains(role); }
}
