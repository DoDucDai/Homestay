package com.homestay.controller;

import com.homestay.db.DBConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** Real SQL Server checks in a disposable database, never the configured database. */
public class BookingServicesSqlServerIT {
    private static final BookingController controller = new BookingController();
    private static int passed;

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && "--isolated-child".equals(args[0])) {
            String database = System.getenv("DB_NAME");
            if (database == null || !database.matches("HomestayCore03Test_[a-f0-9]{32}")) {
                throw new IllegalStateException("Refusing tests outside a disposable database");
            }
            try (Connection conn = DBConnection.getConnection()) {
                if (!database.equals(conn.getCatalog())) throw new IllegalStateException("Wrong database");
            }
            runChecks();
            return;
        }
        String database = "HomestayCore03Test_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection bootstrap = DBConnection.getConnection(); Statement stmt = bootstrap.createStatement()) {
            boolean created = false;
            try {
                stmt.execute("CREATE DATABASE [" + database + "]");
                created = true;
                stmt.execute("USE [" + database + "]");
                String schema = Files.readString(Path.of("../database/schema.sql"));
                // Keep table definitions, skip CREATE DATABASE / USE of the real database.
                schema = schema.substring(schema.indexOf("CREATE TABLE Users"));
                for (String batch : schema.split("(?im)^\\s*GO\\s*$")) {
                    if (!batch.isBlank()) stmt.execute(batch);
                }
                ProcessBuilder child = new ProcessBuilder(
                        Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
                        "-cp", System.getProperty("java.class.path"),
                        BookingServicesSqlServerIT.class.getName(), "--isolated-child");
                child.environment().put("DB_NAME", database);
                child.inheritIO();
                Process process = child.start();
                if (!process.waitFor(300, TimeUnit.SECONDS)) {
                    process.destroyForcibly().waitFor();
                    throw new AssertionError("SQL Server tests timed out");
                }
                if (process.exitValue() != 0) throw new AssertionError("SQL Server tests failed");
            } finally {
                if (created) {
                    stmt.execute("USE [master]");
                    stmt.execute("ALTER DATABASE [" + database + "] SET SINGLE_USER WITH ROLLBACK IMMEDIATE");
                    stmt.execute("DROP DATABASE [" + database + "]");
                    System.out.println("Disposed isolated CORE-03 test database.");
                }
            }
        }
    }

    private static void sql(String sql) throws SQLException {
        try (Connection conn = DBConnection.getConnection(); Statement stmt = conn.createStatement()) {
            boolean resultSet = stmt.execute(sql);
            // Drain every result so a THROW following a diagnostic SELECT is observed.
            while (resultSet || stmt.getUpdateCount() != -1) {
                if (resultSet) {
                    try (ResultSet rs = stmt.getResultSet()) {
                        while (rs.next()) { /* consume diagnostic rows */ }
                    }
                }
                resultSet = stmt.getMoreResults();
            }
        }
    }

    private static String value(String sql) throws SQLException {
        try (Connection conn = DBConnection.getConnection(); Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (!rs.next()) throw new AssertionError("Missing query result");
            return rs.getString(1);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static boolean success(Map<String, Object> response) {
        return Boolean.TRUE.equals(response.get("success"));
    }

    private static void pass(String name) {
        passed++;
        System.out.println("PASS " + name);
    }

    private static List<Map<String, Object>> concurrent(Supplier<Map<String, Object>> a,
                                                       Supplier<Map<String, Object>> b) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Map<String, Object>> one = executor.submit(() -> { start.await(); return a.get(); });
            Future<Map<String, Object>> two = executor.submit(() -> { start.await(); return b.get(); });
            start.countDown();
            return List.of(one.get(20, TimeUnit.SECONDS), two.get(20, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }


    private static int reset() throws SQLException {
        String today=RoomSchedule.today(RoomSchedule.CLOCK).toString();
        String departure=RoomSchedule.today(RoomSchedule.CLOCK).plusDays(2).toString();
        sql("DROP TRIGGER IF EXISTS dbo.Core03FailInsert; DROP TRIGGER IF EXISTS dbo.Core03FailDelete; "
                + "DELETE FROM Payments; DELETE FROM Booking_Services; DELETE FROM Bookings; DELETE FROM Rooms; DELETE FROM Customers; DELETE FROM Services; "
                + "SET IDENTITY_INSERT Customers ON; "
                + "INSERT INTO Customers(customer_id,full_name,cccd,phone) VALUES(1,N'Guest','000000000001','0900000000'); "
                + "SET IDENTITY_INSERT Customers OFF; SET IDENTITY_INSERT Rooms ON; "
                + "INSERT INTO Rooms(room_id,room_code,room_name,room_type,price,status) VALUES(1,'P1',N'Room','Single',300000,N'Đang sử dụng'); "
                + "SET IDENTITY_INSERT Rooms OFF; SET IDENTITY_INSERT Services ON; "
                + "INSERT INTO Services(service_id,service_name,unit_price,status) VALUES"
                + "(1,N'Water',10000,N'Hoạt động'),(2,N'Motorbike',150000,N'Hoạt động'),(3,N'Free',0,N'Hoạt động'); "
                + "SET IDENTITY_INSERT Services OFF; "
                + "INSERT INTO Bookings(customer_id,room_id,checkin_date,checkout_date,status,price_per_night) "
                + "VALUES(1,1,'"+today+"','"+departure+"','CheckedIn',300000)");
        return Integer.parseInt(value("SELECT MAX(booking_id) FROM Bookings"));
    }

    private static Map<String,Object> add(int bookingId,int serviceId,Object quantity) {
        return controller.addServiceToBooking(bookingId,Map.of("serviceId",serviceId,"quantity",quantity));
    }

    private static int line(Map<String,Object> response) {
        require(success(response),"Add failed: "+response);
        return ((Number)response.get("bookingServiceId")).intValue();
    }

    private static void amount(Object actual,String expected) {
        require(actual!=null && new java.math.BigDecimal(actual.toString()).compareTo(new java.math.BigDecimal(expected))==0,
                "Expected "+expected+" but got "+actual);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String,Object>> services(Map<String,Object> invoice) {
        return (List<Map<String,Object>>) invoice.get("services");
    }

    @SuppressWarnings("unchecked")
    private static Map<String,Object> paid(Map<String,Object> response) {
        require(success(response),"Checkout failed: "+response);
        return (Map<String,Object>)response.get("invoice");
    }

    private static void assertPaymentMatchesLines(Map<String,Object> checkout) throws SQLException {
        Map<String,Object> invoice=paid(checkout);
        amount(invoice.get("serviceTotal"),value("SELECT COALESCE(SUM(total_price),0) FROM Booking_Services"));
        require(services(invoice).size()==Integer.parseInt(value("SELECT COUNT(*) FROM Booking_Services")),"Paid details differ from stored lines");
        require("1".equals(value("SELECT COUNT(*) FROM Payments")),"Expected one payment");
        int paymentId=((Number)checkout.get("paymentId")).intValue();
        Map<String,Object> reprint=paid(controller.getPayment(paymentId));
        amount(reprint.get("grandTotal"),invoice.get("grandTotal").toString());
        require(services(reprint).size()==services(invoice).size(),"Frozen service details lost");
    }

    private static void runChecks() throws Exception {
        int booking=reset();
        int water=line(add(booking,1,2));
        int bike=line(add(booking,2,3));
        Map<String,Object> preview=controller.previewCheckout(booking);
        require(success(preview),"Preview failed");
        amount(preview.get("serviceTotal"),"470000");amount(preview.get("grandTotal"),"1070000");
        require(services(preview).size()==2,"Missing service lines");
        Map<String,Object> first=services(preview).get(0);
        require(((Number)first.get("bookingServiceId")).intValue()==water,"Missing line ID");
        require("Water".equals(first.get("serviceName")),"Missing name");
        require(((Number)first.get("quantity")).intValue()==2,"Missing quantity");
        amount(first.get("unitPrice"),"10000");amount(first.get("totalPrice"),"20000");
        pass("two services with multiple quantities produce correct detailed preview and totals");

        int repeat=line(add(booking,1,1));
        require(repeat!=water && repeat!=bike,"Repeated order reused an existing line");
        require("3".equals(value("SELECT COUNT(*) FROM Booking_Services")),"Repeated service order merged");
        require(success(controller.removeServiceFromBooking(water)),"Remove failed");
        preview=controller.previewCheckout(booking);
        amount(preview.get("serviceTotal"),"460000");
        require(services(preview).size()==2,"Wrong line removed");
        require(!success(controller.removeServiceFromBooking(water)),"Repeated removal accepted");
        pass("repeated orders create distinct IDs and deleting one line updates only its amount");

        sql("UPDATE Services SET service_name=N'Renamed water',unit_price=20000 WHERE service_id=1");
        preview=controller.previewCheckout(booking);
        Map<String,Object> oldWater=services(preview).stream().filter(row -> ((Number)row.get("bookingServiceId")).intValue()==repeat).findFirst().orElseThrow();
        require("Water".equals(oldWater.get("serviceName")),"Old name changed");amount(oldWater.get("unitPrice"),"10000");
        Map<String,Object> fresh=add(booking,1,1);line(fresh);
        require("Renamed water".equals(fresh.get("serviceName")),"New order did not capture current name");
        amount(fresh.get("unitPrice"),"20000");
        pass("existing lines retain captured prices/names and new orders capture the updated catalog");

        booking=reset();
        for(Object invalid : List.of(0,-1,1.5,"1.5",10001,Long.MAX_VALUE,"bad",true,"","NaN","Infinity",List.of(1))) {
            require(!success(add(booking,1,invalid)),"Invalid quantity accepted: "+invalid);
        }
        for(Object invalidId : List.of(0,-1,1.5,"bad",true,Long.MAX_VALUE)) {
            require(!success(controller.addServiceToBooking(booking,Map.of("serviceId",invalidId,"quantity",1))),"Invalid service ID accepted");
        }
        require(!success(controller.addServiceToBooking(booking,null)),"Null body accepted");
        require(!success(controller.addServiceToBooking(booking,Map.of())),"Empty body accepted");
        Map<String,Object> nullQuantity=new HashMap<>();nullQuantity.put("serviceId",1);nullQuantity.put("quantity",null);
        require(!success(controller.addServiceToBooking(booking,nullQuantity)),"Null quantity accepted");
        require("0".equals(value("SELECT COUNT(*) FROM Booking_Services")),"Invalid data wrote service lines");
        pass("invalid quantities, IDs and missing/null payloads rejected without writes");

        line(add(booking,1,10000));line(add(booking,3,1));
        preview=controller.previewCheckout(booking);amount(preview.get("serviceTotal"),"100000000");
        pass("quantity upper bound and zero-priced active service supported");

        require(!success(add(Integer.MAX_VALUE,1,1)),"Unknown booking accepted");
        require(!success(add(0,1,1)),"Nonpositive booking accepted");
        require(!success(add(booking,Integer.MAX_VALUE,1)),"Unknown catalog service accepted");
        require(!success(controller.removeServiceFromBooking(Integer.MAX_VALUE)),"Unknown line removal accepted");
        require(!success(controller.removeServiceFromBooking(0)),"Nonpositive line removal accepted");
        pass("unknown booking/catalog/line IDs and nonpositive path IDs rejected");

        booking=reset();
        int retired=line(add(booking,1,1));
        sql("UPDATE Services SET status=N'Ngừng' WHERE service_id=1");
        require(!success(add(booking,1,1)),"Stopped service ordered");
        require(success(controller.removeServiceFromBooking(retired)),"Cannot correct existing line after catalog retirement");
        pass("stopped services cannot be ordered but existing mistaken lines can be removed");

        for(String state : List.of("Confirmed","Cancelled","CheckedOut")) {
            booking=reset();int locked=line(add(booking,1,1));
            sql("UPDATE Bookings SET status='"+state+"'");
            require(!success(add(booking,1,1)),"Order accepted for "+state);
            require(!success(controller.removeServiceFromBooking(locked)),"Removal accepted for "+state);
            require("1".equals(value("SELECT COUNT(*) FROM Booking_Services")),"Rejected operation changed line");
        }
        pass("Confirmed/Cancelled/CheckedOut reject both add and removal");

        booking=reset();int alreadyPaid=line(add(booking,1,1));
        sql("INSERT INTO Payments(booking_id,room_total,service_total,grand_total) VALUES("+booking+",600000,10000,610000)");
        require(!success(add(booking,1,1)),"Paid CheckedIn booking accepted add");
        require(!success(controller.removeServiceFromBooking(alreadyPaid)),"Paid CheckedIn booking accepted removal");
        pass("existing payment blocks service mutation even with inconsistent CheckedIn state");

        booking=reset();
        sql("UPDATE Bookings SET checkin_date=DATEADD(DAY,-4,checkin_date),checkout_date=DATEADD(DAY,-4,checkout_date)");
        int overdue=line(add(booking,1,1));
        require(success(controller.removeServiceFromBooking(overdue)),"Overdue resident cannot correct service");
        pass("actual CheckedIn residents can order/correct services after planned departure");

        booking=reset();
        Map<String,Object> spoof=controller.addServiceToBooking(booking,Map.of("serviceId",1,"quantity",2,
                "unitPrice",1,"totalPrice",1,"serviceName","Fake","bookingId",Integer.MAX_VALUE));
        line(spoof);amount(spoof.get("unitPrice"),"10000");amount(spoof.get("totalPrice"),"20000");
        require("Water".equals(spoof.get("serviceName")),"Client name trusted");
        pass("client price, name and booking overrides ignored in favor of path and server catalog");

        booking=reset();
        final int simultaneousAdd=booking;
        List<Map<String,Object>> results=concurrent(() -> add(simultaneousAdd,1,2),() -> add(simultaneousAdd,2,1));
        require(results.stream().allMatch(BookingServicesSqlServerIT::success),"Concurrent valid orders rejected");
        amount(controller.previewCheckout(booking).get("serviceTotal"),"170000");
        require("2".equals(value("SELECT COUNT(*) FROM Booking_Services")),"Concurrent lines lost");
        pass("concurrent service orders serialize without losing a line or amount");

        final int duplicateDelete=line(add(booking,1,1));
        results=concurrent(() -> controller.removeServiceFromBooking(duplicateDelete),() -> controller.removeServiceFromBooking(duplicateDelete));
        require(results.stream().filter(BookingServicesSqlServerIT::success).count()==1,"Both duplicate deletes succeeded");
        amount(controller.previewCheckout(booking).get("serviceTotal"),"170000");
        pass("concurrent deletion of one line succeeds once");

        booking=reset();
        final int changing=booking;final int toRemove=line(add(booking,1,2));
        results=concurrent(() -> add(changing,2,1),() -> controller.removeServiceFromBooking(toRemove));
        require(results.stream().allMatch(BookingServicesSqlServerIT::success),"Concurrent add/remove failed");
        amount(controller.previewCheckout(booking).get("serviceTotal"),"150000");
        pass("concurrent add and removal affect only their own lines");

        booking=reset();
        final int racingAdd=booking;
        results=concurrent(() -> add(racingAdd,1,1),() -> controller.checkOut(racingAdd,Map.of()));
        assertPaymentMatchesLines(results.get(1));
        amount(paid(results.get(1)).get("serviceTotal"),success(results.get(0))?"10000":"0");
        require(!success(add(booking,1,1)),"Service added after checkout");
        pass("add versus checkout either bills the new service or rejects it after payment");

        booking=reset();
        final int racingDeleteBooking=booking;final int racingDelete=line(add(booking,1,2));
        results=concurrent(() -> controller.removeServiceFromBooking(racingDelete),() -> controller.checkOut(racingDeleteBooking,Map.of()));
        assertPaymentMatchesLines(results.get(1));
        amount(paid(results.get(1)).get("serviceTotal"),success(results.get(0))?"0":"20000");
        require(!success(controller.removeServiceFromBooking(racingDelete)),"Service removed after checkout");
        pass("removal versus checkout either removes before billing or preserves the paid service");

        booking=reset();line(add(booking,1,1));
        Map<String,Object> completed=controller.checkOut(booking,Map.of());assertPaymentMatchesLines(completed);
        int paymentId=((Number)completed.get("paymentId")).intValue();
        String frozen=value("SELECT invoice_snapshot FROM Payments WHERE payment_id="+paymentId);
        int paidLine=Integer.parseInt(value("SELECT MAX(id) FROM Booking_Services"));
        require(!success(add(booking,2,1)) && !success(controller.removeServiceFromBooking(paidLine)),"Paid invoice changed");
        require(frozen.equals(value("SELECT invoice_snapshot FROM Payments WHERE payment_id="+paymentId)),"Frozen invoice overwritten");
        pass("post-payment add/removal rejected without modifying the frozen invoice");

        booking=reset();
        sql("CREATE TRIGGER dbo.Core03FailInsert ON dbo.Booking_Services AFTER INSERT AS BEGIN THROW 51006,'Injected service insert failure',1; END");
        require(!success(add(booking,1,1)),"Failed insert succeeded");
        require("0".equals(value("SELECT COUNT(*) FROM Booking_Services")),"Insert failure left row");
        sql("DROP TRIGGER dbo.Core03FailInsert");
        int deleteFailure=line(add(booking,1,2));
        sql("CREATE TRIGGER dbo.Core03FailDelete ON dbo.Booking_Services AFTER DELETE AS BEGIN THROW 51007,'Injected service delete failure',1; END");
        require(!success(controller.removeServiceFromBooking(deleteFailure)),"Failed delete succeeded");
        require("1".equals(value("SELECT COUNT(*) FROM Booking_Services")),"Delete failure lost row");
        amount(controller.previewCheckout(booking).get("serviceTotal"),"20000");
        sql("DROP TRIGGER dbo.Core03FailDelete");
        require(success(controller.removeServiceFromBooking(deleteFailure)),"Removal not retryable after rollback");
        pass("insert/delete failures roll back cleanly and can be retried");

        booking=reset();
        sql("UPDATE Services SET unit_price=450000000000 WHERE service_id=1");
        line(add(booking,1,1));line(add(booking,1,1));
        require(!success(add(booking,1,1)),"Cumulative overflow accepted");
        require(!success(add(booking,1,3)),"Single-line overflow accepted");
        require("2".equals(value("SELECT COUNT(*) FROM Booking_Services")),"Overflow left an extra line");
        amount(controller.previewCheckout(booking).get("serviceTotal"),"900000000000");
        pass("single-line and cumulative service-total overflow rejected without partial inserts");

        booking=reset();
        sql("UPDATE Services SET unit_price=999999400000 WHERE service_id=1");
        require(!success(add(booking,1,1)),"Grand total overflow including room amount accepted");
        require("0".equals(value("SELECT COUNT(*) FROM Booking_Services")),"Grand total overflow left line");
        pass("grand total limit includes room charges before accepting a service");

        booking=reset();
        int original=line(add(booking,1,1));
        sql("SET IDENTITY_INSERT Rooms ON; INSERT INTO Rooms(room_id,room_code,room_name,room_type,price,status) "
                + "VALUES(2,'P2',N'Other room','Single',300000,N'Đang sử dụng'); SET IDENTITY_INSERT Rooms OFF; "
                + "INSERT INTO Bookings(customer_id,room_id,checkin_date,checkout_date,status,price_per_night) "
                + "SELECT customer_id,2,checkin_date,checkout_date,'CheckedIn',price_per_night FROM Bookings WHERE booking_id="+booking);
        int otherBooking=Integer.parseInt(value("SELECT MAX(booking_id) FROM Bookings"));
        int otherLine=line(add(otherBooking,2,1));
        require(success(controller.removeServiceFromBooking(original)),"Cannot remove selected line");
        require("1".equals(value("SELECT COUNT(*) FROM Booking_Services WHERE id="+otherLine)),"Another booking's line deleted");
        amount(controller.previewCheckout(otherBooking).get("serviceTotal"),"150000");
        pass("removing a selected line leaves another booking's services unchanged");
        System.out.println("SQL Server CORE-03: "+passed+" groups passed.");
    }
}
