package com.homestay.controller;

import com.homestay.api.*;
import java.time.*;
import java.time.temporal.ChronoUnit;

/** Local wall times are always in Vietnam; half-open intervals allow adjacent stays. */
final class HourlyStay {
    record Range(LocalDateTime start, LocalDateTime end) {
        long hours() { return billedHours(start,end); }
    }
    static long billedHours(LocalDateTime start, LocalDateTime end) {
        long seconds=ChronoUnit.SECONDS.between(start,end);
        if(seconds<=0) throw new ApiProblem(400,"INVALID_DATES","Giờ trả phòng phải sau giờ nhận phòng!");
        return (seconds+3599)/3600;
    }
    static LocalDateTime now(Clock clock) { return LocalDateTime.now(clock.withZone(RoomSchedule.ZONE)); }
    static Range range(String start,String end,Clock clock) {
        try {
            if(start==null || end==null || !start.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}")
                || !end.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}")) throw new IllegalArgumentException();
            var a=LocalDateTime.parse(start);var b=LocalDateTime.parse(end);
            if(a.getYear()<1 || b.getYear()>9998) throw new IllegalArgumentException();
            var range=new Range(a,b);range.hours();
            if(a.isBefore(now(clock).truncatedTo(ChronoUnit.MINUTES)))
                throw new ApiProblem(400,"INVALID_DATES","Giờ nhận phòng không được trong quá khứ (giờ Việt Nam)!");
            return range;
        } catch(ApiProblem e) { throw e; }
        catch(RuntimeException e) { throw new ApiProblem(400,"INVALID_DATES","Chọn ngày và giờ nhận/trả hợp lệ!"); }
    }
}
