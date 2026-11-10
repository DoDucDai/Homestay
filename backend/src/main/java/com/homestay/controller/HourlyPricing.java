package com.homestay.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homestay.api.*;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

/** Package prices are captured at booking time, independently of later room edits. */
// NOTE BACKEND: Kiểm tra bảng giá 2/4/8 giờ, lưu bảng giá lúc đặt và chọn phương án tính tiền phù hợp cho thời lượng ở.
final class HourlyPricing {
    private static final ObjectMapper JSON = new ObjectMapper();
    static final List<String> KEYS = List.of("twoHours","fourHours","eightHours");
    record Quote(long packageHours, BigDecimal packagePrice, long extraHours, BigDecimal extraPricePerHour, BigDecimal total) {}

    // NOTE BACKEND: Đọc các cột giá gói từ dòng phòng; không có giá gói thì trả null để dùng giá mỗi giờ.
    static Map<String,Long> packages(ResultSet rs) throws SQLException {
        Map<String,Long> value=new LinkedHashMap<>();
        for(String key:KEYS) {
            BigDecimal amount=rs.getBigDecimal(column(key));
            if(amount!=null)value.put(key,BookingInvoiceSnapshot.money(amount).longValueExact());
        }
        if(value.isEmpty())return null;
        return validate(value);
    }
    // NOTE BACKEND: Đổi khóa twoHours/fourHours/eightHours sang tên cột SQL cố định do chương trình quy định.
    static String column(String key) {return switch(key) {
        case "twoHours" -> "hourly_package_2";case "fourHours" -> "hourly_package_4";case "eightHours" -> "hourly_package_8";
        default -> throw new IllegalArgumentException();
    };}
    // NOTE BACKEND: Yêu cầu đủ ba giá nguyên dương theo thứ tự không giảm; chấp nhận null khi phòng không dùng bảng giá gói.
    static Map<String,Long> validate(Object input) {
        if(input==null)return null;
        if(!(input instanceof Map<?,?> map) || map.size()!=3 || !map.keySet().containsAll(KEYS))
            throw new ApiProblem(400,"INVALID_INPUT","Nhập đủ giá gói 2, 4 và 8 giờ hoặc bỏ trống cả ba.");
        Map<String,Long> result=new LinkedHashMap<>();
        for(String key:KEYS) {
            try {
                long value=new BigDecimal(String.valueOf(map.get(key))).longValueExact();
                if(value<=0 || value>999999999999L)throw new ArithmeticException();
                result.put(key,value);
            } catch(RuntimeException e) {throw new ApiProblem(400,"INVALID_INPUT","Giá gói phải là số nguyên đồng dương hợp lệ.");}
        }
        if(result.get("twoHours")>result.get("fourHours") || result.get("fourHours")>result.get("eightHours"))
            throw new ApiProblem(400,"INVALID_INPUT","Giá gói phải tăng hoặc bằng nhau theo số giờ.");
        return result;
    }
    // NOTE BACKEND: Chuyển bảng giá gói thành JSON để booking giữ được mức giá tại thời điểm đặt.
    static String encode(Map<String,Long> packages) throws SQLException {
        if(packages==null)return null;
        try{return JSON.writeValueAsString(packages);}catch(Exception e){throw new SQLException("Cannot capture hourly package prices",e);}
    }
    // NOTE BACKEND: Đọc và kiểm tra lại JSON bảng giá đã lưu; dữ liệu hỏng báo lỗi thay vì tự lấy giá hiện tại.
    static Map<String,Long> decode(String snapshot) throws SQLException {
        if(snapshot==null)return null;
        try{return validate(JSON.readValue(snapshot,new TypeReference<Map<String,Long>>(){}));}
        catch(Exception e){throw new SQLException("Invalid captured hourly package prices",e);}
    }
    // NOTE BACKEND: So sánh mỗi gói cộng giờ vượt gói và chọn tổng thấp nhất; nếu không có gói thì lấy số giờ nhân đơn giá giờ.
    static Quote quote(BigDecimal extraRate,Map<String,Long> packages,long hours) throws SQLException {
        if(hours<=0 || extraRate==null || extraRate.signum()<=0)throw new ApiProblem(400,"INVALID_INPUT","Giá giờ hoặc số giờ không hợp lệ.");
        extraRate=BookingInvoiceSnapshot.money(extraRate);
        if(packages==null)return new Quote(0,BigDecimal.ZERO,hours,extraRate,BookingInvoiceSnapshot.money(extraRate.multiply(BigDecimal.valueOf(hours))));
        packages=validate(packages);
        long firstExtra=Math.max(0,hours-2);
        BigDecimal firstBase=BigDecimal.valueOf(packages.get(KEYS.get(0)));
        Quote best=new Quote(2,firstBase,firstExtra,extraRate,firstBase.add(extraRate.multiply(BigDecimal.valueOf(firstExtra))));
        for(int i=1;i<3;i++) {
            long included=new long[]{2,4,8}[i],extra=Math.max(0,hours-included);
            BigDecimal base=BigDecimal.valueOf(packages.get(KEYS.get(i)));
            BigDecimal total=base.add(extraRate.multiply(BigDecimal.valueOf(extra)));
            if(total.compareTo(best.total())<0)best=new Quote(included,base,extra,extraRate,total);
        }
        BookingInvoiceSnapshot.money(best.total());return best;
    }
}
