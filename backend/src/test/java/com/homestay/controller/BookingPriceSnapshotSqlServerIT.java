package com.homestay.controller;

import com.homestay.db.DBConnection;
import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Exercises legacy migration and new snapshots in a disposable SQL Server DB. */
public class BookingPriceSnapshotSqlServerIT {
    private static final BookingController controller = new BookingController();
    private static int passed;

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && "--isolated-child".equals(args[0])) {
            String database = System.getenv("DB_NAME");
            if (database == null || !database.matches("HomestayCore01Test_[a-f0-9]{32}")) {
                throw new IllegalStateException("Refusing tests outside disposable database");
            }
            try (Connection conn = DBConnection.getConnection()) {
                require(database.equals(conn.getCatalog()), "Wrong test database");
            }
            runChecks();
            return;
        }
        String database = "HomestayCore01Test_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection bootstrap = DBConnection.getConnection(); Statement stmt = bootstrap.createStatement()) {
            boolean created = false;
            try {
                stmt.execute("CREATE DATABASE [" + database + "]");
                created = true;
                stmt.execute("USE [" + database + "]");
                String schema = Files.readString(Path.of("../database/schema.sql"));
                schema = schema.substring(schema.indexOf("CREATE TABLE Users"));
                // Reconstruct the pre-CORE-01 schema for real migration coverage.
                schema = schema.lines().filter(line -> !line.contains("snapshot")
                        && !line.contains("Snapshot") && !line.contains("price_per_night")
                        && !line.contains("reconciliation_warning")).reduce("", (a,b) -> a + b + "\n");
                for (String batch : schema.split("(?im)^\\s*GO\\s*$")) {
                    if (!batch.isBlank()) stmt.execute(batch);
                }
                ProcessBuilder child = new ProcessBuilder(
                        Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
                        "-cp", System.getProperty("java.class.path"),
                        BookingPriceSnapshotSqlServerIT.class.getName(), "--isolated-child");
                child.environment().put("DB_NAME", database);
                child.inheritIO();
                Process process = child.start();
                if (!process.waitFor(300, TimeUnit.SECONDS)) {
                    process.destroyForcibly().waitFor();
                    throw new AssertionError("CORE-01 tests timed out");
                }
                require(process.exitValue() == 0, "CORE-01 SQL Server checks failed");
            } finally {
                if (created) {
                    stmt.execute("USE [master]");
                    stmt.execute("ALTER DATABASE [" + database + "] SET SINGLE_USER WITH ROLLBACK IMMEDIATE");
                    stmt.execute("DROP DATABASE [" + database + "]");
                    System.out.println("Disposed isolated CORE-01 test database.");
                }
            }
        }
    }

    private static void sql(String query) throws SQLException {
        try (Connection conn = DBConnection.getConnection(); Statement stmt = conn.createStatement()) {
            boolean resultSet = stmt.execute(query);
            while (resultSet || stmt.getUpdateCount() != -1) {
                if (resultSet) try (ResultSet rs = stmt.getResultSet()) { while (rs.next()) {} }
                resultSet = stmt.getMoreResults();
            }
        }
    }

    private static String value(String query) throws SQLException {
        try (Connection conn = DBConnection.getConnection(); Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            require(rs.next(), "Missing result");
            return rs.getString(1);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void pass(String name) { passed++; System.out.println("PASS " + name); }

    private static boolean success(Map<String, Object> response) { return Boolean.TRUE.equals(response.get("success")); }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> invoice(Map<String, Object> response) {
        require(success(response), "Invoice request failed: " + response.get("message"));
        return (Map<String, Object>) response.get("invoice");
    }

    private static void amount(Object actual, String expected) {
        require(actual != null && new BigDecimal(actual.toString()).compareTo(new BigDecimal(expected)) == 0,
                "Amount expected " + expected + " but was " + actual);
    }

    private static void clear() throws SQLException {
        sql("DROP TRIGGER IF EXISTS dbo.Core01FailSnapshot; DELETE FROM Payments; DELETE FROM Booking_Services; "
                + "DELETE FROM Bookings; DELETE FROM Rooms; DELETE FROM Customers; DELETE FROM Services; DELETE FROM Users");
        for (String table : List.of("Users","Payments","Booking_Services","Bookings","Rooms","Customers","Services")) {
            sql("DBCC CHECKIDENT ('" + table + "', RESEED, 0) WITH NO_INFOMSGS");
        }
    }

    private static void runChecks() throws Exception {
        sql("INSERT INTO Customers(full_name,cccd,phone,address) VALUES(N'Legacy guest','000000000001','0900000000',N'Old address'); "
                + "INSERT INTO Rooms(room_code,room_name,room_type,price,status) VALUES('POLD',N'Legacy room','Single',300000,N'Trống'); "
                + "INSERT INTO Services(service_name,unit_price) VALUES(N'Legacy service',999); "
                + "INSERT INTO Bookings(customer_id,room_id,checkin_date,checkout_date,status) VALUES "
                + "(1,1,'2026-10-26','2026-10-28','CheckedOut'),(1,1,'2026-10-28','2026-10-30','CheckedOut'),"
                + "(1,1,'2026-10-30','2026-11-01','CheckedOut'); "
                + "INSERT INTO Booking_Services(booking_id,service_id,quantity,total_price) VALUES(1,1,3,-1); "
                + "INSERT INTO Payments(booking_id,room_total,service_total,grand_total) VALUES"
                + "(1,600000,100,600100),(2,400000,0,400000),(3,600000,0,600000)");
        String migration = Files.readString(Path.of("../database/migrations/002_transaction_price_snapshots.sql"));
        boolean failed = false;
        try { sql(migration); } catch (SQLException expected) { failed = true; }
        require(failed, "Invalid legacy amount not rejected");
        require(value("SELECT COL_LENGTH('dbo.Bookings','price_per_night')") == null, "Failed migration left partial DDL");
        require("3".equals(value("SELECT COUNT(*) FROM Payments")), "Legacy data lost");
        pass("bad legacy data rolls migration back, preserving schema and invoices");

        sql("UPDATE Booking_Services SET total_price=100");
        sql(migration);
        Map<String, Object> legacy = invoice(controller.getPayment(1));
        amount(legacy.get("grandTotal"), "600100");
        require(Boolean.TRUE.equals(legacy.get("snapshotEstimated")), "Legacy snapshot falsely exact");
        require(legacy.get("reconciliationWarning") != null, "Fractional legacy service not flagged");
        require(invoice(controller.getPayment(2)).get("reconciliationWarning") != null, "Legacy room mismatch not flagged");
        require(invoice(controller.getPayment(3)).get("reconciliationWarning") == null, "Reconciled legacy invoice falsely mismatched");
        pass("legacy paid totals preserved, estimates marked, room/service mismatches reported");

        String frozenLegacy = value("SELECT invoice_snapshot FROM Payments WHERE payment_id=1");
        sql("UPDATE Rooms SET price=900000,room_name=N'Changed'; UPDATE Services SET service_name=N'Changed',unit_price=888; UPDATE Customers SET full_name=N'Changed'");
        sql(migration);
        require(frozenLegacy.equals(value("SELECT invoice_snapshot FROM Payments WHERE payment_id=1")), "Migration overwrote frozen invoice");
        amount(invoice(controller.getPayment(1)).get("pricePerNight"), "300000");
        pass("migration repeatable after catalog changes without rewriting historical snapshots");

        clear();
        sql("INSERT INTO Customers(full_name,cccd,phone,address) VALUES(N'Original guest','000000000002','0911111111',N'Original address'); "
                + "INSERT INTO Rooms(room_code,room_name,room_type,price,status) VALUES('PNEW',N'Original room','Single',300000,N'Trống'); "
                + "INSERT INTO Services(service_name,unit_price) VALUES(N'Original service',50000)");
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        Map<String, Object> booking = controller.createBooking(Map.of("customerId",1,"roomId",1,"checkinDate",today.toString(),
                "checkoutDate",today.plusDays(2).toString(),"pricePerNight",1));
        require(success(booking), "Booking creation failed");
        int bookingId = ((Number) booking.get("bookingId")).intValue();
        sql("UPDATE Rooms SET price=900000");
        require(success(controller.checkIn(bookingId)), "Check-in failed");
        Map<String, Object> preview = controller.previewCheckout(bookingId);
        require(success(preview), "Preview failed");
        amount(preview.get("pricePerNight"), "300000");
        amount(preview.get("roomTotal"), "600000");
        pass("booking captures server room price; later price edits and client price ignored");

        require(success(controller.addServiceToBooking(bookingId,Map.of("serviceId",1,"quantity",2))), "Add service failed");
        sql("UPDATE Services SET unit_price=999999,service_name=N'New service name'");
        preview = controller.previewCheckout(bookingId);
        amount(preview.get("serviceTotal"),"100000");
        @SuppressWarnings("unchecked")
        List<Map<String,Object>> services = (List<Map<String,Object>>) preview.get("services");
        require("Original service".equals(services.get(0).get("serviceName")), "Service name changed retroactively");
        amount(services.get(0).get("unitPrice"),"50000");
        require(!success(controller.addServiceToBooking(bookingId,Map.of("serviceId",1,"quantity",1.5))), "Fractional quantity accepted");
        pass("service order captures price/name and rejects fractional quantity");

        sql("CREATE TRIGGER dbo.Core01FailSnapshot ON dbo.Payments AFTER UPDATE AS BEGIN THROW 51004,'Injected snapshot failure',1; END");
        require(!success(controller.checkOut(bookingId,Map.of())), "Failed snapshot write committed");
        require("0".equals(value("SELECT COUNT(*) FROM Payments")), "Orphan payment after snapshot error");
        require("CheckedIn".equals(value("SELECT status FROM Bookings")), "Booking not rolled back");
        sql("DROP TRIGGER dbo.Core01FailSnapshot");
        pass("snapshot serialization/storage and payment are in the same rollback transaction");

        Map<String,Object> checkout = controller.checkOut(bookingId,Map.of("paymentMethod","Chuyển khoản"));
        Map<String,Object> paid = invoice(checkout);
        int paymentId = ((Number) checkout.get("paymentId")).intValue();
        require(paymentId == ((Number) paid.get("paymentId")).intValue(), "Payment ID missing/mismatched");
        amount(paid.get("grandTotal"),"700000");
        require(Boolean.FALSE.equals(paid.get("snapshotEstimated")), "New transaction marked estimated");
        sql("UPDATE Customers SET full_name=N'Changed customer',phone='0999999999',address=N'Changed address'; "
                + "UPDATE Rooms SET room_name=N'Changed room',room_code='RENAMED',price=123; "
                + "UPDATE Services SET unit_price=1,service_name=N'Changed again'; "
                + "DELETE FROM Booking_Services");
        Map<String,Object> reread = invoice(controller.getPayment(paymentId));
        require("Original guest".equals(reread.get("customerName")), "Paid customer name changed");
        require("Original room".equals(reread.get("roomName")), "Paid room name changed");
        require("PNEW".equals(reread.get("roomCode")), "Paid room code changed");
        require("Original address".equals(reread.get("customerAddress")), "Paid address changed");
        require("Chuyển khoản".equals(reread.get("paymentMethod")), "Payment method missing");
        amount(reread.get("grandTotal"),"700000");
        require(((List<?>)reread.get("services")).size()==1, "Invoice service detail lost");
        require(!success(controller.getPayment(Integer.MAX_VALUE)), "Missing payment accepted");
        pass("payment ID, totals, guest/room/service details remain frozen for reprints");

        sql("UPDATE Bookings SET status='CheckedIn',price_per_night=999999999999; DELETE FROM Payments; UPDATE Rooms SET status=N'Đang sử dụng'");
        require(!success(controller.checkOut(bookingId,Map.of())), "Overflowing amount accepted");
        require("0".equals(value("SELECT COUNT(*) FROM Payments")), "Overflow created payment");
        pass("amount overflow rejected without creating payment");

        clear();
        String seed = Files.readString(Path.of("../database/seed_data.sql"));
        seed = seed.substring(seed.indexOf("INSERT INTO Users"));
        for (String batch : seed.split("(?im)^\\s*GO\\s*$")) if (!batch.isBlank()) sql(batch);
        Map<String,Object> sample = invoice(controller.getPayment(1));
        amount(sample.get("grandTotal"),"1100000");
        require(((List<?>)sample.get("services")).size()==1, "Seed service details missing");
        sql(migration);
        amount(invoice(controller.getPayment(1)).get("grandTotal"),"1100000");
        pass("seed works with migrated schema and contains a complete frozen sample invoice");
        System.out.println("SQL Server CORE-01: " + passed + " groups passed.");
    }
}
