package com.homestay.controller;

import com.homestay.db.DBConnection;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Selective branch integration checks; writes only to a UUID disposable database. */
public class BranchContributionSqlServerIT {
    private static void require(boolean ok,String message) { if(!ok) throw new AssertionError(message); }
    private static List<List<Map<String,String>>> results(Statement s,String sql) throws SQLException {
        var sets=new ArrayList<List<Map<String,String>>>();
        boolean result=s.execute(sql);
        while(true) {
            if(result) try(ResultSet rs=s.getResultSet()) {
                var rows=new ArrayList<Map<String,String>>();
                while(rs.next()) {
                    var row=new HashMap<String,String>();
                    for(int i=1;i<=rs.getMetaData().getColumnCount();i++) row.put(rs.getMetaData().getColumnLabel(i),rs.getString(i));
                    rows.add(row);
                }
                sets.add(rows);
            }
            else if(s.getUpdateCount()==-1) break;
            result=s.getMoreResults();
        }
        return sets;
    }
    private static String email(Statement s) throws SQLException {
        try(ResultSet rs=s.executeQuery("SELECT email FROM Customers WHERE customer_id=1")) {rs.next();return rs.getString(1);}
    }
    private static void reject(Statement s,String sql) throws SQLException {
        boolean rejected=false;try{s.execute(sql);}catch(SQLException expected){rejected=expected.getErrorCode()==51000 || expected.getErrorCode()==51001;}
        require(rejected,"Unsafe repair was not rejected");
    }
    public static void main(String[] args) throws Exception {
        String database="HomestayBranchTest_"+UUID.randomUUID().toString().replace("-","");
        try(Connection conn=DBConnection.getConnection();Statement s=conn.createStatement()) {
            boolean created=false;
            try {
                s.execute("CREATE DATABASE ["+database+"]");created=true;s.execute("USE ["+database+"]");
                String schema=Files.readString(Path.of("../database/schema.sql"));
                for(String batch:schema.substring(schema.indexOf("CREATE TABLE Users")).split("(?im)^\\s*GO\\s*$")) if(!batch.isBlank())s.execute(batch);
                s.execute("""
                    INSERT Customers(full_name,cccd,phone,email) VALUES(N'Test','001234567890','0901234567','an@email.coman@email.com');
                    INSERT Rooms(room_code,room_name,room_type,price,status) VALUES
                    ('R1','One','Single',100,N'Trống'),('R2','Two','Single',100,N'Trống'),
                    ('R3','Three','Single',100,N'Đã đặt'),('R4','Four','Single',100,N'Bảo trì');
                    INSERT Bookings(customer_id,room_id,checkin_date,checkout_date,status,price_per_night,created_at) VALUES
                    (1,1,'2031-01-01','2031-01-02','CheckedIn',100,'2031-01-01'),
                    (1,2,'2031-01-15','2031-01-16','Confirmed',100,'2031-01-01'),
                    (1,3,'2031-01-01','2031-01-02','Cancelled',100,'2031-01-01'),
                    (1,3,'2031-01-01','2031-01-02','CheckedOut',100,'2030-01-01');
                    INSERT Services(service_name,unit_price) VALUES('New catalog name',999);
                    INSERT Booking_Services(booking_id,service_id,quantity,total_price,service_name_snapshot,unit_price_snapshot) VALUES
                    (1,1,2,100,'Captured name',50),(2,1,9,450,'Captured name',50),(4,1,1,50,'Captured name',50);
                    INSERT Payments(booking_id,room_total,service_total,grand_total,payment_status,paid_at) VALUES
                    (1,100,100,200,N'Đã thanh toán','2031-01-05'),(4,100,50,150,N'Đã thanh toán','2030-01-05');
                    """);
                String report=Files.readString(Path.of("../database/reports/rooms_bookings_services.sql"))
                    .replace("CONVERT(date, SYSUTCDATETIME() AT TIME ZONE 'UTC' AT TIME ZONE 'SE Asia Standard Time')","CONVERT(date,'2031-01-15')");
                var sets=results(s,report);require(sets.size()==5,"Expected five report result sets");
                require(sets.get(0).size()==4 && sets.get(0).stream().allMatch(r->"1".equals(r.get("room_count"))),"Rollover/overdue status differs from API");
                require("1".equals(sets.get(1).get(0).get("non_cancelled_booking_count")) && "3".equals(sets.get(2).get(0).get("bookings_created_in_month")),"Ranking counts cancelled or wrong year");
                System.out.println("PASS BRANCH-01 room status, ranking and monthly booking count use correct lifecycle/year");
                var service=sets.get(3);require(service.size()==1 && "2".equals(service.get(0).get("paid_quantity")) && "100".equals(service.get(0).get("paid_service_line_total")) && "Captured name".equals(service.get(0).get("service_name_snapshot")),"Unpaid/other-year/catalog data contaminated paid services");
                require(sets.get(4).size()==2 && "1".equals(sets.get(4).get(0).get("overdue_checkout")),"Missing overdue occupancy");
                require(sets.equals(results(s,report)),"Read-only report changed data or result order");
                System.out.println("PASS BRANCH-02 paid service snapshots exclude unpaid/other-year lines; active list retains overdue guests");
                String repair=Files.readString(Path.of("../database/maintenance/repair_duplicated_customer_email.sql"));
                require(results(s,repair).get(0).size()==1 && "an@email.coman@email.com".equals(email(s)),"Preview modified data");
                reject(s,repair.replace("@Apply bit=0","@Apply bit=1"));
                require("an@email.coman@email.com".equals(email(s)),"Missing parameters changed customer");
                System.out.println("PASS BRANCH-03 email repair defaults to preview and rejects missing write parameters");
                String apply=repair.replace("@Apply bit=0","@Apply bit=1").replace("@CustomerId int=NULL","@CustomerId int=1")
                    .replace("@ExpectedEmail nvarchar(100)=NULL","@ExpectedEmail nvarchar(100)=N'an@email.coman@email.com'")
                    .replace("@CorrectEmail nvarchar(100)=NULL","@CorrectEmail nvarchar(100)=N'an@email.com'");
                reject(s,apply.replace("N'an@email.coman@email.com'","N'AN@email.comAN@email.com'").replace("N'an@email.com'","N'AN@email.com'"));
                require("an@email.coman@email.com".equals(email(s)),"Case mismatch changed customer");
                s.execute(apply);require("an@email.com".equals(email(s)),"Exact repair failed");reject(s,apply);
                require("an@email.com".equals(email(s)),"Repeated repair changed customer");
                System.out.println("PASS BRANCH-04 exact-match email repair succeeds once and rolls back mismatched/repeated writes");
            } finally {
                if(created) {s.execute("USE [master]");s.execute("ALTER DATABASE ["+database+"] SET SINGLE_USER WITH ROLLBACK IMMEDIATE");s.execute("DROP DATABASE ["+database+"]");System.out.println("Disposed isolated branch-review database.");}
            }
        }
    }
}
