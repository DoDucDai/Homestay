package com.homestay.api;

// NOTE API: Ngoại lệ nghiệp vụ mang HTTP status, code và message để gửi lỗi nhất quán về frontend.
// Controller/lớp kiểm tra ném lỗi này; ApiExceptionHandler chuyển nó thành phản hồi HTTP.
public class ApiProblem extends RuntimeException {
    public final int status;
    public final String code;
    public ApiProblem(int status,String code,String message) { super(message);this.status=status;this.code=code; }
}
