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
// NOTE BACKEND: Quy tắc dùng chung về ngày giờ, trạng thái phòng, khả dụng và giao dịch để các API dùng cùng cách tính.
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

    // NOTE BACKEND: Lấy ngày nghiệp vụ theo múi giờ Việt Nam, không phụ thuộc múi giờ của máy chủ.
    static LocalDate today(Clock clock) { return LocalDate.now(clock.withZone(ZONE)); }

    // NOTE BACKEND: Kiểm tra ngày nhận/trả theo YYYY-MM-DD, ngày trả sau ngày nhận và ngày nhận không thuộc quá khứ.
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

    // NOTE BACKEND: Mở giao dịch JDBC, commit nếu hoàn tất; khi lỗi thì rollback và đổi lỗi thành phản hồi API phù hợp.
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

    // NOTE BACKEND: Khóa riêng một phòng tới cuối giao dịch để các lần đặt/nhận/trả đồng thời không ghi đè nhau.
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
    // NOTE BACKEND: Tạo câu SELECT tính trạng thái phòng từ booking; Bảo trì ưu tiên, sau đó Đang sử dụng, Đã đặt hoặc Trống.
    static String selectRooms() {
        return """
            SELECT r.room_id,r.room_code,r.room_name,r.room_type,r.price,r.weekend_price,r.weekend_days,r.hourly_price,r.hourly_package_2,r.hourly_package_4,r.hourly_package_8,r.description,
                r.property_id,p.property_name,p.address AS property_address,
                CASE WHEN r.status=N'Bảo trì' THEN N'Bảo trì'
                     WHEN EXISTS(SELECT 1 FROM Bookings b WHERE b.room_id=r.room_id AND b.status='CheckedIn') THEN N'Đang sử dụng'
                     WHEN EXISTS(SELECT 1 FROM Bookings b WHERE b.room_id=r.room_id AND b.status='Confirmed'
                          AND COALESCE(b.checkin_at,CAST(b.checkin_date AS datetime2))<=? AND COALESCE(b.checkout_at,CAST(b.checkout_date AS datetime2))>?) THEN N'Đã đặt'
                     ELSE N'Trống' END AS status
            FROM Rooms r JOIN Properties p ON p.property_id=r.property_id
            """;
    }

    // NOTE BACKEND: Gán hai tham số ngày cho câu truy vấn trạng thái tại ngày nghiệp vụ được truyền vào.
    static void bindToday(PreparedStatement stmt, LocalDate today) throws SQLException {
        stmt.setDate(1, java.sql.Date.valueOf(today));
        stmt.setDate(2, java.sql.Date.valueOf(today));
    }

    // NOTE BACKEND: Gán thời điểm Việt Nam hiện tại cho truy vấn trạng thái cần độ chính xác theo giờ.
    static void bindNow(PreparedStatement stmt, Clock clock) throws SQLException {
        Timestamp now=Timestamp.valueOf(HourlyStay.now(clock));
        stmt.setTimestamp(1,now);stmt.setTimestamp(2,now);
    }

    // NOTE BACKEND: Chuyển một dòng SQL thành dữ liệu phòng cho frontend, gồm cơ sở, giá thường/cuối tuần, gói giờ và trạng thái.
    static Map<String, Object> roomRow(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("roomId", rs.getInt("room_id"));
        row.put("roomCode", rs.getString("room_code"));
        row.put("roomName", rs.getString("room_name"));
        row.put("roomType", rs.getString("room_type"));
        row.put("propertyId",rs.getInt("property_id"));
        row.put("propertyName",rs.getString("property_name"));
        row.put("propertyAddress",rs.getString("property_address"));
        row.put("price", rs.getBigDecimal("price"));
        var nightly=NightlyPricing.room(rs);
        row.put("nightlyPricing",nightly);
        row.put("hourlyPrice", rs.getBigDecimal("hourly_price"));
        row.put("hourlyPackages", HourlyPricing.packages(rs));
        row.put("description", rs.getString("description"));
        row.put("status", rs.getString("status"));
        return row;
    }

    // NOTE BACKEND: Đọc lại trạng thái đã tính từ lịch phòng theo ngày truyền vào, thay vì chỉ tin cột status đang lưu.
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
    // NOTE BACKEND: Ghi lại trạng thái phòng đã tính; bên gọi phải giữ khóa phòng để đồng bộ với thao tác booking.
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
    // NOTE BACKEND: Điều kiện SQL nhận biết lịch chồng nhau; booking đã hủy/trả không chặn lịch, khách đang ở có quy tắc chặn bổ sung.
    static String unavailablePredicate() {
        return """
            EXISTS(SELECT 1 FROM Bookings b WHERE b.room_id=r.room_id AND
                ((b.status IN ('Confirmed','CheckedIn') AND COALESCE(b.checkin_at,CAST(b.checkin_date AS datetime2))<? AND COALESCE(b.checkout_at,CAST(b.checkout_date AS datetime2))>?)
                 OR (b.status='CheckedIn' AND ?<=?)))
            """;
    }

    // NOTE BACKEND: Gán đúng thứ tự các ngày cho điều kiện kiểm tra lịch chồng, dùng chung khi tìm phòng và khi ghi booking.
    static void bindRange(PreparedStatement stmt, int start, Range range, LocalDate today) throws SQLException {
        stmt.setDate(start, java.sql.Date.valueOf(range.checkout()));
        stmt.setDate(start + 1, java.sql.Date.valueOf(range.checkin()));
        stmt.setDate(start + 2, java.sql.Date.valueOf(range.checkin()));
        stmt.setDate(start + 3, java.sql.Date.valueOf(today));
    }

    // NOTE BACKEND: Kiểm tra lại phòng còn nhận đặt được bên trong giao dịch; nếu lịch vừa thay đổi hoặc có bảo trì thì báo xung đột.
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
    // NOTE BACKEND: Xử lý yêu cầu Trống/Bảo trì có kiểm tra booking; không cho gán trạng thái tùy ý trái với lịch đang có.
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
