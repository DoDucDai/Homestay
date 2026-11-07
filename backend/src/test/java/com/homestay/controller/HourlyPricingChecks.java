package com.homestay.controller;

import java.math.BigDecimal;
import java.util.Map;

// NOTE KIỂM THỬ: Kiểm tra nhanh tính tiền gói 2/4/8 giờ, giá giờ cũ, lưu/đọc JSON giá và các trường hợp vượt giới hạn.
// File này thuộc src/test, không phải endpoint và không nằm trong mã nghiệp vụ chạy phục vụ người dùng.
public class HourlyPricingChecks {
    // NOTE KIỂM THỬ: Điểm chạy nhóm kiểm tra; dùng AssertionError/kết thúc khác 0 để báo điều kiện không đạt.
    public static void main(String[] args) throws Exception {
        var prices=Map.of("twoHours",250000L,"fourHours",350000L,"eightHours",550000L);
        long[] expected={250000,250000,330000,350000,430000,510000,550000,550000,630000};
        for(int i=0;i<expected.length;i++)if(HourlyPricing.quote(BigDecimal.valueOf(80000),prices,i+1).total().longValueExact()!=expected[i])throw new AssertionError("Package hour "+(i+1));
        if(HourlyPricing.quote(BigDecimal.valueOf(70000),null,3).total().longValueExact()!=210000)throw new AssertionError("Legacy pricing changed");
        if(!prices.equals(HourlyPricing.decode(HourlyPricing.encode(prices))))throw new AssertionError("Snapshot round trip");
        try{HourlyPricing.quote(BigDecimal.valueOf(999999999999L),null,2);throw new AssertionError("Overflow accepted");}catch(com.homestay.api.ApiProblem expectedError){}
        try{HourlyPricing.validate(Map.of("twoHours",1));throw new AssertionError("Incomplete accepted");}catch(com.homestay.api.ApiProblem expectedError){}
        System.out.println("PASS package boundaries 1–9h, legacy rates, snapshot roundtrip, invalid and excessive totals");
    }
}
