package com.homestay.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.homestay.api.*;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Builds invoices from captured prices; reprints read only the stored JSON. */
final class BookingInvoiceSnapshot {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    private static final BigDecimal MAX_MONEY = new BigDecimal("999999999999");

    private BookingInvoiceSnapshot() {}

    static BigDecimal money(BigDecimal value) throws SQLException {
        if (value == null) throw new ApiProblem(409,"DATA_INCONSISTENT","Dữ liệu giá đã lưu chưa đầy đủ. Vui lòng liên hệ người quản lý!");
        try {
            BigDecimal rounded = value.setScale(0, RoundingMode.UNNECESSARY);
            if (rounded.signum() < 0 || rounded.compareTo(MAX_MONEY) > 0) {
                throw new ApiProblem(400,"AMOUNT_OUT_OF_RANGE","Tổng tiền vượt giới hạn lưu trữ hoặc có giá trị âm!");
            }
            return rounded;
        } catch (ArithmeticException e) {
            throw new ApiProblem(409,"DATA_INCONSISTENT","Dữ liệu tiền VND phải là số nguyên đồng!");
        }
    }

    static Map<String, Object> build(Connection conn, int bookingId) throws SQLException {
        Map<String, Object> invoice = new LinkedHashMap<>();
        BigDecimal price;
        long nights;
        boolean estimated;
        String query = """
                SELECT b.checkin_date,b.checkout_date,b.price_per_night,b.snapshot_estimated,
                       c.full_name,c.cccd,c.phone,c.address,r.room_code,r.room_name
                FROM Bookings b JOIN Customers c ON c.customer_id=b.customer_id
                JOIN Rooms r ON r.room_id=b.room_id WHERE b.booking_id=?
                """;
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, bookingId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) throw ApiErrors.notFound("Không tìm thấy dữ liệu hóa đơn booking!");
                price = money(rs.getBigDecimal("price_per_night"));
                nights = ChronoUnit.DAYS.between(rs.getDate("checkin_date").toLocalDate(),
                        rs.getDate("checkout_date").toLocalDate());
                if (nights <= 0) throw new ApiProblem(409,"DATA_INCONSISTENT","Ngày lưu trú đã lưu không hợp lệ!");
                estimated = rs.getBoolean("snapshot_estimated");
                invoice.put("bookingId", bookingId);
                invoice.put("customerName", rs.getString("full_name"));
                invoice.put("customerCccd", rs.getString("cccd"));
                invoice.put("customerPhone", rs.getString("phone"));
                invoice.put("customerAddress", rs.getString("address"));
                invoice.put("roomCode", rs.getString("room_code"));
                invoice.put("roomName", rs.getString("room_name"));
                invoice.put("checkinDate", rs.getDate("checkin_date").toString());
                invoice.put("checkoutDate", rs.getDate("checkout_date").toString());
            }
        }
        List<Map<String, Object>> services = new ArrayList<>();
        BigDecimal serviceTotal = BigDecimal.ZERO;
        boolean reconciled = true;
        try (PreparedStatement stmt = conn.prepareStatement("""
                SELECT id,service_name_snapshot,unit_price_snapshot,quantity,total_price,snapshot_estimated
                FROM Booking_Services WHERE booking_id=? ORDER BY id
                """)) {
            stmt.setInt(1, bookingId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    BigDecimal unitPrice = rs.getBigDecimal("unit_price_snapshot");
                    BigDecimal total = money(rs.getBigDecimal("total_price"));
                    int quantity = rs.getInt("quantity");
                    if (unitPrice == null || unitPrice.signum() < 0 || quantity <= 0) {
                        throw new ApiProblem(409,"DATA_INCONSISTENT","Giá hoặc số lượng dịch vụ đã lưu không hợp lệ!");
                    }
                    reconciled &= unitPrice.multiply(BigDecimal.valueOf(quantity)).compareTo(total) == 0;
                    boolean serviceEstimated = rs.getBoolean("snapshot_estimated");
                    estimated |= serviceEstimated;
                    Map<String, Object> service = new LinkedHashMap<>();
                    service.put("bookingServiceId", rs.getInt("id"));
                    service.put("serviceName", rs.getString("service_name_snapshot"));
                    service.put("quantity", quantity);
                    service.put("unitPrice", unitPrice.stripTrailingZeros());
                    service.put("totalPrice", total);
                    service.put("snapshotEstimated", serviceEstimated);
                    services.add(service);
                    serviceTotal = serviceTotal.add(total);
                }
            }
        }
        BigDecimal roomTotal = money(price.multiply(BigDecimal.valueOf(nights)));
        invoice.put("nights", nights);
        invoice.put("pricePerNight", price);
        invoice.put("roomTotal", roomTotal);
        invoice.put("serviceTotal", money(serviceTotal));
        invoice.put("grandTotal", money(roomTotal.add(serviceTotal)));
        invoice.put("services", services);
        invoice.put("snapshotEstimated", estimated);
        invoice.put("reconciliationWarning", reconciled ? null
                : "Đơn giá dịch vụ dựng lại không khớp tuyệt đối; thành tiền đã lưu được giữ nguyên.");
        return invoice;
    }

    static int store(Connection conn, Map<String, Object> invoice, String paymentMethod) throws SQLException {
        return store(conn, invoice, paymentMethod, null);
    }

    // Manual imports can preserve paid_at; normal checkout keeps the SQL server timestamp.
    static int store(Connection conn, Map<String, Object> invoice, String paymentMethod,
                     java.time.LocalDateTime paidAt) throws SQLException {
        int paymentId;
        String insert = """
                INSERT INTO Payments(booking_id,room_total,service_total,grand_total,payment_method,
                    payment_status,paid_at,snapshot_estimated,reconciliation_warning)
                VALUES(?,?,?,?,?,N'Đã thanh toán',COALESCE(?,GETDATE()),?,?)
                """;
        try (PreparedStatement stmt = conn.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, ((Number) invoice.get("bookingId")).intValue());
            stmt.setBigDecimal(2, (BigDecimal) invoice.get("roomTotal"));
            stmt.setBigDecimal(3, (BigDecimal) invoice.get("serviceTotal"));
            stmt.setBigDecimal(4, (BigDecimal) invoice.get("grandTotal"));
            stmt.setString(5, paymentMethod);
            stmt.setTimestamp(6, paidAt == null ? null : Timestamp.valueOf(paidAt));
            stmt.setBoolean(7, Boolean.TRUE.equals(invoice.get("snapshotEstimated")));
            stmt.setString(8, (String) invoice.get("reconciliationWarning"));
            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (!rs.next()) throw new SQLException("Payment key was not returned");
                paymentId = rs.getInt(1);
            }
        }
        invoice.put("paymentId", paymentId);
        invoice.put("paymentMethod", paymentMethod);
        invoice.put("paymentStatus", "Đã thanh toán");
        try (PreparedStatement stmt = conn.prepareStatement("SELECT paid_at FROM Payments WHERE payment_id=?")) {
            stmt.setInt(1, paymentId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) throw new SQLException("Payment not found after insert");
                invoice.put("paidAt", rs.getTimestamp(1).toLocalDateTime().toString());
            }
        }
        try (PreparedStatement stmt = conn.prepareStatement("UPDATE Payments SET invoice_snapshot=? WHERE payment_id=?")) {
            try {
                stmt.setString(1, JSON.writeValueAsString(invoice));
            } catch (Exception e) {
                throw new SQLException("Cannot serialize invoice snapshot", e);
            }
            stmt.setInt(2, paymentId);
            if (stmt.executeUpdate() != 1) throw new SQLException("Cannot save invoice snapshot");
        }
        return paymentId;
    }

    static Map<String, Object> read(Connection conn, int paymentId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("SELECT invoice_snapshot FROM Payments WHERE payment_id=?")) {
            stmt.setInt(1, paymentId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) return ApiErrors.missing("Không tìm thấy hóa đơn!");
                String snapshot = rs.getString(1);
                if (snapshot == null) return ApiErrors.conflict("Hóa đơn chưa có bản lưu. Vui lòng liên hệ người quản lý!");
                try {
                    Map<String, Object> invoice = JSON.readValue(snapshot, new TypeReference<Map<String, Object>>() {});
                    return Map.of("success", true, "invoice", invoice);
                } catch (Exception e) {
                    return ApiErrors.unexpected(e);
                }
            }
        }
    }
}
