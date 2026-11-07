package com.homestay.controller;

import com.homestay.db.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

// NOTE KIỂM THỬ: Kiểm tra chuẩn hóa/gộp mã phòng, giữ liên kết booking và xử lý ID cũ qua bảng ánh xạ.
// File này thuộc src/test, không phải endpoint và không nằm trong mã nghiệp vụ chạy phục vụ người dùng.
public final class RoomCatalogSqlServerIT {
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void sql(String query)throws Exception{try(var c=DBConnection.getConnection();var s=c.createStatement()){s.execute(query);}}
    private static String value(String query)throws Exception{try(var c=DBConnection.getConnection();var s=c.createStatement();var r=s.executeQuery(query)){r.next();return r.getString(1);}}
    // NOTE KIỂM THỬ: Điểm chạy nhóm kiểm tra; dùng AssertionError/kết thúc khác 0 để báo điều kiện không đạt.
    public static void main(String[] args)throws Exception {
        if(args.length>0){checks();return;}
        String db="HomestayCatalogTest_"+UUID.randomUUID().toString().replace("-","");
        try(var c=DBConnection.getConnection();var s=c.createStatement()) {
            try {
                s.execute("CREATE DATABASE ["+db+"]");s.execute("USE ["+db+"]");
                String schema=Files.readString(Path.of("../database/schema.sql"));
                for(String batch:schema.substring(schema.indexOf("CREATE TABLE Users")).split("(?im)^\\s*GO\\s*$"))if(!batch.isBlank())s.execute(batch);
                var child=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java.exe").toString(),"-cp",System.getProperty("java.class.path"),RoomCatalogSqlServerIT.class.getName(),"--child");
                child.environment().put("DB_NAME",db);child.inheritIO();var process=child.start();
                if(!process.waitFor(120,TimeUnit.SECONDS)){process.destroyForcibly();throw new AssertionError("Catalog tests timed out");}
                require(process.exitValue()==0,"Catalog integration failed");
            } finally {
                s.execute("USE master");s.execute("ALTER DATABASE ["+db+"] SET SINGLE_USER WITH ROLLBACK IMMEDIATE");s.execute("DROP DATABASE ["+db+"]");
                System.out.println("Disposed isolated catalog test database.");
            }
        }
    }
    // NOTE KIỂM THỬ: Tạo tình huống, gọi code cần kiểm tra rồi đối chiếu kết quả/trạng thái dữ liệu với giá trị mong đợi.
    private static void checks()throws Exception {
        require(System.getenv("DB_NAME").matches("HomestayCatalogTest_[a-f0-9]{32}"),"Wrong test database");
        try(var c=DBConnection.getConnection()){require(RoomCatalogMigration.resolveId(c,1)==1,"Pre-migration ID lookup failed");HourlyPricingMigration.migrate(c);}
        sql("""
            INSERT Customers(full_name,cccd,phone) VALUES(N'Catalog test','001234567890','0901234567');
            INSERT Rooms(room_code,room_name,room_type,price,status,description) VALUES
            ('P401',N'Legacy','Single',900000,N'Trống',N'Old'),
            ('DEMO-P401',N'Phòng demo 401','Single',400000,N'Trống',N'Dữ liệu giả lập'),
            ('DEMO-P408',N'Phòng demo 408','Family',1400000,N'Trống',N'Dữ liệu giả lập'),
            ('CUSTOM',N'Custom room','Single',700000,N'Bảo trì',N'Custom description');
            INSERT Bookings(customer_id,room_id,checkin_date,checkout_date,status,price_per_night) VALUES
            (1,1,'2030-01-01','2030-01-03','CheckedOut',900000),
            (1,1,'2031-01-01','2031-01-03','Confirmed',900000),
            (1,2,'2031-01-02','2031-01-04','Confirmed',400000);
            INSERT Payments(booking_id,room_total,service_total,grand_total,invoice_snapshot) VALUES(1,1800000,0,1800000,N'{"roomCode":"P401","roomTotal":1800000}');
            """);
        try(var c=DBConnection.getConnection()) {
            try{RoomCatalogMigration.migrate(c);throw new AssertionError("Overlapping reservations merged");}
            catch(SQLException expected){require(expected.getMessage().contains("overlap"),expected.getMessage());}
        }
        require(value("SELECT COUNT(*) FROM Rooms").equals("4"),"Failed merge changed room rows");
        require(value("SELECT room_id FROM Bookings WHERE booking_id=1").equals("1"),"Failed merge changed booking");
        sql("UPDATE Bookings SET checkin_date='2031-01-03',checkout_date='2031-01-04' WHERE booking_id=3");
        try(var c=DBConnection.getConnection()){RoomCatalogMigration.migrate(c);}
        require(value("SELECT COUNT(*) FROM Rooms").equals("3"),"Duplicate room not merged");
        require(value("SELECT COUNT(*) FROM Bookings WHERE room_id=2").equals("3"),"Booking history lost");
        require(value("SELECT price_per_night FROM Bookings WHERE booking_id=1").startsWith("900000"),"Booked price changed");
        require(value("SELECT invoice_snapshot FROM Payments WHERE booking_id=1").equals("{\"roomCode\":\"P401\",\"roomTotal\":1800000}"),"Paid invoice changed");
        require(value("SELECT description FROM Rooms WHERE room_code='Room 401'").contains("bồn tắm"),"Poster details missing");
        require(value("SELECT room_name FROM Rooms WHERE room_code='Room 408'").equals("Room 408"),"Unsupported room invented");
        require(value("SELECT description FROM Rooms WHERE room_code='CUSTOM'").equals("Custom description"),"Unrelated room overwritten");
        require(value("SELECT COUNT(*) FROM RoomCatalogMigrations WHERE ISJSON(before_json)=1").equals("1"),"Original data not archived");
        try(var c=DBConnection.getConnection()){require(RoomCatalogMigration.resolveId(c,1)==2,"Legacy deep link lost");}
        require(new PublicRoomController().detail(1).get("roomCode").equals("Room 401"),"Public legacy URL lost");
        require(new RoomController().getRoomById(1).get("roomId").equals(2),"Operational legacy URL lost");
        sql("UPDATE Rooms SET room_name=N'Admin edited',description=N'Admin description',price=777777 WHERE room_code='Room 401'");
        try(var c=DBConnection.getConnection()){RoomCatalogMigration.migrate(c);}
        require(value("SELECT room_name FROM Rooms WHERE room_code='Room 401'").equals("Admin edited"),"Restart overwrote admin edit");
        require(value("SELECT price FROM Rooms WHERE room_code='Room 401'").startsWith("777777"),"Restart overwrote admin price");
        sql("ALTER TABLE Rooms DROP CONSTRAINT FK_Rooms_Property; ALTER TABLE Rooms DROP CONSTRAINT DF_Rooms_Property; ALTER TABLE Rooms DROP COLUMN property_id; DROP TABLE Properties;");
        try(var c=DBConnection.getConnection()){RoomPropertiesMigration.migrate(c);}
        require(value("SELECT COUNT(*) FROM Rooms WHERE property_id=1").equals("3"),"Existing rooms not assigned to property 1");
        require(value("SELECT COUNT(*) FROM Bookings").equals("3"),"Property migration changed booking history");
        require(new PublicRoomController().detail(2).get("propertyName").equals("Cơ sở 1"),"Property missing from public room");
        require(new RoomController().getRoomById(2).get("propertyAddress").equals("123 Đường Hoa Mai, TP. Hồ Chí Minh"),"Property address missing from operations");
        sql("INSERT Properties(property_id,property_name,address) VALUES(2,N'Cơ sở 2',N'Test address'); UPDATE Rooms SET property_id=2 WHERE room_code='CUSTOM'; UPDATE Properties SET address=N'Edited address' WHERE property_id=1;");
        try(var c=DBConnection.getConnection()){RoomPropertiesMigration.migrate(c);}
        require(value("SELECT property_id FROM Rooms WHERE room_code='CUSTOM'").equals("2"),"Restart moved future property room");
        require(value("SELECT address FROM Properties WHERE property_id=1").equals("Edited address"),"Restart overwrote address edit");
        System.out.println("PASS merge rollback on overlap, adjacent stays, booking history/prices, immutable invoices, database backup, aliases, verified descriptions and idempotent restart");
        System.out.println("PASS property schema upgrade, all existing rooms assigned, API address, preserved history and future property/address edits");
    }
}
