package com.homestay.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Map;

// NOTE BACKEND: Tạo phản hồi lỗi JSON ở tầng bộ lọc bảo mật, nơi lỗi thường xảy ra trước khi controller được gọi.
public final class SecurityJson {
    private static final ObjectMapper JSON=new ObjectMapper();
    private SecurityJson() {}
    // NOTE BACKEND: Đặt HTTP status, kiểu JSON UTF-8 và no-store rồi ghi success=false/code/message ra phản hồi.
    public static void error(HttpServletResponse response,int status,String code,String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control","no-store");
        JSON.writeValue(response.getWriter(),Map.of("success",false,"code",code,"message",message));
    }
}
