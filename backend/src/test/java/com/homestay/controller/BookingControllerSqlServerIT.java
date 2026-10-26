package com.homestay.controller;

import com.homestay.db.DBConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** Real SQL Server checks in a disposable database, never the configured database. */
public class BookingControllerSqlServerIT {
    private static final BookingController controller = new BookingController();
    private static int passed;

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && "--isolated-child".equals(args[0])) {
            String database = System.getenv("DB_NAME");
            if (database == null || !database.matches("HomestayFix02Test_[a-f0-9]{32}")) {
                throw new IllegalStateException("Refusing tests outside a disposable database");
            }
            try (Connection conn = DBConnection.getConnection()) {
                if (!database.equals(conn.getCatalog())) throw new IllegalStateException("Wrong database");
            }
            runChecks();
            return;
        }
        String database = "HomestayFix02Test_" + UUID.randomUUID().toString().replace("-", "");
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
                        BookingControllerSqlServerIT.class.getName(), "--isolated-child");
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
                    System.out.println("Disposed isolated FIX-02 test database.");
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

    private static int fixture(String status) throws SQLException {
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        sql("DROP TRIGGER IF EXISTS dbo.Fix02FailRoomUpdate");
        sql("DELETE FROM Payments; DELETE FROM Booking_Services; DELETE FROM Bookings; DELETE FROM Rooms; DELETE FROM Customers");
        sql("INSERT INTO Customers(full_name,cccd,phone) VALUES(N'Test','000000000001','0900000000'); "
                + "INSERT INTO Rooms(room_code,room_name,room_type,price,status) VALUES('TEST',N'Test','Single',300000,N'Đã đặt'); "
                + "INSERT INTO Bookings(customer_id,room_id,checkin_date,checkout_date,status,price_per_night) "
                + "SELECT c.customer_id,r.room_id,'" + today + "','" + today.plusDays(2) + "','" + status + "',300000 FROM Customers c CROSS JOIN Rooms r");
        return Integer.parseInt(value("SELECT MAX(booking_id) FROM Bookings"));
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

    private static void runChecks() throws Exception {
        for (String status : List.of("Cancelled", "CheckedOut", "CheckedIn")) {
            int id = fixture(status);
            require(!success(controller.checkIn(id)), "Invalid check-in accepted: " + status);
            require(status.equals(value("SELECT status FROM Bookings")), "State changed on rejection");
        }
        pass("check-in rejects Cancelled/CheckedOut/CheckedIn");

        for (String status : List.of("Confirmed", "Cancelled", "CheckedOut")) {
            int id = fixture(status);
            require(!success(controller.previewCheckout(id)), "Invalid preview accepted");
            require(!success(controller.checkOut(id, Map.of())), "Invalid checkout accepted");
            require("0".equals(value("SELECT COUNT(*) FROM Payments")), "Rejected checkout created payment");
        }
        pass("preview and checkout reject non-CheckedIn bookings");

        require(!success(controller.checkIn(Integer.MAX_VALUE)), "Missing booking accepted");
        require(!success(controller.checkOut(Integer.MAX_VALUE, Map.of())), "Missing checkout accepted");
        pass("unknown booking rejected");

        int id = fixture("Confirmed");
        sql("UPDATE Rooms SET status=N'Bảo trì'");
        require(!success(controller.checkIn(id)), "Maintenance check-in accepted");
        sql("UPDATE Bookings SET status='CheckedIn'");
        require(!success(controller.checkOut(id, Map.of())), "Maintenance checkout accepted");
        pass("maintenance room rejected");

        id = fixture("Confirmed");
        sql("INSERT INTO Bookings(customer_id,room_id,checkin_date,checkout_date,status,price_per_night) "
                + "SELECT customer_id,room_id,checkin_date,checkout_date,'CheckedIn',price_per_night FROM Bookings");
        require(!success(controller.checkIn(id)), "Occupied room check-in accepted");
        pass("another resident blocks check-in");

        id = fixture("CheckedIn");
        sql("INSERT INTO Payments(booking_id,room_total,grand_total) VALUES(" + id + ",600000,600000)");
        require(!success(controller.checkOut(id, Map.of())), "Existing invoice ignored");
        require("1".equals(value("SELECT COUNT(*) FROM Payments")), "Existing invoice duplicated");
        pass("existing invoice rejected even with inconsistent booking status");

        final int checkoutId = fixture("CheckedIn");
        List<Map<String, Object>> results = concurrent(
                () -> controller.checkOut(checkoutId, Map.of()), () -> controller.checkOut(checkoutId, Map.of()));
        require(results.stream().filter(BookingControllerSqlServerIT::success).count() == 1, "Expected exactly one checkout");
        require("1".equals(value("SELECT COUNT(*) FROM Payments")), "Duplicate invoice");
        require("600000".equals(value("SELECT CAST(SUM(grand_total) AS BIGINT) FROM Payments")), "Revenue duplicated");
        require("CheckedOut".equals(value("SELECT status FROM Bookings")), "Checkout status missing");
        require("Trống".equals(value("SELECT status FROM Rooms")), "Room not released");
        pass("two concurrent checkouts produce one invoice and one revenue amount");

        final int checkinId = fixture("Confirmed");
        results = concurrent(() -> controller.checkIn(checkinId), () -> controller.checkIn(checkinId));
        require(results.stream().filter(BookingControllerSqlServerIT::success).count() == 1, "Expected exactly one check-in");
        pass("two concurrent check-ins succeed once");

        final int firstId = fixture("Confirmed");
        sql("INSERT INTO Bookings(customer_id,room_id,checkin_date,checkout_date,status,price_per_night) "
                + "SELECT customer_id,room_id,checkin_date,checkout_date,'Confirmed',price_per_night FROM Bookings");
        final int secondId = Integer.parseInt(value("SELECT MAX(booking_id) FROM Bookings"));
        results = concurrent(() -> controller.checkIn(firstId), () -> controller.checkIn(secondId));
        require(results.stream().filter(BookingControllerSqlServerIT::success).count() == 1, "Two residents admitted to one room");
        require("1".equals(value("SELECT COUNT(*) FROM Bookings WHERE status='CheckedIn'")), "Multiple residents");
        pass("different bookings cannot concurrently occupy the same room");

        for (String state : List.of("Confirmed", "CheckedIn")) {
            id = fixture(state);
            sql("CREATE TRIGGER dbo.Fix02FailRoomUpdate ON dbo.Rooms AFTER UPDATE AS BEGIN THROW 51002, 'Injected FIX-02 failure', 1; END");
            Map<String, Object> result = "Confirmed".equals(state)
                    ? controller.checkIn(id) : controller.checkOut(id, Map.of());
            require(!success(result), "Injected failure did not fail");
            require(state.equals(value("SELECT status FROM Bookings")), "Booking not rolled back");
            require("Đã đặt".equals(value("SELECT status FROM Rooms")), "Room not rolled back");
            require("0".equals(value("SELECT COUNT(*) FROM Payments")), "Payment not rolled back");
        }
        pass("failure after booking/payment writes rolls back all changes");

        id = fixture("CheckedIn");
        String migration = Files.readString(Path.of("../database/migrations/001_unique_payment_per_booking.sql"));
        sql(migration);
        sql(migration);
        sql("ALTER TABLE Payments DROP CONSTRAINT UQ_Payments_Booking");
        sql("INSERT INTO Payments(booking_id,room_total,grand_total) VALUES(" + id + ",600000,600000),(" + id + ",600000,600000)");
        boolean rejected = false;
        try { sql(migration); } catch (SQLException expected) { rejected = true; }
        require(rejected, "Migration accepted duplicates");
        require("2".equals(value("SELECT COUNT(*) FROM Payments")), "Migration deleted historical invoices");
        fixture("CheckedIn");
        sql(migration);
        pass("migration repeatable; duplicates stop it without deleting invoices");

        id = Integer.parseInt(value("SELECT MAX(booking_id) FROM Bookings"));
        sql("INSERT INTO Payments(booking_id,room_total,grand_total) VALUES(" + id + ",600000,600000)");
        rejected = false;
        try { sql("INSERT INTO Payments(booking_id,room_total,grand_total) VALUES(" + id + ",600000,600000)"); }
        catch (SQLException expected) { rejected = true; }
        require(rejected && "1".equals(value("SELECT COUNT(*) FROM Payments")), "Database uniqueness ineffective");
        pass("database constraint rejects direct duplicate payment inserts");
        System.out.println("SQL Server FIX-02: " + passed + " groups passed.");
    }
}
