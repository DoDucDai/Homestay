package com.homestay.controller;

import com.homestay.db.DBConnection;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** End-to-end JDBC/controller checks; all business writes use a UUID disposable DB. */
public class HourlyBookingSqlServerIT {
    static final Clock CLOCK=Clock.fixed(Instant.parse("2031-01-15T03:00:00Z"),RoomSchedule.ZONE);
    static final BookingController app=new BookingController(CLOCK);
    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static boolean ok(Map<String,Object> r){return Boolean.TRUE.equals(r.get("success"));}
    static void sql(String sql)throws Exception{try(var c=DBConnection.getConnection();var s=c.createStatement()){s.execute(sql);}}
    static Map<String,Object> book(int room,String start,String end){return app.createBooking(Map.of("billingUnit","Hour","customerId",1,"roomId",room,"checkinAt",start,"checkoutAt",end));}
    static int id(Map<String,Object> r){require(ok(r),r.toString());return ((Number)r.get("bookingId")).intValue();}
    static String at(String time){return "2031-01-15T"+time;}
    public static void main(String[] args)throws Exception {
        if(args.length>0 && args[0].equals("--child")){checks();return;}
        String db="HomestayHourlyTest_"+UUID.randomUUID().toString().replace("-","");
        try(var c=DBConnection.getConnection();var s=c.createStatement()) {
            try {
                s.execute("CREATE DATABASE ["+db+"]");s.execute("USE ["+db+"]");
                String schema=Files.readString(Path.of("../database/schema.sql"));
                for(String batch:schema.substring(schema.indexOf("CREATE TABLE Users")).split("(?im)^\\s*GO\\s*$"))if(!batch.isBlank())s.execute(batch);
                // Re-running migration must be harmless.
                for(String batch:Files.readString(Path.of("../database/migrations/006_hourly_bookings.sql")).split("(?im)^\\s*GO\\s*$"))if(!batch.isBlank())s.execute(batch);
                var child=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java.exe").toString(),"-cp",System.getProperty("java.class.path"),HourlyBookingSqlServerIT.class.getName(),"--child");
                child.environment().put("DB_NAME",db);child.inheritIO();var process=child.start();
                if(!process.waitFor(120,TimeUnit.SECONDS)){process.destroyForcibly();throw new AssertionError("Hourly tests timed out");}
                require(process.exitValue()==0,"Hourly integration failed");
            } finally {
                s.execute("USE master");s.execute("ALTER DATABASE ["+db+"] SET SINGLE_USER WITH ROLLBACK IMMEDIATE");s.execute("DROP DATABASE ["+db+"]");
                System.out.println("Disposed isolated hourly database.");
            }
        }
    }
    static void checks()throws Exception {
        require(System.getenv("DB_NAME").matches("HomestayHourlyTest_[a-f0-9]{32}"),"Wrong test database");
        sql("""
            INSERT Customers(full_name,cccd,phone) VALUES(N'Hourly guest','001234567890','0901234567');
            INSERT Rooms(room_code,room_name,room_type,price,hourly_price,status) VALUES
                ('H1','Hourly one','Single',500000,70000,N'Trống'),
                ('H2','Hourly two','Double',800000,90000,N'Trống'),
                ('H3','Unset rate','VIP',1200000,NULL,N'Trống'),
                ('H4','Maintenance','Single',500000,70000,N'Bảo trì');
            """);
        require(HourlyStay.billedHours(LocalDateTime.parse(at("10:00")),LocalDateTime.parse(at("11:00")))==1,"Exact hour rounded wrong");
        require(HourlyStay.billedHours(LocalDateTime.parse(at("10:00")),LocalDateTime.parse(at("11:01")))==2,"Partial hour not rounded up");
        require(!ok(book(1,at("09:59"),at("11:00"))) && !ok(book(1,at("10:00"),at("10:00"))),"Invalid range accepted");
        require(!ok(book(3,at("10:00"),at("11:00"))) && !ok(book(4,at("10:00"),at("11:00"))),"Missing rate or maintenance accepted");
        var rooms=new RoomController(CLOCK);
        require(ok(rooms.updateRoom(3,Map.of("roomCode","H3","roomName","Unset rate","roomType","VIP","price",1200000,"hourlyPrice",80000))),"Hourly rate edit failed");
        require(((Number)rooms.getRoomById(3).get("hourlyPrice")).longValue()==80000,"Hourly rate not persisted");
        require(ok(rooms.updateRoom(3,Map.of("roomCode","H3","roomName","Unset rate","roomType","VIP","price",1200000))),"Legacy room edit failed");
        require(((Number)rooms.getRoomById(3).get("hourlyPrice")).longValue()==80000,"Omitted hourly rate was erased by old client");
        var clear=new HashMap<String,Object>(Map.of("roomName","Unset rate","roomType","VIP","price",1200000));clear.put("hourlyPrice",null);
        require(ok(rooms.updateRoom(3,clear)) && rooms.getRoomById(3).get("hourlyPrice")==null,"Explicit hourly rate removal failed");
        System.out.println("PASS HOUR-01 rounding, invalid dates, missing/maintenance rate and rate edit/preserve/clear");
        int first=id(book(1,at("10:00"),at("11:01")));
        require(!ok(book(1,at("11:00"),at("12:00"))),"Overlap accepted");
        int next=id(book(1,at("11:01"),at("12:00")));
        require(!ok(app.checkIn(next)),"Early checkin accepted");
        require(ok(app.checkIn(first)),"Current hourly checkin failed");
        require("Đang sử dụng".equals(new RoomController(CLOCK).getRoomById(1).get("status")),"Card occupancy incorrect");
        System.out.println("PASS HOUR-02 overlaps rejected, adjacent intervals accepted, timed checkin and occupied state");
        var preview=app.previewCheckout(first);require(ok(preview),preview.toString());
        require(((Number)preview.get("billedHours")).longValue()==2 && ((Number)preview.get("roomTotal")).longValue()==140000,"Invoice not rounded from captured rate");
        sql("UPDATE Rooms SET hourly_price=999999 WHERE room_id=1");
        require(((Number)app.previewCheckout(first).get("roomTotal")).longValue()==140000,"Catalog edit changed booked price");
        var paid=app.checkOut(first,Map.of("paymentMethod","Chuyển khoản"));require(ok(paid),paid.toString());
        require(!ok(app.checkOut(first,Map.of())),"Duplicate payment accepted");
        var snapshot=app.getPayment(((Number)paid.get("paymentId")).intValue());require(ok(snapshot),snapshot.toString());
        require(((Map<?,?>)snapshot.get("invoice")).get("billingUnit").equals("Hour"),"Hourly snapshot lost unit");
        require("Trống".equals(new RoomController(CLOCK).getRoomById(1).get("status")),"Room did not become available before next interval");
        System.out.println("PASS HOUR-03 captured hourly price, rounded invoice, immutable saved snapshot, one payment and room release");
        int overnight=id(book(2,"2031-01-16T23:30","2031-01-17T00:31"));
        require(!ok(app.createBooking(Map.of("customerId",1,"roomId",2,"checkinDate","2031-01-16","checkoutDate","2031-01-17"))),"Night booking overlapped hourly booking");
        require(ok(app.cancelBooking(overnight)),"Hourly cancel failed");
        int nightly=id(app.createBooking(Map.of("customerId",1,"roomId",2,"checkinDate","2031-01-16","checkoutDate","2031-01-17")));
        require(!ok(book(2,"2031-01-16T23:00","2031-01-17T01:00")),"Hourly booking overlapped nightly booking");
        require(app.getBookings(null).stream().anyMatch(b->b.get("bookingId").equals(nightly) && b.get("billingUnit").equals("Night")),"Legacy nightly mode lost");
        System.out.println("PASS HOUR-04 midnight ranges, cross-mode overlap, cancellation and legacy nightly compatibility");
        var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        try {
            Callable<Map<String,Object>> job=()->{gate.await();return book(1,"2031-01-18T10:00","2031-01-18T12:00");};
            var a=pool.submit(job);var b=pool.submit(job);gate.countDown();
            require(ok(a.get())!=ok(b.get()),"Concurrent overlapping bookings both accepted or both failed");
        } finally {pool.shutdownNow();}
        System.out.println("PASS HOUR-05 concurrent overlapping bookings serialized by room lock");
    }
}
