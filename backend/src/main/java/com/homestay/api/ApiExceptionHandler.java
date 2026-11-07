package com.homestay.api;

import org.springframework.core.MethodParameter;
import org.springframework.http.*;
import org.springframework.http.converter.*;
import org.springframework.http.server.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import java.util.*;

// NOTE API: Bộ xử lý lỗi chung của Spring cho các controller, giúp lỗi luôn có JSON và mã HTTP phù hợp.
@RestControllerAdvice
public class ApiExceptionHandler implements ResponseBodyAdvice<Object> {
    // NOTE API: Áp dụng bước kiểm tra nội dung phản hồi cho mọi controller.
    @Override public boolean supports(MethodParameter method,Class<? extends HttpMessageConverter<?>> converter) { return true; }
    // NOTE API: Trước khi gửi JSON, lấy status từ ApiErrors.Body để gắn vào phản hồi HTTP.
    @Override public Object beforeBodyWrite(Object body,MethodParameter method,MediaType type,
            Class<? extends HttpMessageConverter<?>> converter,ServerHttpRequest request,ServerHttpResponse response) {
        if(body instanceof ApiErrors.Body error) response.setStatusCode(HttpStatus.valueOf(error.status));
        return body;
    }
    // NOTE API: Nhận lỗi nghiệp vụ do code ném ra và giữ nguyên status/code/message đã định nghĩa.
    @ExceptionHandler(ApiProblem.class)
    public ResponseEntity<Map<String,Object>> problem(ApiProblem error) {
        return ResponseEntity.status(error.status).body(ApiErrors.from(error));
    }
    // NOTE API: JSON sai, thiếu tham số hoặc sai kiểu dữ liệu được quy về HTTP 400.
    @ExceptionHandler({HttpMessageNotReadableException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
        org.springframework.web.bind.MissingServletRequestParameterException.class,org.springframework.web.bind.MethodArgumentNotValidException.class})
    public ResponseEntity<Map<String,Object>> invalid(Exception error) {
        return ResponseEntity.badRequest().body(ApiErrors.bad("Yêu cầu không hợp lệ. Kiểm tra JSON, tham số và kiểu dữ liệu!"));
    }
    // NOTE API: Dùng sai GET/POST/PUT/DELETE đối với đường dẫn sẽ nhận HTTP 405.
    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String,Object>> method(Exception error) {
        return ResponseEntity.status(405).body(ApiErrors.error(405,"METHOD_NOT_ALLOWED","Phương thức không được hỗ trợ!"));
    }
    // NOTE API: Gửi kiểu nội dung không được hỗ trợ, chẳng hạn thiếu định dạng JSON phù hợp, sẽ nhận HTTP 415.
    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String,Object>> media(Exception error) {
        return ResponseEntity.status(415).body(ApiErrors.error(415,"UNSUPPORTED_MEDIA_TYPE","Nội dung yêu cầu cần có định dạng JSON!"));
    }
    // NOTE API: Không tìm thấy tài nguyên hoặc đường dẫn tương ứng thì trả JSON lỗi HTTP 404.
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<Map<String,Object>> missing(Exception error) {
        return ResponseEntity.status(404).body(ApiErrors.missing("Không tìm thấy tài nguyên!"));
    }
    // NOTE API: Bắt lỗi còn lại, ghi log kỹ thuật và trả HTTP 500 với thông báo đã ẩn chi tiết nội bộ.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String,Object>> unexpected(Exception error) {
        return ResponseEntity.internalServerError().body(ApiErrors.unexpected(error));
    }
}
