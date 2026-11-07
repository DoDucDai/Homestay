package com.homestay.api;

import java.math.*;
import java.util.*;

// NOTE API: Các hàm kiểm tra đầu vào dùng chung; dữ liệu sai sẽ ném ApiProblem với HTTP 400.
public final class Input {
    private Input() {}
    // NOTE API: Đọc trường chuỗi từ JSON, bỏ khoảng trắng hai đầu, kiểm tra bắt buộc và độ dài; dùng fallback nếu được phép thiếu.
    public static String text(Map<String,Object> body,String key,int max,boolean required,String fallback) {
        if(body==null) throw ApiErrors.invalid("Thiếu nội dung yêu cầu!");
        Object raw=body.get(key);
        if(raw==null) {
            if(required) throw ApiErrors.invalid("Thiếu trường "+key+"!");
            return fallback;
        }
        if(!(raw instanceof String)) throw ApiErrors.invalid("Trường "+key+" phải là chuỗi!");
        String value=((String)raw).trim();
        if(value.length()>max || required && value.isEmpty()) throw ApiErrors.invalid("Trường "+key+" trống hoặc vượt "+max+" ký tự!");
        return value;
    }
    // NOTE API: Đọc số nguyên trong khoảng min/max; từ chối phần thập phân hoặc giá trị vượt kiểu int.
    public static int integer(Map<String,Object> body,String key,int min,int max) {
        if(body==null || body.get(key)==null || !(body.get(key) instanceof Number || body.get(key) instanceof String)) throw ApiErrors.invalid("Trường "+key+" phải là số nguyên!");
        try {
            int value=new BigDecimal(body.get(key).toString()).intValueExact();
            if(value<min || value>max) throw new ArithmeticException();return value;
        } catch(RuntimeException error) { throw ApiErrors.invalid("Trường "+key+" phải là số nguyên từ "+min+" đến "+max+"!"); }
    }
    // NOTE API: Đọc tiền nguyên trong giới hạn lưu trữ; positive quyết định có bắt buộc lớn hơn 0 hay không.
    public static long money(Map<String,Object> body,String key,boolean positive) {
        if(body==null || body.get(key)==null || !(body.get(key) instanceof Number || body.get(key) instanceof String)) throw ApiErrors.invalid("Thiếu giá tiền hợp lệ!");
        try {
            long value=new BigDecimal(body.get(key).toString()).longValueExact();
            if(value<(positive?1:0) || value>999999999999L) throw new ArithmeticException();return value;
        } catch(RuntimeException error) { throw ApiErrors.invalid("Giá tiền phải là số nguyên trong giới hạn cho phép!"); }
    }
    // NOTE API: Kiểm tra ID trên đường dẫn hoặc query phải là số nguyên dương.
    public static int id(int value) { if(value<=0) throw ApiErrors.invalid("ID phải là số nguyên dương!");return value; }
    // NOTE API: Chỉ chấp nhận giá trị có trong danh sách cho phép, ví dụ loại phòng hoặc trạng thái.
    public static String oneOf(String value,Set<String> allowed,String label) {
        if(!allowed.contains(value)) throw ApiErrors.invalid(label+" không hợp lệ!");return value;
    }
    // NOTE API: Kiểm tra số điện thoại gồm đúng 10 chữ số và bắt đầu bằng 0.
    public static String phone(String value) { if(!value.matches("0[0-9]{9}")) throw ApiErrors.invalid("Số điện thoại phải có 10 chữ số, bắt đầu bằng 0!");return value; }
    // NOTE API: Cho phép email trống nếu trường không bắt buộc; nếu đã nhập thì kiểm tra định dạng cơ bản.
    public static String email(String value) {
        if(!value.isEmpty() && !value.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw ApiErrors.invalid("Email không hợp lệ!");return value;
    }
    // NOTE API: Trả true/false cho CCCD 12 chữ số hoặc hộ chiếu 6–20 ký tự chữ/số; chưa ném lỗi.
    public static boolean documentValid(String type,String number) {
        return "CCCD".equals(type) ? number.matches("[0-9]{12}") : "PASSPORT".equals(type) && number.matches("[A-Za-z0-9]{6,20}");
    }
    // NOTE API: Dùng quy tắc giấy tờ bên trên và ném lỗi 400 nếu không đạt.
    public static void document(String type,String number) {
        if(!documentValid(type,number)) throw ApiErrors.invalid("CCCD cần 12 chữ số; hộ chiếu cần 6–20 chữ cái hoặc chữ số!");
    }
}
