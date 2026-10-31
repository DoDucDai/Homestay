package com.homestay.controller;

import com.homestay.db.DBConnection;
import com.homestay.api.*;
import org.springframework.web.bind.annotation.*;
import java.sql.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api")

public class BookingController {

    private final java.time.Clock clock;

    public BookingController() { this(RoomSchedule.CLOCK); }
    BookingController(java.time.Clock clock) { this.clock = clock; }

    private Map<String, Object> inTransaction(RoomSchedule.Work work, String errorMessage) {
        return RoomSchedule.transaction(work, errorMessage);
    }

    // Always lock Rooms before Bookings; the room lock serializes operations
    // on different bookings for the same physical room as well.
    private int lockBookingRoom(Connection conn, int bookingId, String expectedStatus) throws SQLException {
        return lockBookingRoom(conn, bookingId, expectedStatus, true);
    }

    private int lockBookingRoom(Connection conn, int bookingId, String expectedStatus, boolean requireUsable) throws SQLException {
        int roomId;
        try (PreparedStatement stmt = conn.prepareStatement("SELECT room_id FROM Bookings WHERE booking_id = ?")) {
            stmt.setInt(1, bookingId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) throw ApiErrors.notFound("Không tìm thấy thông tin đặt phòng!");
                roomId = rs.getInt("room_id");
            }
        }
        RoomSchedule.LockedRoom room = RoomSchedule.lockRoom(conn, roomId);
        if (requireUsable && "Bảo trì".equals(room.status())) {
            throw new RoomSchedule.Conflict("Phòng đang bảo trì, không thể nhận hoặc trả phòng!");
        }
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT room_id, status FROM Bookings WITH (UPDLOCK, HOLDLOCK) WHERE booking_id = ?")) {
            stmt.setInt(1, bookingId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) throw ApiErrors.notFound("Không tìm thấy thông tin đặt phòng!");
                if (rs.getInt("room_id") != roomId) throw new RoomSchedule.Conflict("Phòng đã thay đổi, vui lòng tải lại!");
                if (!expectedStatus.equals(rs.getString("status"))) {
                    throw new RoomSchedule.Conflict("Thao tác yêu cầu booking ở trạng thái " + expectedStatus + "!");
                }
            }
        }
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT booking_id FROM Bookings WHERE room_id = ? AND booking_id <> ? AND status = 'CheckedIn'")) {
            stmt.setInt(1, roomId);
            stmt.setInt(2, bookingId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (requireUsable && rs.next()) throw new RoomSchedule.Conflict("Phòng đang có booking khác lưu trú!");
            }
        }
        try (PreparedStatement stmt = conn.prepareStatement("SELECT payment_id FROM Payments WHERE booking_id = ?")) {
            stmt.setInt(1, bookingId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) throw new RoomSchedule.Conflict("Booking đã có hóa đơn, không thể thực hiện lại!");
            }
        }
        return roomId;
    }

    private void transitionBooking(Connection conn, int bookingId, String from, String to) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE Bookings SET status = ? WHERE booking_id = ? AND status = ?")) {
            stmt.setString(1, to);
            stmt.setInt(2, bookingId);
            stmt.setString(3, from);
            if (stmt.executeUpdate() != 1) throw new RoomSchedule.Conflict("Trạng thái booking đã thay đổi, vui lòng tải lại!");
        }
    }

    private int parseInt(Object val, int defaultVal) {
        if (val == null) return defaultVal;
        try {
            return new BigDecimal(val.toString().trim()).intValueExact();
        } catch (Exception e) {
            return defaultVal;
        }
    }

    // GET /api/bookings?status=Confirmed
    @GetMapping("/bookings")
    public List<Map<String, Object>> getBookings(@RequestParam(required = false) String status) {
        if(status!=null && !status.isBlank() && !status.equalsIgnoreCase("all")) {
            Input.oneOf(status.trim(),Set.of("Confirmed","CheckedIn","CheckedOut","Cancelled"),"Trạng thái booking");
        }
        List<Map<String, Object>> result = new ArrayList<>();

        StringBuilder sql = new StringBuilder("""
            SELECT b.booking_id, b.customer_id, b.room_id, b.checkin_date, b.checkout_date, b.status, b.note, b.created_at,b.billing_unit,b.checkin_at,b.checkout_at,b.price_per_hour,
                   c.full_name AS customer_name, c.phone, c.cccd,
                   r.room_code, r.room_name, b.price_per_night AS price, r.room_type
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
                    row.put("billingUnit",rs.getString("billing_unit"));
                    row.put("checkinAt",rs.getTimestamp("checkin_at")==null?null:rs.getTimestamp("checkin_at").toLocalDateTime().toString());
                    row.put("checkoutAt",rs.getTimestamp("checkout_at")==null?null:rs.getTimestamp("checkout_at").toLocalDateTime().toString());
                    row.put("pricePerHour",rs.getBigDecimal("price_per_hour"));
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
            throw ApiErrors.databaseFailure(e);
        }
        return result;
    }

    @ExceptionHandler(RoomSchedule.InvalidDates.class)
    @ResponseStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
    public Map<String, Object> invalidDates(RoomSchedule.InvalidDates error) {
        return ApiErrors.guard(() -> {
            return ApiErrors.from(error);


        });
    }

    @GetMapping("/bookings/available-rooms")
    public List<Map<String, Object>> getAvailableRooms(@RequestParam String checkin, @RequestParam String checkout) {
        LocalDate today = RoomSchedule.today(clock);
        RoomSchedule.Range range = RoomSchedule.range(checkin, checkout, today);
        String sql = RoomSchedule.selectRooms() + " WHERE r.status<>N'Bảo trì' AND NOT ("
                + RoomSchedule.unavailablePredicate() + ") ORDER BY r.room_code";
        List<Map<String, Object>> rooms = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            RoomSchedule.bindToday(stmt, today);
            RoomSchedule.bindRange(stmt, 3, range, today);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) rooms.add(RoomSchedule.roomRow(rs));
            }
        } catch (SQLException e) {
            throw new RoomSchedule.QueryFailure(e);
        }
        return rooms;
    }

    @PutMapping("/bookings/{bookingId}/cancel")
    public Map<String, Object> cancelBooking(@PathVariable int bookingId) {
        return ApiErrors.guard(() -> {
            Input.id(bookingId);
            return inTransaction(conn -> {
                // A cancellation must also work when a different guest occupies the room.
                int roomId = lockBookingRoom(conn, bookingId, "Confirmed", false);
                transitionBooking(conn, bookingId, "Confirmed", "Cancelled");
                String roomStatus = RoomSchedule.refresh(conn, roomId, RoomSchedule.today(clock));
                return Map.of("success", true, "message", "Đã hủy đặt phòng!", "roomStatus", roomStatus);
            }, "Không thể hủy đặt phòng. Vui lòng tải lại để kiểm tra!");


        });
    }

    // POST /api/bookings
    // Body: { "customerId": 1, "roomId": 2, "checkinDate": "2026-09-15", "checkoutDate": "2026-09-18", "note": "..." }
    @PostMapping("/bookings")
    public Map<String, Object> createBooking(@RequestBody Map<String, Object> body) {
        return ApiErrors.guard(() -> {
            String unit=Input.oneOf(Input.text(body,"billingUnit",5,false,"Night"),Set.of("Night","Hour"),"Cách tính tiền");
            if("Hour".equals(unit)) return createHourly(body);

            int customerId = Input.integer(body,"customerId",1,Integer.MAX_VALUE);
            int roomId = Input.integer(body,"roomId",1,Integer.MAX_VALUE);
            String checkinDate = Input.text(body,"checkinDate",10,true,null);
            String checkoutDate = Input.text(body,"checkoutDate",10,true,null);
            String note = Input.text(body,"note",500,false,"");

            if (customerId <= 0 || roomId <= 0 || checkinDate.isEmpty() || checkoutDate.isEmpty()) {
                return ApiErrors.bad("Vui lòng chọn khách hàng, phòng và ngày nhận/trả phòng!");
            }

            final RoomSchedule.Range range;
            try {
                range = RoomSchedule.range(checkinDate, checkoutDate, RoomSchedule.today(clock));
            } catch (RoomSchedule.InvalidDates e) {
                return ApiErrors.from(e);
            }

            return inTransaction(conn -> {
                RoomSchedule.LockedRoom room = RoomSchedule.lockRoom(conn, roomId);
                LocalDate today = RoomSchedule.today(clock);
                RoomSchedule.requireAvailable(conn, roomId, range, today);
                BigDecimal capturedPrice = BookingInvoiceSnapshot.money(room.price());
                try (PreparedStatement stmt = conn.prepareStatement("SELECT customer_id FROM Customers WHERE customer_id=?")) {
                    stmt.setInt(1, customerId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (!rs.next()) throw ApiErrors.notFound("Khách hàng không tồn tại!");
                    }
                }

                // 2. Thêm mới Booking
                int newBookingId = 0;
                String insertBookingSql = "INSERT INTO Bookings (customer_id, room_id, checkin_date, checkout_date, status, note, price_per_night, created_at) VALUES (?, ?, ?, ?, 'Confirmed', ?, ?, ?)";
                try (PreparedStatement insertStmt = conn.prepareStatement(insertBookingSql, Statement.RETURN_GENERATED_KEYS)) {
                    insertStmt.setInt(1, customerId);
                    insertStmt.setInt(2, roomId);
                    insertStmt.setDate(3, java.sql.Date.valueOf(range.checkin()));
                    insertStmt.setDate(4, java.sql.Date.valueOf(range.checkout()));
                    insertStmt.setString(5, note);
                    insertStmt.setBigDecimal(6, capturedPrice);
                    insertStmt.setTimestamp(7,Timestamp.valueOf(java.time.LocalDateTime.now(clock.withZone(RoomSchedule.ZONE))));
                    insertStmt.executeUpdate();

                    try (ResultSet generatedKeys = insertStmt.getGeneratedKeys()) {
                        if (generatedKeys.next()) {
                            newBookingId = generatedKeys.getInt(1);
                        } else throw new SQLException("Booking key was not returned");
                    }
                }

                RoomSchedule.refresh(conn, roomId, today);

                return Map.of("success", true, "message", "Tạo đặt phòng thành công!", "bookingId", newBookingId);
            }, "Không thể tạo đặt phòng. Vui lòng thử lại!");


        });
    }

    private Map<String,Object> createHourly(Map<String,Object> body) {
        int customerId=Input.integer(body,"customerId",1,Integer.MAX_VALUE);
        int roomId=Input.integer(body,"roomId",1,Integer.MAX_VALUE);
        String note=Input.text(body,"note",500,false,"");
        var range=HourlyStay.range(Input.text(body,"checkinAt",16,true,null),Input.text(body,"checkoutAt",16,true,null),clock);
        return inTransaction(conn -> {
            RoomSchedule.lockRoom(conn,roomId);
            java.math.BigDecimal rate;
            try(var stmt=conn.prepareStatement("SELECT hourly_price,status FROM Rooms WHERE room_id=?")) {
                stmt.setInt(1,roomId);
                try(var rs=stmt.executeQuery()) {
                    if(!rs.next()) throw ApiErrors.notFound("Không tìm thấy phòng!");
                    if("Bảo trì".equals(rs.getString("status"))) throw new RoomSchedule.Conflict("Phòng đang bảo trì!");
                    rate=rs.getBigDecimal("hourly_price");
                    if(rate==null || rate.signum()<=0) throw new RoomSchedule.Conflict("Phòng chưa khai báo giá giờ. Hãy nhờ quản lý nhập giá trước!");
                    rate=BookingInvoiceSnapshot.money(rate);
                    BookingInvoiceSnapshot.money(rate.multiply(java.math.BigDecimal.valueOf(range.hours())));
                }
            }
            try(var stmt=conn.prepareStatement("""
                SELECT booking_id FROM Bookings WHERE room_id=? AND
                ((status IN ('Confirmed','CheckedIn') AND COALESCE(checkin_at,CAST(checkin_date AS datetime2))<?
                    AND COALESCE(checkout_at,CAST(checkout_date AS datetime2))>?)
                 OR (status='CheckedIn' AND ?<=?))
                """)) {
                stmt.setInt(1,roomId);stmt.setTimestamp(2,Timestamp.valueOf(range.end()));stmt.setTimestamp(3,Timestamp.valueOf(range.start()));
                stmt.setTimestamp(4,Timestamp.valueOf(range.start()));stmt.setTimestamp(5,Timestamp.valueOf(HourlyStay.now(clock)));
                try(var rs=stmt.executeQuery()) { if(rs.next()) throw new RoomSchedule.Conflict("Phòng đã có khách hoặc trùng lịch trong khung giờ này!"); }
            }
            try(var stmt=conn.prepareStatement("SELECT customer_id FROM Customers WHERE customer_id=?")) {
                stmt.setInt(1,customerId);try(var rs=stmt.executeQuery()){if(!rs.next())throw ApiErrors.notFound("Khách hàng không tồn tại!");}
            }
            // DATE columns remain a valid envelope for old reports; scheduling uses exact timestamps.
            var endDate=range.end().toLocalDate();
            if(!endDate.isAfter(range.start().toLocalDate())) endDate=range.start().toLocalDate().plusDays(1);
            try(var stmt=conn.prepareStatement("""
                INSERT Bookings(customer_id,room_id,checkin_date,checkout_date,status,note,price_per_night,
                    billing_unit,checkin_at,checkout_at,price_per_hour,created_at)
                VALUES(?,?,?,?,'Confirmed',?,0,'Hour',?,?,?,?)
                """,Statement.RETURN_GENERATED_KEYS)) {
                stmt.setInt(1,customerId);stmt.setInt(2,roomId);stmt.setDate(3,java.sql.Date.valueOf(range.start().toLocalDate()));
                stmt.setDate(4,java.sql.Date.valueOf(endDate));stmt.setString(5,note);stmt.setTimestamp(6,Timestamp.valueOf(range.start()));
                stmt.setTimestamp(7,Timestamp.valueOf(range.end()));stmt.setBigDecimal(8,rate);stmt.setTimestamp(9,Timestamp.valueOf(HourlyStay.now(clock)));
                stmt.executeUpdate();try(var rs=stmt.getGeneratedKeys()) {
                    if(!rs.next())throw new SQLException("Missing booking key");
                    return Map.of("success",true,"bookingId",rs.getInt(1),"message","Đã đặt phòng theo giờ!","billedHours",range.hours());
                }
            }
        },"Không thể đặt phòng theo giờ. Hãy tải lại lịch phòng!");
    }

    // PUT /api/checkin/{bookingId}
    @PutMapping("/checkin/{bookingId}")
    public Map<String, Object> checkIn(@PathVariable int bookingId) {
        return ApiErrors.guard(() -> {
            Input.id(bookingId);
            return inTransaction(conn -> {
                int roomId = lockBookingRoom(conn, bookingId, "Confirmed");
                LocalDate today = RoomSchedule.today(clock);
                try (PreparedStatement stmt = conn.prepareStatement("SELECT checkin_date,checkout_date,billing_unit,checkin_at,checkout_at FROM Bookings WHERE booking_id=?")) {
                    stmt.setInt(1, bookingId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (!rs.next()) throw ApiErrors.notFound("Không tìm thấy booking!");
                        if ("Hour".equals(rs.getString("billing_unit"))) {
                            var now=HourlyStay.now(clock);
                            if(now.isBefore(rs.getTimestamp("checkin_at").toLocalDateTime()) || !now.isBefore(rs.getTimestamp("checkout_at").toLocalDateTime()))
                                throw new RoomSchedule.Conflict("Chỉ nhận phòng trong khung giờ đã đặt!");
                        } else if (today.isBefore(rs.getDate("checkin_date").toLocalDate())
                                || !today.isBefore(rs.getDate("checkout_date").toLocalDate())) {
                            throw new RoomSchedule.Conflict("Chỉ nhận phòng từ ngày nhận dự kiến đến trước ngày trả dự kiến!");
                        }
                    }
                }
                transitionBooking(conn, bookingId, "Confirmed", "CheckedIn");
                RoomSchedule.refresh(conn, roomId, today);

                return Map.of("success", true, "message", "Check-in thành công!");
            }, "Không thể Check-in. Vui lòng thử lại!");


        });
    }

    // Each order is a separate captured-price line, serialized with checkout.
    @PostMapping("/bookings/{bookingId}/services")
    public Map<String, Object> addServiceToBooking(@PathVariable int bookingId, @RequestBody(required = false) Map<String, Object> body) {
        return ApiErrors.guard(() -> {
            Input.id(bookingId);
            final int serviceId=Input.integer(body,"serviceId",1,Integer.MAX_VALUE);
            final int quantity=Input.integer(body,"quantity",1,10000);
            return inTransaction(conn -> {
                lockBookingRoom(conn, bookingId, "CheckedIn");
                String serviceName;
                BigDecimal unitPrice;
                try (PreparedStatement stmt = conn.prepareStatement(
                        "SELECT service_name,unit_price,status FROM Services WITH (HOLDLOCK) WHERE service_id=?")) {
                    stmt.setInt(1, serviceId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (!rs.next()) throw ApiErrors.notFound("Không tìm thấy dịch vụ!");
                        if (!"Hoạt động".equals(rs.getString("status"))) throw new RoomSchedule.Conflict("Dịch vụ đã ngừng hoạt động!");
                        serviceName = rs.getString("service_name");
                        unitPrice = BookingInvoiceSnapshot.money(rs.getBigDecimal("unit_price"));
                    }
                }
                BigDecimal total = BookingInvoiceSnapshot.money(unitPrice.multiply(BigDecimal.valueOf(quantity)));
                // Validate cumulative totals too, so several valid lines cannot make
                // the final invoice exceed the Payments columns' supported range.
                Map<String, Object> currentInvoice = BookingInvoiceSnapshot.build(conn, bookingId);
                BookingInvoiceSnapshot.money(((BigDecimal) currentInvoice.get("serviceTotal")).add(total));
                BookingInvoiceSnapshot.money(((BigDecimal) currentInvoice.get("grandTotal")).add(total));
                try (PreparedStatement stmt = conn.prepareStatement("""
                        INSERT INTO Booking_Services(booking_id,service_id,quantity,total_price,
                            service_name_snapshot,unit_price_snapshot) VALUES(?,?,?,?,?,?)
                        """, Statement.RETURN_GENERATED_KEYS)) {
                    stmt.setInt(1, bookingId);
                    stmt.setInt(2, serviceId);
                    stmt.setInt(3, quantity);
                    stmt.setBigDecimal(4, total);
                    stmt.setString(5, serviceName);
                    stmt.setBigDecimal(6, unitPrice);
                    stmt.executeUpdate();
                    try (ResultSet rs = stmt.getGeneratedKeys()) {
                        if (!rs.next()) throw new SQLException("Service key was not returned");
                        return Map.of("success", true, "message", "Đã thêm dịch vụ!", "bookingServiceId", rs.getInt(1),
                                "serviceName", serviceName, "quantity", quantity, "unitPrice", unitPrice, "totalPrice", total);
                    }
                }
            }, "Không thể thêm dịch vụ. Vui lòng tải lại để kiểm tra!");


        });
    }

    @DeleteMapping("/bookings/services/{bookingServiceId}")
    public Map<String, Object> removeServiceFromBooking(@PathVariable int bookingServiceId) {
        return ApiErrors.guard(() -> {
            Input.id(bookingServiceId);
            return inTransaction(conn -> {
                int bookingId;
                // Resolve the owner without retaining a service-row lock. All writes
                // take Rooms -> Bookings -> Booking_Services, like add and checkout.
                try (PreparedStatement stmt = conn.prepareStatement("SELECT booking_id FROM Booking_Services WHERE id=?")) {
                    stmt.setInt(1, bookingServiceId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (!rs.next()) throw ApiErrors.notFound("Không tìm thấy dòng dịch vụ hoặc dòng đã được xóa!");
                        bookingId = rs.getInt("booking_id");
                    }
                }
                lockBookingRoom(conn, bookingId, "CheckedIn");
                try (PreparedStatement stmt = conn.prepareStatement(
                        "SELECT booking_id FROM Booking_Services WITH (UPDLOCK,HOLDLOCK) WHERE id=?")) {
                    stmt.setInt(1, bookingServiceId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (!rs.next()) throw ApiErrors.notFound("Dòng dịch vụ đã được xóa. Vui lòng tải lại!");
                        if (rs.getInt("booking_id") != bookingId) {
                            throw new RoomSchedule.Conflict("Thông tin dòng dịch vụ đã thay đổi. Vui lòng tải lại!");
                        }
                    }
                }
                try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM Booking_Services WHERE id=? AND booking_id=?")) {
                    stmt.setInt(1, bookingServiceId);
                    stmt.setInt(2, bookingId);
                    if (stmt.executeUpdate() != 1) throw new RoomSchedule.Conflict("Không thể xóa dòng dịch vụ đã thay đổi!");
                }
                return Map.of("success", true, "message", "Đã xóa dịch vụ gọi nhầm!",
                        "bookingServiceId", bookingServiceId, "bookingId", bookingId);
            }, "Không thể xóa dịch vụ. Vui lòng tải lại để kiểm tra!");


        });
    }

    @GetMapping("/checkout/preview/{bookingId}")
    public Map<String, Object> previewCheckout(@PathVariable int bookingId) {
        return ApiErrors.guard(() -> {
            Input.id(bookingId);
            return inTransaction(conn -> {
                lockBookingRoom(conn, bookingId, "CheckedIn");
                Map<String, Object> preview = BookingInvoiceSnapshot.build(conn, bookingId);
                preview.put("success", true);
                return preview;
            }, "Không thể tải hóa đơn tạm tính. Vui lòng liên hệ người quản lý để kiểm tra dữ liệu giá!");


        });
    }

    @PostMapping("/checkout/{bookingId}")
    public Map<String, Object> checkOut(@PathVariable int bookingId, @RequestBody(required = false) Map<String, Object> body) {
        return ApiErrors.guard(() -> {
            Input.id(bookingId);
            String paymentMethod=Input.oneOf(Input.text(body==null?Map.of():body,"paymentMethod",50,false,"Tiền mặt"),
                    Set.of("Tiền mặt","Chuyển khoản","Thẻ ngân hàng"),"Phương thức thanh toán");
            final String selectedPaymentMethod = paymentMethod;
            return inTransaction(conn -> {
                int roomId = lockBookingRoom(conn, bookingId, "CheckedIn");
                Map<String, Object> invoice = BookingInvoiceSnapshot.build(conn, bookingId);
                int paymentId = BookingInvoiceSnapshot.store(conn, invoice, selectedPaymentMethod,java.time.LocalDateTime.now(clock.withZone(RoomSchedule.ZONE)));
                transitionBooking(conn, bookingId, "CheckedIn", "CheckedOut");
                RoomSchedule.refresh(conn, roomId, RoomSchedule.today(clock));
                return Map.of("success", true, "message", "Check-out và thanh toán thành công!",
                        "paymentId", paymentId, "invoice", invoice);
            }, "Không thể thanh toán và Check-out. Vui lòng tải lại để kiểm tra trạng thái booking!");


        });
    }

    @GetMapping("/payments")
    public List<Map<String,Object>> getPayments(@RequestParam(required=false) Integer bookingId) {
        if (bookingId != null) Input.id(bookingId);
        List<Map<String,Object>> result = new ArrayList<>();
        String sql = "SELECT payment_id,booking_id,paid_at,payment_method,grand_total FROM Payments WHERE payment_status=N'Đã thanh toán'"
                + (bookingId == null ? "" : " AND booking_id=?") + " ORDER BY payment_id DESC";
        try (Connection conn = DBConnection.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            if (bookingId != null) stmt.setInt(1,bookingId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) result.add(Map.of("paymentId",rs.getInt("payment_id"),"bookingId",rs.getInt("booking_id"),
                    "paidAt",rs.getTimestamp("paid_at").toLocalDateTime().toString(),"paymentMethod",rs.getString("payment_method"),
                    "grandTotal",rs.getBigDecimal("grand_total")));
            }
        } catch (SQLException e) { throw ApiErrors.databaseFailure(e); }
        return result;
    }

    @GetMapping("/payments/{paymentId}")
    public Map<String, Object> getPayment(@PathVariable int paymentId) {
        return ApiErrors.guard(() -> {
            Input.id(paymentId);
            try (Connection conn = DBConnection.getConnection()) {
                return BookingInvoiceSnapshot.read(conn, paymentId);
            } catch (SQLException e) {
                return ApiErrors.database(e);
            }


        });
    }
}
