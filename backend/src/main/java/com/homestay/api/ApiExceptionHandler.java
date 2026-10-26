package com.homestay.api;

import org.springframework.core.MethodParameter;
import org.springframework.http.*;
import org.springframework.http.converter.*;
import org.springframework.http.server.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import java.util.*;

@RestControllerAdvice
public class ApiExceptionHandler implements ResponseBodyAdvice<Object> {
    @Override public boolean supports(MethodParameter method,Class<? extends HttpMessageConverter<?>> converter) { return true; }
    @Override public Object beforeBodyWrite(Object body,MethodParameter method,MediaType type,
            Class<? extends HttpMessageConverter<?>> converter,ServerHttpRequest request,ServerHttpResponse response) {
        if(body instanceof ApiErrors.Body error) response.setStatusCode(HttpStatus.valueOf(error.status));
        return body;
    }
    @ExceptionHandler(ApiProblem.class)
    public ResponseEntity<Map<String,Object>> problem(ApiProblem error) {
        return ResponseEntity.status(error.status).body(ApiErrors.from(error));
    }
    @ExceptionHandler({HttpMessageNotReadableException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
        org.springframework.web.bind.MissingServletRequestParameterException.class,org.springframework.web.bind.MethodArgumentNotValidException.class})
    public ResponseEntity<Map<String,Object>> invalid(Exception error) {
        return ResponseEntity.badRequest().body(ApiErrors.bad("Yêu cầu không hợp lệ. Kiểm tra JSON, tham số và kiểu dữ liệu!"));
    }
    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String,Object>> method(Exception error) {
        return ResponseEntity.status(405).body(ApiErrors.error(405,"METHOD_NOT_ALLOWED","Phương thức không được hỗ trợ!"));
    }
    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String,Object>> media(Exception error) {
        return ResponseEntity.status(415).body(ApiErrors.error(415,"UNSUPPORTED_MEDIA_TYPE","Nội dung yêu cầu cần có định dạng JSON!"));
    }
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<Map<String,Object>> missing(Exception error) {
        return ResponseEntity.status(404).body(ApiErrors.missing("Không tìm thấy tài nguyên!"));
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String,Object>> unexpected(Exception error) {
        return ResponseEntity.internalServerError().body(ApiErrors.unexpected(error));
    }
}
