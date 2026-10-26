package com.homestay.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Map;

public final class SecurityJson {
    private static final ObjectMapper JSON=new ObjectMapper();
    private SecurityJson() {}
    public static void error(HttpServletResponse response,int status,String code,String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control","no-store");
        JSON.writeValue(response.getWriter(),Map.of("success",false,"code",code,"message",message));
    }
}
