package com.homestay.security;

import com.homestay.db.DBConnection;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import java.sql.*;
import java.util.Locale;

@Service
public class UserAccounts implements UserDetailsService {
    @Override public AppUser loadUserByUsername(String username) {
        try (Connection conn=DBConnection.getConnection(); PreparedStatement stmt=conn.prepareStatement(
                "SELECT user_id,username,password,full_name,role,auth_version,password_scheme FROM Users WHERE username=?")) {
            stmt.setString(1,username);
            try(ResultSet rs=stmt.executeQuery()) {
                if (!rs.next() || !PasswordHashing.SCHEME.equals(rs.getString("password_scheme"))
                        || !PasswordHashing.validHash(rs.getString("password"))) {
                    throw new UsernameNotFoundException("Invalid credentials");
                }
                return new AppUser(rs.getInt("user_id"),rs.getString("username"),rs.getString("password"),
                        rs.getString("full_name"),rs.getString("role").toLowerCase(Locale.ROOT),rs.getInt("auth_version"));
            }
        } catch(SQLException e) { throw new org.springframework.security.authentication.InternalAuthenticationServiceException("Account store unavailable",e); }
    }

    public boolean sessionCurrent(AppUser user) throws SQLException {
        try(Connection conn=DBConnection.getConnection();PreparedStatement stmt=conn.prepareStatement(
                "SELECT auth_version,role,password_scheme FROM Users WHERE user_id=?")) {
            stmt.setInt(1,user.userId());
            try(ResultSet rs=stmt.executeQuery()) {
                return rs.next() && rs.getInt("auth_version")==user.authVersion()
                        && user.role().equalsIgnoreCase(rs.getString("role"))
                        && PasswordHashing.SCHEME.equals(rs.getString("password_scheme"));
            }
        }
    }
}
