package com.homestay.security;

import org.springframework.security.core.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.*;

public record AppUser(int userId, String username, String password, String fullName,
                      String role, int authVersion) implements UserDetails {
    public Map<String,Object> profile() {
        return Map.of("userId",userId,"username",username,"fullName",fullName,"role",role);
    }
    @Override public String getUsername() { return username; }
    @Override public String getPassword() { return password; }
    @Override public String toString() { return "AppUser[userId="+userId+", role="+role+"]"; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_"+role.toUpperCase(Locale.ROOT)));
    }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return Set.of("admin","staff").contains(role); }
}
