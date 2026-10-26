package com.homestay.controller;

import com.homestay.db.DBConnection;
import com.homestay.api.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;

/** One definition of room status/availability, shared by reads and transactions. */
final class RoomSchedule {
    static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    static final Clock CLOCK = Clock.system(ZONE);

    static class Conflict extends ApiProblem {
        Conflict(String message) { super(409,"BUSINESS_CONFLICT",message); }
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    static class InvalidDates extends ApiProblem {
        InvalidDates(String message) { super(400,"INVALID_DATES",message); }
    }

    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    static class QueryFailure extends ApiProblem {
        QueryFailure(SQLException cause) { super(503,"DATABASE_UNAVAILABLE","Không thể đọc lịch phòng. Vui lòng thử lại!");ApiErrors.database(cause); }
    }

    record Range(LocalDate checkin, LocalDate checkout) {}
    record LockedRoom(String status, BigDecimal price) {}

    @FunctionalInterface
    interface Work { Map<String, Object> execute(Connection conn) throws SQLException; }

    private RoomSchedule() {}

    static LocalDate today(Clock clock) { return LocalDate.now(clock.withZone(ZONE)); }

    static Range range(String checkin, String checkout, LocalDate today) {
        try {
            if (checkin == null || checkout == null || !checkin.matches("\\d{4}-\\d{2}-\\d{2}")
                    || !checkout.matches("\\d{4}-\\d{2}-\\d{2}")) throw new IllegalArgumentException();
            LocalDate cin = LocalDate.parse(checkin);
            LocalDate cout = LocalDate.parse(checkout);
            if (cin.getYear() < 1 || cout.getYear() < 1) throw new IllegalArgumentException();
            if (!cout.isAfter(cin)) throw new InvalidDates("Ngày trả phòng phải sau ngày nhận phòng!");
            if (cin.isBefore(today)) throw new InvalidDates("Không thể đặt phòng với ngày nhận trong quá khứ!");
            return new Range(cin, cout);
        } catch (InvalidDates e) {
            throw e;
        } catch (RuntimeException e) {
            throw new InvalidDates("Ngày không hợp lệ, cần đúng định dạng YYYY-MM-DD!");
        }
    }

    static Map<String, Object> transaction(Work work, String errorMessage) {
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Map<String, Object> result = work.execute(conn);
                conn.commit();
                return result;
            } catch (Exception e) {
                try { conn.rollback(); } catch (SQLException rollbackError) { e.addSuppressed(rollbackError); }
                if (e instanceof ApiProblem problem) return ApiErrors.from(problem);
                if (e instanceof SQLException sqlError) return ApiErrors.database(sqlError);
                return ApiErrors.unexpected(e);
            }
        } catch (SQLException e) {
            return ApiErrors.database(e);
        }
    }

    static LockedRoom lockRoom(Connection conn, int roomId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT status,price FROM Rooms WITH (XLOCK,HOLDLOCK) WHERE room_id=?")) {
            stmt.setInt(1, roomId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) throw ApiErrors.notFound("Phòng không tồn tại!");
                return new LockedRoom(rs.getString("status"), rs.getBigDecimal("price"));
            }
        }
    }

    // Two DATE parameters: <= today and > today. Only maintenance is manual.
    static String selectRooms() {
        return """
            SELECT r.room_id,r.room_code,r.room_name,r.room_type,r.price,r.description,
                CASE WHEN r.status=N'Bảo trì' THEN N'Bảo trì'
                     WHEN EXISTS(SELECT 1 FROM Bookings b WHERE b.room_id=r.room_id AND b.status='CheckedIn') THEN N'Đang sử dụng'
                     WHEN EXISTS(SELECT 1 FROM Bookings b WHERE b.room_id=r.room_id AND b.status='Confirmed'
                          AND b.checkin_date<=? AND b.checkout_date>?) THEN N'Đã đặt'
                     ELSE N'Trống' END AS status
            FROM Rooms r
            """;
    }

    static void bindToday(PreparedStatement stmt, LocalDate today) throws SQLException {
        stmt.setDate(1, java.sql.Date.valueOf(today));
        stmt.setDate(2, java.sql.Date.valueOf(today));
    }

    static Map<String, Object> roomRow(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("roomId", rs.getInt("room_id"));
        row.put("roomCode", rs.getString("room_code"));
        row.put("roomName", rs.getString("room_name"));
        row.put("roomType", rs.getString("room_type"));
        row.put("price", rs.getBigDecimal("price"));
        row.put("description", rs.getString("description"));
        row.put("status", rs.getString("status"));
        return row;
    }

    static String currentStatus(Connection conn, int roomId, LocalDate today) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(selectRooms() + " WHERE r.room_id=?")) {
            bindToday(stmt, today);
            stmt.setInt(3, roomId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) throw ApiErrors.notFound("Phòng không tồn tại!");
                return rs.getString("status");
            }
        }
    }

    // Caller must already hold the room lock. Preserves maintenance and other bookings.
    static String refresh(Connection conn, int roomId, LocalDate today) throws SQLException {
        String status = currentStatus(conn, roomId, today);
        try (PreparedStatement stmt = conn.prepareStatement("UPDATE Rooms SET status=? WHERE room_id=?")) {
            stmt.setString(1, status);
            stmt.setInt(2, roomId);
            if (stmt.executeUpdate() != 1) throw new SQLException("Room status update failed");
        }
        return status;
    }

    // Same predicate in available-rooms and createBooking. Closed/cancelled rows do not block.
    static String unavailablePredicate() {
        return """
            EXISTS(SELECT 1 FROM Bookings b WHERE b.room_id=r.room_id AND
                ((b.status IN ('Confirmed','CheckedIn') AND b.checkin_date<? AND b.checkout_date>?)
                 OR (b.status='CheckedIn' AND ?<=?)))
            """;
    }

    static void bindRange(PreparedStatement stmt, int start, Range range, LocalDate today) throws SQLException {
        stmt.setDate(start, java.sql.Date.valueOf(range.checkout()));
        stmt.setDate(start + 1, java.sql.Date.valueOf(range.checkin()));
        stmt.setDate(start + 2, java.sql.Date.valueOf(range.checkin()));
        stmt.setDate(start + 3, java.sql.Date.valueOf(today));
    }

    static void requireAvailable(Connection conn, int roomId, Range range, LocalDate today) throws SQLException {
        if (range.checkin().isBefore(today)) throw new Conflict("Ngày nhận phòng đã qua, vui lòng chọn lại!");
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT r.room_id FROM Rooms r WHERE r.room_id=? AND (r.status=N'Bảo trì' OR " + unavailablePredicate() + ")")) {
            stmt.setInt(1, roomId);
            bindRange(stmt, 2, range, today);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) throw new Conflict("Phòng không còn trống trong khoảng ngày đã chọn hoặc đang bảo trì!");
            }
        }
    }

    // Used by the old room edit API as well, so it cannot bypass schedule locks.
    static String requestedStatus(Connection conn, int roomId, String storedStatus,
                                  String requested, LocalDate today) throws SQLException {
        if (requested == null || requested.isBlank()) return currentStatus(conn, roomId, today);
        if ("Bảo trì".equals(requested)) {
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT booking_id FROM Bookings WHERE room_id=? AND status IN ('Confirmed','CheckedIn')")) {
                stmt.setInt(1, roomId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) throw new Conflict("Cần xử lý các booking đang hiệu lực trước khi bật bảo trì!");
                }
            }
            return "Bảo trì";
        }
        if ("Trống".equals(requested)) {
            if ("Bảo trì".equals(storedStatus)) {
                try (PreparedStatement stmt = conn.prepareStatement("UPDATE Rooms SET status=N'Trống' WHERE room_id=?")) {
                    stmt.setInt(1, roomId);
                    stmt.executeUpdate();
                }
            }
            return currentStatus(conn, roomId, today);
        }
        String actual = currentStatus(conn, roomId, today);
        if (!actual.equals(requested)) throw new Conflict("Trạng thái phòng được xác định từ booking, không thể gán tùy ý!");
        return actual;
    }
}
