package com.homestay.controller;

import com.homestay.db.DBConnection;
import org.springframework.web.bind.annotation.*;
import java.sql.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class BookingController {

    private int parseInt(Object val, int defaultVal) {
        if (val == null) return defaultVal;
        if (val instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(val.toString().trim());
        } catch (Exception e) {
            return defaultVal;
        }
    }

    // GET /api/bookings?status=Confirmed
    @GetMapping("/bookings")
    public List<Map<String, Object>> getBookings(@RequestParam(required = false) String status) {
        List<Map<String, Object>> result = new ArrayList<>();

        StringBuilder sql = new StringBuilder("""
            SELECT b.booking_id, b.customer_id, b.room_id, b.checkin_date, b.checkout_date, b.status, b.note, b.created_at,
                   c.full_name AS customer_name, c.phone, c.cccd,
                   r.room_code, r.room_name, r.price, r.room_type
            FROM Bookings b
            JOIN Customers c ON b.customer_id = c.customer_id
            JOIN Rooms r ON b.room_id = r.room_id
            """);

        boolean hasStatusFilter = status != null && !status.trim().isEmpty() && !status.equalsIgnoreCase("all");
        if (hasStatusFilter) {
            sql.append(" WHERE b.status = ?");
        }
        sql.append(" ORDER BY b.booking_id DESC");

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            if (hasStatusFilter) {
                stmt.setString(1, status.trim());
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("bookingId", rs.getInt("booking_id"));
                    row.put("customerId", rs.getInt("customer_id"));
                    row.put("customerName", rs.getString("customer_name"));
                    row.put("phone", rs.getString("phone"));
                    row.put("cccd", rs.getString("cccd"));
                    row.put("roomId", rs.getInt("room_id"));
                    row.put("roomCode", rs.getString("room_code"));
                    row.put("roomName", rs.getString("room_name"));
                    row.put("roomType", rs.getString("room_type"));
                    row.put("price", rs.getLong("price"));
                    row.put("checkinDate", rs.getString("checkin_date"));
                    row.put("checkoutDate", rs.getString("checkout_date"));
                    row.put("status", rs.getString("status"));
                    row.put("note", rs.getString("note"));
                    row.put("createdAt", rs.getString("created_at"));
                    result.add(row);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;
    }

    // POST /api/bookings
    // Body: { "customerId": 1, "roomId": 2, "checkinDate": "2026-09-15", "checkoutDate": "2026-09-18", "note": "..." }
    @PostMapping("/bookings")
    public Map<String, Object> createBooking(@RequestBody Map<String, Object> body) {
        int customerId = parseInt(body.get("customerId"), 0);
        int roomId = parseInt(body.get("roomId"), 0);
        String checkinDate = body.get("checkinDate") != null ? body.get("checkinDate").toString().trim() : "";
        String checkoutDate = body.get("checkoutDate") != null ? body.get("checkoutDate").toString().trim() : "";
        String note = body.get("note") != null ? body.get("note").toString().trim() : "";

        if (customerId <= 0 || roomId <= 0 || checkinDate.isEmpty() || checkoutDate.isEmpty()) {
            return Map.of("success", false, "message", "Vui lòng chọn khách hàng, phòng và ngày nhận/trả phòng!");
        }

        try {
            LocalDate cin = LocalDate.parse(checkinDate.substring(0, 10));
            LocalDate cout = LocalDate.parse(checkoutDate.substring(0, 10));
            if (!cout.isAfter(cin)) {
                return Map.of("success", false, "message", "Ngày trả phòng phải sau ngày nhận phòng!");
            }
        } catch (Exception e) {
            return Map.of("success", false, "message", "Định dạng ngày không hợp lệ (cần YYYY-MM-DD)!");
        }

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);

            // 1. Kiểm tra phòng có trạng thái 'Trống' không
            String checkRoomSql = "SELECT status FROM Rooms WHERE room_id = ?";
            try (PreparedStatement checkStmt = conn.prepareStatement(checkRoomSql)) {
                checkStmt.setInt(1, roomId);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (!rs.next()) {
                        conn.rollback();
                        return Map.of("success", false, "message", "Phòng không tồn tại!");
                    }
                    String currentStatus = rs.getString("status");
                    if (!"Trống".equalsIgnoreCase(currentStatus)) {
                        conn.rollback();
                        return Map.of("success", false, "message", "Phòng này hiện không còn trống (Trạng thái: " + currentStatus + ")!");
                    }
                }
            }

            // 2. Thêm mới Booking
            int newBookingId = 0;
            String insertBookingSql = "INSERT INTO Bookings (customer_id, room_id, checkin_date, checkout_date, status, note) VALUES (?, ?, ?, ?, 'Confirmed', ?)";
            try (PreparedStatement insertStmt = conn.prepareStatement(insertBookingSql, Statement.RETURN_GENERATED_KEYS)) {
                insertStmt.setInt(1, customerId);
                insertStmt.setInt(2, roomId);
                insertStmt.setString(3, checkinDate);
                insertStmt.setString(4, checkoutDate);
                insertStmt.setString(5, note);
                insertStmt.executeUpdate();

                try (ResultSet generatedKeys = insertStmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        newBookingId = generatedKeys.getInt(1);
                    }
                }
            }

            // 3. Cập nhật trạng thái phòng thành 'Đã đặt'
            String updateRoomSql = "UPDATE Rooms SET status = N'Đã đặt' WHERE room_id = ?";
            try (PreparedStatement updateStmt = conn.prepareStatement(updateRoomSql)) {
                updateStmt.setInt(1, roomId);
                updateStmt.executeUpdate();
            }

            conn.commit();
            return Map.of("success", true, "message", "Tạo đặt phòng thành công!", "bookingId", newBookingId);

        } catch (SQLException e) {
            e.printStackTrace();
            return Map.of("success", false, "message", "Lỗi tạo đặt phòng: " + e.getMessage());
        }
    }

    // PUT /api/checkin/{bookingId}
    @PutMapping("/checkin/{bookingId}")
    public Map<String, Object> checkIn(@PathVariable int bookingId) {
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);

            // Kiểm tra booking và lấy room_id
            String findBookingSql = "SELECT room_id, status FROM Bookings WHERE booking_id = ?";
            int roomId = 0;
            try (PreparedStatement findStmt = conn.prepareStatement(findBookingSql)) {
                findStmt.setInt(1, bookingId);
                try (ResultSet rs = findStmt.executeQuery()) {
                    if (!rs.next()) {
                        conn.rollback();
                        return Map.of("success", false, "message", "Không tìm thấy thông tin đặt phòng!");
                    }
                    roomId = rs.getInt("room_id");
                }
            }

            // 1. Cập nhật booking sang CheckedIn
            String updateBookingSql = "UPDATE Bookings SET status = 'CheckedIn' WHERE booking_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(updateBookingSql)) {
                stmt.setInt(1, bookingId);
                stmt.executeUpdate();
            }

            // 2. Cập nhật trạng thái phòng sang 'Đang sử dụng'
            String updateRoomSql = "UPDATE Rooms SET status = N'Đang sử dụng' WHERE room_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(updateRoomSql)) {
                stmt.setInt(1, roomId);
                stmt.executeUpdate();
            }

            conn.commit();
            return Map.of("success", true, "message", "Check-in thành công!");

        } catch (SQLException e) {
            e.printStackTrace();
            return Map.of("success", false, "message", "Lỗi Check-in: " + e.getMessage());
        }
    }

    // GET /api/checkout/preview/{bookingId}
    // Xem trước thông tin hóa đơn trước khi xác nhận thanh toán
    @GetMapping("/checkout/preview/{bookingId}")
    public Map<String, Object> previewCheckout(@PathVariable int bookingId) {
        try (Connection conn = DBConnection.getConnection()) {
            String sql = """
                SELECT b.booking_id, b.checkin_date, b.checkout_date, b.room_id, b.status,
                       c.full_name AS customer_name,
                       r.room_code, r.room_name, r.price
                FROM Bookings b
                JOIN Customers c ON b.customer_id = c.customer_id
                JOIN Rooms r ON b.room_id = r.room_id
                WHERE b.booking_id = ?
                """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, bookingId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        return Map.of("success", false, "message", "Không tìm thấy thông tin đặt phòng!");
                    }

                    long price = rs.getLong("price");
                    String checkinDate = rs.getString("checkin_date");
                    String checkoutDate = rs.getString("checkout_date");

                    LocalDate cin = LocalDate.parse(checkinDate.substring(0, 10));
                    LocalDate cout = LocalDate.parse(checkoutDate.substring(0, 10));
                    long nights = ChronoUnit.DAYS.between(cin, cout);
                    if (nights <= 0) nights = 1;

                    long roomTotal = price * nights;

                    // Tiền dịch vụ
                    long serviceTotal = 0;
                    List<Map<String, Object>> services = new ArrayList<>();
                    String svcSql = """
                        SELECT s.service_name, bs.quantity, s.unit_price, bs.total_price
                        FROM Booking_Services bs
                        JOIN Services s ON bs.service_id = s.service_id
                        WHERE bs.booking_id = ?
                        """;
                    try (PreparedStatement svcStmt = conn.prepareStatement(svcSql)) {
                        svcStmt.setInt(1, bookingId);
                        try (ResultSet svcRs = svcStmt.executeQuery()) {
                            while (svcRs.next()) {
                                Map<String, Object> svcRow = new LinkedHashMap<>();
                                svcRow.put("serviceName", svcRs.getString("service_name"));
                                svcRow.put("quantity", svcRs.getInt("quantity"));
                                svcRow.put("unitPrice", svcRs.getLong("unit_price"));
                                svcRow.put("totalPrice", svcRs.getLong("total_price"));
                                services.add(svcRow);
                                serviceTotal += svcRs.getLong("total_price");
                            }
                        }
                    }

                    long grandTotal = roomTotal + serviceTotal;

                    Map<String, Object> res = new LinkedHashMap<>();
                    res.put("success", true);
                    res.put("bookingId", bookingId);
                    res.put("customerName", rs.getString("customer_name"));
                    res.put("roomCode", rs.getString("room_code"));
                    res.put("roomName", rs.getString("room_name"));
                    res.put("checkinDate", checkinDate);
                    res.put("checkoutDate", checkoutDate);
                    res.put("nights", nights);
                    res.put("pricePerNight", price);
                    res.put("roomTotal", roomTotal);
                    res.put("serviceTotal", serviceTotal);
                    res.put("grandTotal", grandTotal);
                    res.put("services", services);
                    return res;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return Map.of("success", false, "message", "Lỗi: " + e.getMessage());
        }
    }

    // POST /api/checkout/{bookingId}
    // Body: { "paymentMethod": "Tiền mặt" }
    @PostMapping("/checkout/{bookingId}")
    public Map<String, Object> checkOut(@PathVariable int bookingId, @RequestBody(required = false) Map<String, Object> body) {
        String paymentMethod = "Tiền mặt";
        if (body != null && body.get("paymentMethod") != null && !body.get("paymentMethod").toString().trim().isEmpty()) {
            paymentMethod = body.get("paymentMethod").toString().trim();
        }

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);

            // 1. Lấy thông tin booking & phòng
            String infoSql = """
                SELECT b.checkin_date, b.checkout_date, b.room_id, r.price, r.room_code, r.room_name,
                       c.full_name AS customer_name
                FROM Bookings b
                JOIN Rooms r ON b.room_id = r.room_id
                JOIN Customers c ON b.customer_id = c.customer_id
                WHERE b.booking_id = ?
                """;

            int roomId = 0;
            long price = 0;
            String checkinDate = "";
            String checkoutDate = "";
            String customerName = "";
            String roomCode = "";

            try (PreparedStatement infoStmt = conn.prepareStatement(infoSql)) {
                infoStmt.setInt(1, bookingId);
                try (ResultSet rs = infoStmt.executeQuery()) {
                    if (!rs.next()) {
                        conn.rollback();
                        return Map.of("success", false, "message", "Không tìm thấy booking!");
                    }
                    roomId = rs.getInt("room_id");
                    price = rs.getLong("price");
                    checkinDate = rs.getString("checkin_date");
                    checkoutDate = rs.getString("checkout_date");
                    customerName = rs.getString("customer_name");
                    roomCode = rs.getString("room_code");
                }
            }

            // 2. Tính số đêm và tiền phòng
            LocalDate cin = LocalDate.parse(checkinDate.substring(0, 10));
            LocalDate cout = LocalDate.parse(checkoutDate.substring(0, 10));
            long nights = ChronoUnit.DAYS.between(cin, cout);
            if (nights <= 0) nights = 1;
            long roomTotal = price * nights;

            // 3. Tính tiền dịch vụ
            long serviceTotal = 0;
            String svcSql = "SELECT ISNULL(SUM(total_price), 0) AS svc FROM Booking_Services WHERE booking_id = ?";
            try (PreparedStatement svcStmt = conn.prepareStatement(svcSql)) {
                svcStmt.setInt(1, bookingId);
                try (ResultSet svcRs = svcStmt.executeQuery()) {
                    if (svcRs.next()) {
                        serviceTotal = svcRs.getLong("svc");
                    }
                }
            }

            long grandTotal = roomTotal + serviceTotal;

            // 4. Tạo hóa đơn trong bảng Payments
            int paymentId = 0;
            String paymentSql = """
                INSERT INTO Payments (booking_id, room_total, service_total, grand_total, payment_method, payment_status, paid_at)
                VALUES (?, ?, ?, ?, ?, N'Đã thanh toán', GETDATE())
                """;
            try (PreparedStatement payStmt = conn.prepareStatement(paymentSql, Statement.RETURN_GENERATED_KEYS)) {
                payStmt.setInt(1, bookingId);
                payStmt.setLong(2, roomTotal);
                payStmt.setLong(3, serviceTotal);
                payStmt.setLong(4, grandTotal);
                payStmt.setString(5, paymentMethod);
                payStmt.executeUpdate();

                try (ResultSet pks = payStmt.getGeneratedKeys()) {
                    if (pks.next()) {
                        paymentId = pks.getInt(1);
                    }
                }
            }

            // 5. Cập nhật trạng thái Booking và Room
            String updateBookingSql = "UPDATE Bookings SET status = 'CheckedOut' WHERE booking_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(updateBookingSql)) {
                stmt.setInt(1, bookingId);
                stmt.executeUpdate();
            }

            String updateRoomSql = "UPDATE Rooms SET status = N'Trống' WHERE room_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(updateRoomSql)) {
                stmt.setInt(1, roomId);
                stmt.executeUpdate();
            }

            conn.commit();

            Map<String, Object> invoice = new LinkedHashMap<>();
            invoice.put("paymentId", paymentId);
            invoice.put("bookingId", bookingId);
            invoice.put("customerName", customerName);
            invoice.put("roomCode", roomCode);
            invoice.put("nights", nights);
            invoice.put("pricePerNight", price);
            invoice.put("roomTotal", roomTotal);
            invoice.put("serviceTotal", serviceTotal);
            invoice.put("grandTotal", grandTotal);
            invoice.put("paymentMethod", paymentMethod);

            Map<String, Object> res = new LinkedHashMap<>();
            res.put("success", true);
            res.put("message", "Check-out và thanh toán thành công!");
            res.put("invoice", invoice);
            return res;

        } catch (Exception e) {
            e.printStackTrace();
            return Map.of("success", false, "message", "Lỗi thanh toán & check-out: " + e.getMessage());
        }
    }
}