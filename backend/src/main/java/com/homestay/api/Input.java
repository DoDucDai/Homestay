package com.homestay.api;

import java.math.*;
import java.util.*;

public final class Input {
    private Input() {}
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
    public static int integer(Map<String,Object> body,String key,int min,int max) {
        if(body==null || body.get(key)==null || !(body.get(key) instanceof Number || body.get(key) instanceof String)) throw ApiErrors.invalid("Trường "+key+" phải là số nguyên!");
        try {
            int value=new BigDecimal(body.get(key).toString()).intValueExact();
            if(value<min || value>max) throw new ArithmeticException();return value;
        } catch(RuntimeException error) { throw ApiErrors.invalid("Trường "+key+" phải là số nguyên từ "+min+" đến "+max+"!"); }
    }
    public static long money(Map<String,Object> body,String key,boolean positive) {
        if(body==null || body.get(key)==null || !(body.get(key) instanceof Number || body.get(key) instanceof String)) throw ApiErrors.invalid("Thiếu giá tiền hợp lệ!");
        try {
            long value=new BigDecimal(body.get(key).toString()).longValueExact();
            if(value<(positive?1:0) || value>999999999999L) throw new ArithmeticException();return value;
        } catch(RuntimeException error) { throw ApiErrors.invalid("Giá tiền phải là số nguyên trong giới hạn cho phép!"); }
    }
    public static int id(int value) { if(value<=0) throw ApiErrors.invalid("ID phải là số nguyên dương!");return value; }
    public static String oneOf(String value,Set<String> allowed,String label) {
        if(!allowed.contains(value)) throw ApiErrors.invalid(label+" không hợp lệ!");return value;
    }
    public static String phone(String value) { if(!value.matches("0[0-9]{9}")) throw ApiErrors.invalid("Số điện thoại phải có 10 chữ số, bắt đầu bằng 0!");return value; }
    public static String email(String value) {
        if(!value.isEmpty() && !value.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw ApiErrors.invalid("Email không hợp lệ!");return value;
    }
    public static boolean documentValid(String type,String number) {
        return "CCCD".equals(type) ? number.matches("[0-9]{12}") : "PASSPORT".equals(type) && number.matches("[A-Za-z0-9]{6,20}");
    }
    public static void document(String type,String number) {
        if(!documentValid(type,number)) throw ApiErrors.invalid("CCCD cần 12 chữ số; hộ chiếu cần 6–20 chữ cái hoặc chữ số!");
    }
}
