package com.homestay.controller;

import com.homestay.db.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class RoomExpansionSqlServerIT {
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void sql(String query)throws Exception{try(var c=DBConnection.getConnection();var s=c.createStatement()){s.execute(query);}}
    private static String value(String query)throws Exception{try(var c=DBConnection.getConnection();var s=c.createStatement();var r=s.executeQuery(query)){r.next();return r.getString(1);}}
    public static void main(String[] args)throws Exception {
        if(args.length>0){checks();return;}
        String db="HomestayExpansionTest_"+UUID.randomUUID().toString().replace("-","");
        try(var c=DBConnection.getConnection();var s=c.createStatement()) {
            try {
                s.execute("CREATE DATABASE ["+db+"]");s.execute("USE ["+db+"]");
                String schema=Files.readString(Path.of("../database/schema.sql"));
                for(String batch:schema.substring(schema.indexOf("CREATE TABLE Users")).split("(?im)^\\s*GO\\s*$"))if(!batch.isBlank())s.execute(batch);
                var child=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java.exe").toString(),"-cp",System.getProperty("java.class.path"),RoomExpansionSqlServerIT.class.getName(),"--child");
                child.environment().put("DB_NAME",db);child.inheritIO();var process=child.start();
                if(!process.waitFor(120,TimeUnit.SECONDS)){process.destroyForcibly();throw new AssertionError("Expansion tests timed out");}
                require(process.exitValue()==0,"Expansion integration failed");
            } finally {
                s.execute("USE master");s.execute("ALTER DATABASE ["+db+"] SET SINGLE_USER WITH ROLLBACK IMMEDIATE");s.execute("DROP DATABASE ["+db+"]");
                System.out.println("Disposed isolated expansion database.");
            }
        }
    }
    private static void checks()throws Exception {
        require(System.getenv("DB_NAME").matches("HomestayExpansionTest_[a-f0-9]{32}"),"Wrong test database");
        try(var c=DBConnection.getConnection()){HourlyPricingMigration.migrate(c);RoomPropertiesMigration.migrate(c);}
        sql("""
            INSERT Customers(full_name,cccd,phone) VALUES(N'Expansion test','001234567890','0901234567');
            INSERT Rooms(room_code,room_name,room_type,price,status,description) VALUES
            ('Room 401',N'Existing','VIP',777000,N'Trống',N'Existing description'),
            ('Room 408',N'Existing duplicate','Double',888000,N'Bảo trì',N'Keep this description');
            INSERT Bookings(customer_id,room_id,checkin_date,checkout_date,status,price_per_night) VALUES(1,1,'2030-01-01','2030-01-03','CheckedOut',777000);
            INSERT Payments(booking_id,room_total,service_total,grand_total,invoice_snapshot) VALUES(1,1554000,0,1554000,N'{"roomCode":"Room 401","roomTotal":1554000}');
            """);
        try(var c=DBConnection.getConnection()){RoomExpansionMigration.migrate(c);}
        require(value("SELECT COUNT(*) FROM Rooms WHERE property_id=2").equals("37"),"Property 2 count wrong");
        require(value("SELECT COUNT(*) FROM Rooms WHERE property_id=3").equals("37"),"Property 3 count wrong");
        require(value("SELECT COUNT(*) FROM Rooms WHERE property_id=1").equals("2"),"Existing property changed");
        require(value("SELECT price FROM Rooms WHERE room_code='Room 401'").startsWith("777000"),"Existing price changed");
        require(value("SELECT description FROM Rooms WHERE room_code='Room 408'").equals("Keep this description"),"Duplicate poster room overwritten");
        require(value("SELECT COUNT(*) FROM Bookings").equals("1"),"History changed");
        require(value("SELECT invoice_snapshot FROM Payments").equals("{\"roomCode\":\"Room 401\",\"roomTotal\":1554000}"),"Invoice changed");
        require(value("SELECT COUNT(*) FROM Rooms WHERE property_id IN(2,3) AND price=400000 AND weekend_price=450000 AND hourly_price=80000 AND hourly_package_2=250000 AND hourly_package_4=350000 AND hourly_package_8=550000 AND weekend_days='6,7'").equals("74"),"Poster pricing mismatch");
        var rooms=new RoomController();
        require(rooms.getAllRooms(null,null,null,2).size()==37 && rooms.getAllRooms(null,null,null,3).size()==37,"Room property filter failed");
        require(new PropertyController().list().size()==3,"Property directory missing");
        require(new PublicRoomController().list().size()==75,"Public catalog count wrong");
        var bookings=new BookingController();
        require(bookings.getBookings(null,2).isEmpty() && bookings.getBookings(null,1).size()==1,"Booking property filter failed");
        require(bookings.getPayments(null,2).isEmpty() && bookings.getPayments(null,1).size()==1,"Payment property filter failed");
        var stats=new ServiceStatController();
        require(((Number)stats.getOverview(2).get("totalRooms")).intValue()==37,"Overview property filter failed");
        int year=java.time.LocalDate.now().getYear();
        require(stats.getYearly(year,2).stream().allMatch(n->n.signum()==0),"Yearly property filter failed");
        require(stats.getRevenue(java.time.LocalDate.now().getMonthValue(),year,2).isEmpty(),"Revenue property filter failed");
        sql("UPDATE Rooms SET price=999000,description=N'Admin edited',property_id=3 WHERE room_code='Room 104'; UPDATE Properties SET address=N'Admin address' WHERE property_id=2;");
        try(var c=DBConnection.getConnection()){RoomExpansionMigration.migrate(c);}
        require(value("SELECT price FROM Rooms WHERE room_code='Room 104'").startsWith("999000"),"Restart overwrote price");
        require(value("SELECT property_id FROM Rooms WHERE room_code='Room 104'").equals("3"),"Restart moved room");
        require(value("SELECT address FROM Properties WHERE property_id=2").equals("Admin address"),"Restart overwrote property address");
        // Recreate an initial import with an existing conflicting new number; skip it without changes.
        sql("DELETE FROM RoomExpansionImports; DELETE FROM Rooms WHERE property_id IN(2,3) AND room_code<>'Room 104'; UPDATE Rooms SET property_id=1 WHERE room_code='Room 104';");
        try(var c=DBConnection.getConnection()){RoomExpansionMigration.migrate(c);}
        require(value("SELECT property_id FROM Rooms WHERE room_code='Room 104'").equals("1"),"Conflicting room reassigned");
        require(value("SELECT price FROM Rooms WHERE room_code='Room 104'").startsWith("999000"),"Conflicting room changed");
        require(value("SELECT COUNT(*) FROM Rooms WHERE property_id=2").equals("36"),"Duplicate number not skipped");
        System.out.println("PASS 37/37 import, duplicate skip, image prices, property room/booking/payment/report filters, preserved property 1/history/invoices and idempotent admin edits");
    }
}
