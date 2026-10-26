package com.homestay.security;

import org.springframework.security.crypto.password.*;
import java.util.Map;

public final class PasswordHashing {
    public static final String SCHEME = "pbkdf2-v58";
    private PasswordHashing() {}
    public static PasswordEncoder encoder() {
        return new DelegatingPasswordEncoder(SCHEME,
                Map.of(SCHEME, Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8()));
    }
    public static boolean validHash(String value) {
        return value != null && value.matches("\\{pbkdf2-v58}[0-9a-f]{96}");
    }
}
