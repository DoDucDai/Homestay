package com.homestay.controller;

import com.homestay.db.DBConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** Real SQL Server checks in a disposable database, never the configured database. */
public class BookingScheduleSqlServerIT {
    private static final java.time.Clock CLOCK = java.time.Clock.fixed(java.time.Instant.parse("2026-10-26T05:00:00Z"), RoomSchedule.ZONE);
    private static final java.time.LocalDate TODAY = RoomSchedule.today(CLOCK);
    private static final BookingController controller = new BookingController(CLOCK);
    private static final RoomController rooms = new RoomController(CLOCK);
    private static int passed;

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && "--isolated-child".equals(args[0])) {
            String database = System.getenv("DB_NAME");
            if (database == null || !database.matches("HomestayCore02Test_[a-f0-9]{32}")) {
                throw new IllegalStateException("Refusing tests outside a disposable database");
            }
            try (Connection conn = DBConnection.getConnection()) {
                if (!database.equals(conn.getCatalog())) throw new IllegalStateException("Wrong database");
            }
            runChecks();
            return;
        }
        String database = "HomestayCore02Test_" + UUID.randomUUID().toString().replace("-", "");
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
                        BookingScheduleSqlServerIT.class.getName(), "--isolated-child");
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
                    System.out.println("Disposed isolated CORE-02 test database.");
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


    private static String day(int offset) { return TODAY.plusDays(offset).toString(); }

    private static void reset() throws SQLException {
        sql("DROP TRIGGER IF EXISTS dbo.Core02FailRoom; DELETE FROM Payments; DELETE FROM Booking_Services; "
                + "DELETE FROM Bookings; DELETE FROM Rooms; DELETE FROM Customers; DELETE FROM Services; "
                + "SET IDENTITY_INSERT Customers ON; "
                + "INSERT INTO Customers(customer_id,full_name,cccd,phone) VALUES(1,N'Guest','000000000001','0900000000'); "
                + "SET IDENTITY_INSERT Customers OFF; SET IDENTITY_INSERT Rooms ON; "
                + "INSERT INTO Rooms(room_id,room_code,room_name,room_type,price,status) VALUES(1,'P1',N'Room','Single',300000,N'Trống'); "
                + "SET IDENTITY_INSERT Rooms OFF");
    }

    private static Map<String,Object> book(int start, int end) {
        return controller.createBooking(Map.of("customerId",1,"roomId",1,"checkinDate",day(start),"checkoutDate",day(end)));
    }

    private static int id(Map<String,Object> response) {
        require(success(response), "Expected success: " + response);
        return ((Number)response.get("bookingId")).intValue();
    }

    private static int existing(int start, int end, String status) throws SQLException {
        sql("INSERT INTO Bookings(customer_id,room_id,checkin_date,checkout_date,status,price_per_night) "
                + "VALUES(1,1,'" + day(start) + "','" + day(end) + "','" + status + "',300000)");
        return Integer.parseInt(value("SELECT MAX(booking_id) FROM Bookings"));
    }

    private static boolean available(int start, int end) {
        return controller.getAvailableRooms(day(start),day(end)).stream()
                .anyMatch(room -> ((Number)room.get("roomId")).intValue()==1);
    }

    private static String status() { return (String)rooms.getRoomById(1).get("status"); }

    private static Map<String,Object> edit(String status) {
        return rooms.updateRoom(1,Map.of("roomName","Room","roomType","Single","price",300000,"status",status));
    }

    private static void runChecks() throws Exception {
        reset();
        for (String[] dates : List.of(new String[]{day(-1),day(1)},new String[]{day(1),day(1)},
                new String[]{day(2),day(1)},new String[]{"2026-02-30",day(1)},
                new String[]{day(1)+"T12:00:00",day(2)},new String[]{"0000-01-01",day(1)})) {
            boolean rejected=false;
            try { controller.getAvailableRooms(dates[0],dates[1]); } catch(RoomSchedule.InvalidDates expected) { rejected=true; }
            require(rejected,"Invalid availability dates accepted");
            require(!success(controller.createBooking(Map.of("customerId",1,"roomId",1,
                    "checkinDate",dates[0],"checkoutDate",dates[1]))),"Invalid booking dates accepted");
        }
        require(!success(controller.createBooking(Map.of("customerId",1.5,"roomId",1,"checkinDate",day(0),"checkoutDate",day(2)))),"Fractional ID accepted");
        pass("strict date format, real dates, past/equal/reversed dates and fractional IDs rejected");

        require(!success(controller.createBooking(Map.of("customerId",999,"roomId",1,"checkinDate",day(0),"checkoutDate",day(2)))),"Missing customer accepted");
        require(!success(controller.createBooking(Map.of("customerId",1,"roomId",999,"checkinDate",day(0),"checkoutDate",day(2)))),"Missing room accepted");
        require("0".equals(value("SELECT COUNT(*) FROM Bookings")),"Rejected booking inserted rows");
        pass("missing room/customer rejected without creating booking");

        id(book(30,32));
        require("Trống".equals(status()),"Future booking marks room booked today");
        require(available(0,2),"Future reservation blocks today");
        id(book(0,2));
        require("Đã đặt".equals(status()),"Current confirmed reservation not reflected");
        pass("future reservation leaves today's room free and another interval can be booked");

        for (int[] interval : List.of(new int[]{30,32},new int[]{29,33},new int[]{30,31},new int[]{31,33},new int[]{29,31})) {
            require(!available(interval[0],interval[1]),"Overlap shown available");
            require(!success(book(interval[0],interval[1])),"Overlap accepted");
        }
        require(available(28,30) && available(32,34),"Adjacent reservations blocked");
        id(book(28,30)); id(book(32,34));
        pass("overlap shapes rejected; exact checkout/checkin boundary is allowed");

        reset();
        existing(0,2,"Cancelled"); existing(0,2,"CheckedOut");
        require(available(0,2),"Archived bookings block availability"); id(book(0,2));
        pass("Cancelled and CheckedOut do not block the calendar");

        reset();
        List<Map<String,Object>> results=concurrent(() -> book(0,2),() -> book(0,2));
        require(results.stream().filter(BookingScheduleSqlServerIT::success).count()==1,"Two overlapping concurrent bookings succeeded");
        require("1".equals(value("SELECT COUNT(*) FROM Bookings")),"Duplicate booking rows");
        pass("two concurrent overlapping creates succeed exactly once");

        reset();
        results=concurrent(() -> book(5,7),() -> book(7,9));
        require(results.stream().allMatch(BookingScheduleSqlServerIT::success),"Nonoverlapping concurrent bookings rejected");
        require("2".equals(value("SELECT COUNT(*) FROM Bookings")),"Missing nonoverlapping reservation");
        pass("two concurrent adjacent creates both succeed");

        reset();
        require(success(edit("Bảo trì")),"Cannot enable maintenance on empty room");
        require(!available(0,2) && !success(book(0,2)),"Maintenance room can be booked");
        require(success(edit("Trống")),"Cannot disable maintenance");
        id(book(10,12)); require(!success(edit("Bảo trì")),"Maintenance ignores future booking");
        require(!success(edit("Đang sử dụng")),"Manual fake occupancy accepted");
        pass("maintenance blocks availability/create; legacy edit API cannot bypass active bookings");

        reset();
        results=concurrent(() -> book(0,2),() -> edit("Bảo trì"));
        require(results.stream().filter(BookingScheduleSqlServerIT::success).count()==1,"Maintenance/create race both succeeded");
        boolean maintenance="Bảo trì".equals(status());
        require(maintenance ? "0".equals(value("SELECT COUNT(*) FROM Bookings"))
                : "1".equals(value("SELECT COUNT(*) FROM Bookings")),"Maintenance/create race inconsistent");
        pass("maintenance and booking creation serialize on the same room lock");

        reset();
        int cancelled=id(book(0,2));
        require(success(controller.cancelBooking(cancelled)),"Cancel failed");
        require("Trống".equals(status()) && available(0,2),"Cancelled reservation still blocks room");
        require(!success(controller.cancelBooking(cancelled)),"Repeated cancel accepted");
        require(!success(controller.cancelBooking(Integer.MAX_VALUE)),"Unknown cancel accepted");
        sql("UPDATE Bookings SET status='CheckedIn'"); require(!success(controller.cancelBooking(cancelled)),"CheckedIn cancellation accepted");
        sql("UPDATE Bookings SET status='CheckedOut'"); require(!success(controller.cancelBooking(cancelled)),"CheckedOut cancellation accepted");
        pass("only Confirmed may be cancelled and cancellation releases its calendar interval");

        reset();
        existing(-1,2,"CheckedIn");
        int future=id(book(10,12));
        require(success(controller.cancelBooking(future)),"Cancel other reservation while guest present failed");
        require("Đang sử dụng".equals(status()),"Cancellation cleared occupied room");
        require(success(edit("Trống")) && "Đang sử dụng".equals(status()),"Old room edit forces occupied room empty");
        pass("cancelling a future reservation and stale room edits preserve another resident");

        reset();
        int maintenanceCancel=id(book(10,12));
        sql("UPDATE Rooms SET status=N'Bảo trì'");
        require(success(controller.cancelBooking(maintenanceCancel)) && "Bảo trì".equals(status()),"Cancel erased maintenance");
        pass("cancellation preserves an existing maintenance flag");

        reset();
        int rollback=id(book(0,2));
        sql("CREATE TRIGGER dbo.Core02FailRoom ON dbo.Rooms AFTER UPDATE AS BEGIN THROW 51005,'Injected schedule failure',1; END");
        require(!success(controller.cancelBooking(rollback)),"Cancel with failed room update succeeded");
        require("Confirmed".equals(value("SELECT status FROM Bookings")),"Failed cancellation not rolled back");
        pass("cancellation rolls back booking when room status update fails");

        reset();
        int early=id(book(1,3)); require(!success(controller.checkIn(early)),"Early check-in accepted");
        int expired=existing(-2,0,"Confirmed"); require(!success(controller.checkIn(expired)),"Checkout-day check-in accepted");
        int valid=id(book(0,1)); require(success(controller.checkIn(valid)),"Valid check-in rejected");
        pass("check-in only permitted from planned arrival through the day before planned departure");

        reset();
        int overdue=existing(-3,-1,"CheckedIn");
        require(!available(0,2) && !success(book(0,2)),"Overdue resident not blocking today");
        require(available(5,7),"Overdue resident incorrectly blocks every future interval");
        id(book(5,7));
        int blocked=existing(0,2,"Confirmed"); require(!success(controller.checkIn(blocked)),"Second resident admitted to overdue room");
        require("Đang sử dụng".equals(status()),"Overdue occupancy missing");
        pass("overdue CheckedIn blocks today's booking and physical check-in, with future dates evaluated separately");

        reset();
        int departing=existing(-2,0,"CheckedIn");
        existing(0,2,"Confirmed"); // reserved earlier, now waiting for the previous guest
        require(success(controller.checkOut(departing,Map.of())),"Checkout failed");
        require("Đã đặt".equals(status()),"Checkout cleared the next current reservation");
        pass("checkout preserves the next Confirmed reservation covering today");

        reset();
        final int cancelling=id(book(0,2));
        results=concurrent(() -> controller.cancelBooking(cancelling),() -> controller.checkIn(cancelling));
        require(results.stream().filter(BookingScheduleSqlServerIT::success).count()==1,"Cancel/check-in race both succeeded");
        String bookingState=value("SELECT status FROM Bookings");
        require(("Cancelled".equals(bookingState) && "Trống".equals(status()))
                || ("CheckedIn".equals(bookingState) && "Đang sử dụng".equals(status())),"Cancel/check-in state mismatch");
        pass("cancel and check-in race commits one valid transition");

        reset();
        final int replacing=id(book(0,2));
        results=concurrent(() -> controller.cancelBooking(replacing),() -> book(0,2));
        require("Cancelled".equals(value("SELECT status FROM Bookings WHERE booking_id="+replacing)),"Cancel failed in create race");
        require(Integer.parseInt(value("SELECT COUNT(*) FROM Bookings WHERE status='Confirmed'"))<=1,"Cancel/create race duplicated reservation");
        pass("cancel and replacement-create race never leaves overlapping active reservations");

        reset();
        final int previous=existing(-2,0,"CheckedIn");
        final int next=existing(0,2,"Confirmed");
        results=concurrent(() -> controller.checkOut(previous,Map.of()),() -> controller.checkIn(next));
        require(success(results.get(0)),"Checkout failed during next check-in race");
        require("1".equals(value("SELECT COUNT(*) FROM Payments")),"Race duplicated payment");
        require(Integer.parseInt(value("SELECT COUNT(*) FROM Bookings WHERE status='CheckedIn'"))<=1,"Race double occupied room");
        require(success(results.get(1)) ? "Đang sử dụng".equals(status()) : "Đã đặt".equals(status()),"Race room status mismatch");
        pass("checkout and next check-in race preserves one resident and one invoice");

        reset();
        id(book(1,3));
        sql("UPDATE Rooms SET status=N'Đã đặt'");
        require("Trống".equals(status()),"Read trusts stale stored status");
        RoomController tomorrow=new RoomController(java.time.Clock.offset(CLOCK,java.time.Duration.ofDays(1)));
        RoomController later=new RoomController(java.time.Clock.offset(CLOCK,java.time.Duration.ofDays(3)));
        require("Đã đặt".equals(tomorrow.getRoomById(1).get("status")),"Status not updated after day rollover");
        require("Trống".equals(later.getRoomById(1).get("status")),"Status not released after interval ends");
        require("Trống".equals(rooms.getAllRooms().get(0).get("status")),"Room list/detail disagree");
        Map<String,Object> nowStats=new ServiceStatController(CLOCK).getOverview();
        Map<String,Object> nextStats=new ServiceStatController(java.time.Clock.offset(CLOCK,java.time.Duration.ofDays(1))).getOverview();
        require(((Number)nowStats.get("availableRooms")).intValue()==1,"Overview disagrees with rooms today");
        require(((Number)nextStats.get("bookedRooms")).intValue()==1,"Overview disagrees after day rollover");
        require("Đã đặt".equals(value("SELECT status FROM Rooms")),"Read unexpectedly wrote persisted status");
        pass("list/detail/statistics recompute status across day rollover without a write");

        java.time.Clock vietnamMidnight=java.time.Clock.fixed(java.time.Instant.parse("2026-10-26T17:30:00Z"),java.time.ZoneOffset.UTC);
        require(TODAY.plusDays(1).equals(RoomSchedule.today(vietnamMidnight)),"Date follows server UTC instead of Vietnam");
        BookingController afterMidnight=new BookingController(vietnamMidnight);
        require(!success(afterMidnight.createBooking(Map.of("customerId",1,"roomId",1,"checkinDate",day(0),"checkoutDate",day(1)))),"Vietnam past date accepted");
        pass("business dates use Asia/Ho_Chi_Minh regardless of host timezone");

        reset();
        existing(0,3,"Confirmed");existing(1,2,"Confirmed");
        String indexMigration=Files.readString(Path.of("../database/migrations/003_booking_schedule_index.sql"));
        sql("DROP INDEX IX_Bookings_Room_Status_Dates ON Bookings");
        sql(indexMigration);sql(indexMigration);
        require("1".equals(value("SELECT COUNT(*) FROM sys.indexes WHERE object_id=OBJECT_ID('Bookings') AND name='IX_Bookings_Room_Status_Dates'")),"Schedule index missing");
        require("2".equals(value("SELECT COUNT(*) FROM Bookings")),"Index migration changed existing conflicting reservations");
        pass("index migration repeatable and keeps existing conflicts for manual review");
        reset();
        require(rooms.getAllRooms("Single","Trống","P").size()==1,"Combined filters lost matching room");
        require(rooms.getAllRooms("VIP","Trống","P").isEmpty(),"Type filter ignored");
        require(rooms.getAllRooms(null,null,"' OR 1=1 --").isEmpty(),"Search is not literal/parameterized");
        require(rooms.getAllRooms(null,null,"%").isEmpty(),"Search treated percent as wildcard");
        pass("FE-03 combined type/status/literal code-name search");
        int filterFuture=id(book(1,3));
        require(rooms.getAllRooms(null,"Trống",null).size()==1,"Future booking blocks today filter");
        require(tomorrow.getAllRooms(null,"Đã đặt",null).size()==1,"Filter misses business-day rollover");
        require(later.getAllRooms(null,"Trống",null).size()==1,"Filter keeps stale reserved status");
        require(!success(rooms.updateStatus(1,Map.of("status","Bảo trì"))),"Status API bypassed future booking");
        require(!success(edit("Bảo trì")),"Legacy update bypassed booking");
        pass("FE-03 calculated status filtering across dates and maintenance blocked by future bookings");
        require(success(controller.cancelBooking(filterFuture)),"Could not cancel fixture booking");
        require(success(rooms.updateStatus(1,Map.of("status","Bảo trì"))),"Could not enable maintenance");
        require(rooms.getAllRooms(null,"Bảo trì",null).size()==1,"Maintenance filter mismatch");
        require(success(rooms.updateStatus(1,Map.of("status","Trống"))) && "Trống".equals(status()),"Activation failed");
        require(!success(rooms.updateStatus(1,Map.of("status","Đã đặt"))),"Manual reservation status accepted");
        require(!success(edit("Đang sử dụng")),"Legacy fake occupancy accepted");
        pass("FE-03 maintenance activation and arbitrary status assignment rejection");
        int resident=id(book(0,2)); require(success(controller.checkIn(resident)),"Fixture checkin failed");
        require(!success(rooms.updateStatus(1,Map.of("status","Bảo trì"))),"Occupied room entered maintenance");
        require(success(rooms.updateStatus(1,Map.of("status","Trống"))) && "Đang sử dụng".equals(status()),"Activation overwrote actual occupancy");
        Map<String,Object> paid=controller.checkOut(resident,Map.of("paymentMethod","Tiền mặt"));
        require(success(paid),"Fixture checkout failed");
        List<Map<String,Object>> payments=controller.getPayments(resident);
        require(payments.size()==1 && paid.get("paymentId").equals(payments.get(0).get("paymentId")),"Payment reconciliation list mismatch");
        require(controller.getPayments(resident+1000).isEmpty(),"Payment booking filter ignored");
        require(controller.getPayments(null).size()==1,"Payment history listing failed");
        pass("FE-02 payment list/reconciliation and FE-03 activation preserves occupied state");
        System.out.println("SQL Server CORE-02 + FE-02/03: " + passed + " groups passed.");
    }
}
