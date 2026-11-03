package com.homestay.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homestay.api.*;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** A night belongs to its arrival date; departure date is excluded. */
final class NightlyPricing {
    private static final ObjectMapper JSON=new ObjectMapper();
    record Quote(long nights,long weekdayNights,long weekendNights,BigDecimal weekdayPrice,BigDecimal weekendPrice,BigDecimal total) {}
    static Map<String,Object> input(Object value) {
        if(value==null)return null;
        if(!(value instanceof Map<?,?> map) || map.size()!=2 || map.get("weekendPrice")==null || !map.containsKey("weekendDays"))throw new ApiProblem(400,"INVALID_INPUT","Nhập đủ giá cuối tuần và ngày áp dụng.");
        return validate(map.get("weekendPrice"),map.get("weekendDays"));
    }
    static Map<String,Object> validate(Object rate,Object days) {
        if(rate==null)return null;
        long amount;
        try{amount=new BigDecimal(String.valueOf(rate)).longValueExact();if(amount<=0 || amount>999999999999L)throw new ArithmeticException();}
        catch(RuntimeException e){throw new ApiProblem(400,"INVALID_INPUT","Giá cuối tuần phải là số nguyên đồng dương hợp lệ.");}
        if(!(days instanceof List<?> list) || list.isEmpty() || list.size()>7)throw new ApiProblem(400,"INVALID_INPUT","Chọn các đêm áp dụng giá cuối tuần.");
        SortedSet<Integer> values=new TreeSet<>();
        for(Object day:(List<?>)days) {
            try{int d=new BigDecimal(String.valueOf(day)).intValueExact();if(d<1 || d>7 || !values.add(d))throw new ArithmeticException();}
            catch(RuntimeException e){throw new ApiProblem(400,"INVALID_INPUT","Ngày cuối tuần phải từ 1 (thứ Hai) đến 7 (Chủ nhật), không trùng.");}
        }
        return Map.of("weekendPrice",amount,"weekendDays",List.copyOf(values));
    }
    static Map<String,Object> room(ResultSet rs) throws SQLException {
        BigDecimal rate=rs.getBigDecimal("weekend_price");
        if(rate==null)return null;
        try{return validate(rate,Arrays.stream(rs.getString("weekend_days").split(",")).map(Integer::valueOf).toList());}
        catch(RuntimeException e){throw new SQLException("Invalid weekend pricing configuration",e);}
    }
    static String days(Map<String,Object> config) {
        return config==null?null:((List<?>)config.get("weekendDays")).stream().map(Object::toString).collect(java.util.stream.Collectors.joining(","));
    }
    static String encode(Map<String,Object> config) throws SQLException {
        if(config==null)return null;
        try{return JSON.writeValueAsString(config);}catch(Exception e){throw new SQLException("Cannot capture nightly prices",e);}
    }
    static Map<String,Object> decode(String snapshot) throws SQLException {
        if(snapshot==null)return null;
        try{Map<String,Object> value=JSON.readValue(snapshot,new TypeReference<Map<String,Object>>(){});return validate(value.get("weekendPrice"),value.get("weekendDays"));}
        catch(Exception e){throw new SQLException("Invalid captured nightly prices",e);}
    }
    static Quote quote(BigDecimal base,Map<String,Object> config,LocalDate start,LocalDate end) throws SQLException {
        long nights=ChronoUnit.DAYS.between(start,end);
        if(nights<=0)throw new ApiProblem(400,"INVALID_DATES","Ngày trả phải sau ngày nhận.");
        base=BookingInvoiceSnapshot.money(base);
        BigDecimal weekend=base;long count=0;
        if(config!=null) {
            config=validate(config.get("weekendPrice"),config.get("weekendDays"));
            weekend=BigDecimal.valueOf((Long)config.get("weekendPrice"));
            List<?> days=(List<?>)config.get("weekendDays");
            count=(nights/7)*days.size();
            for(int i=0;i<nights%7;i++)if(days.contains(start.plusDays(i).getDayOfWeek().getValue()))count++;
        }
        return new Quote(nights,nights-count,count,base,weekend,BookingInvoiceSnapshot.money(base.multiply(BigDecimal.valueOf(nights-count)).add(weekend.multiply(BigDecimal.valueOf(count)))));
    }
}
