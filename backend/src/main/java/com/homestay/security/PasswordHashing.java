package com.homestay.security;

import org.springframework.security.crypto.password.*;
import java.util.Map;

// NOTE BACKEND: Cung cấp bộ băm mật khẩu PBKDF2 thống nhất; chuỗi đã băm có tiền tố chỉ rõ phiên bản thuật toán.
public final class PasswordHashing {
    public static final String SCHEME = "pbkdf2-v58";
    private PasswordHashing() {}
    // NOTE BACKEND: Dùng DelegatingPasswordEncoder với cấu hình PBKDF2 của Spring Security 5.8 để băm và đối chiếu mật khẩu.
    public static PasswordEncoder encoder() {
        return new DelegatingPasswordEncoder(SCHEME,
                Map.of(SCHEME, Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8()));
    }
    // NOTE BACKEND: Chỉ kiểm tra hình dạng chuỗi băm được hỗ trợ; muốn xác nhận mật khẩu phải dùng encoder.matches.
    public static boolean validHash(String value) {
        return value != null && value.matches("\\{pbkdf2-v58}[0-9a-f]{96}");
    }
}
