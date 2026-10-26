package com.homestay.api;

import java.sql.*;
import java.util.*;
import java.util.function.Supplier;
import org.slf4j.LoggerFactory;

public final class ApiErrors {
    public static final class Body extends LinkedHashMap<String,Object> {
        public final int status;
        Body(int status,String code,String message) {
            this.status=status;put("success",false);put("code",code);put("message",message);
        }
    }
    private ApiErrors() {}
    public static Body error(int status,String code,String message) { return new Body(status,code,message); }
    public static Body bad(String message) { return error(400,"INVALID_INPUT",message); }
    public static Body missing(String message) { return error(404,"NOT_FOUND",message); }
    public static Body conflict(String message) { return error(409,"BUSINESS_CONFLICT",message); }
    public static ApiProblem notFound(String message) { return new ApiProblem(404,"NOT_FOUND",message); }
    public static ApiProblem invalid(String message) { return new ApiProblem(400,"INVALID_INPUT",message); }
    public static Body from(ApiProblem error) { return error(error.status,error.code,error.getMessage()); }
    public static Map<String,Object> guard(Supplier<Map<String,Object>> work) {
        try { return work.get(); } catch(ApiProblem error) { return from(error); }
    }
    public static Body database(SQLException error) {
        if(error.getErrorCode()==2601 || error.getErrorCode()==2627) return error(409,"DUPLICATE","Dữ liệu bị trùng với bản ghi đã có!");
        if(error.getErrorCode()==547) return error(409,"DATA_CONFLICT","Dữ liệu đang liên kết hoặc không còn hợp lệ. Vui lòng tải lại!");
        if(error.getErrorCode()==2628 || error.getErrorCode()==8152) return bad("Dữ liệu vượt giới hạn độ dài cho phép!");
        LoggerFactory.getLogger(ApiErrors.class).error("Database request failed ({})",error.getClass().getSimpleName(),error);
        return error(503,"DATABASE_UNAVAILABLE","Không thể truy cập dữ liệu lúc này. Vui lòng thử lại!");
    }
    public static ApiProblem databaseFailure(SQLException error) {
        Body body=database(error);
        return new ApiProblem(body.status,(String)body.get("code"),(String)body.get("message"));
    }
    public static Body unexpected(Throwable error) {
        String reference=UUID.randomUUID().toString();
        LoggerFactory.getLogger(ApiErrors.class).error("Unexpected API error [{}]",reference,error);
        return error(500,"INTERNAL_ERROR","Có lỗi xử lý. Vui lòng thử lại hoặc liên hệ người quản lý! Mã: "+reference);
    }
}
