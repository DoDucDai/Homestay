package com.homestay.controller;

import com.homestay.api.*;
import com.homestay.security.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

/** Admin receipt confirmation is independent of arrival/departure. */
@RestController
@RequestMapping("/api/admin/booking-payments")
public class BookingPaymentAdminController {
    private final int reviewWarningMinutes;
    public BookingPaymentAdminController(@Value("${booking.transfer-review-warning-minutes:30}") int minutes) {
        if(minutes<1 || minutes>10080)throw new IllegalArgumentException("Review warning must be 1–10080 minutes");
        reviewWarningMinutes=minutes;
    }
    private void admin(AppUser user){if(user==null || !"admin".equals(user.role()))throw new ApiProblem(403,"FORBIDDEN","Chỉ quản trị viên được xác nhận thu tiền.");}
    @GetMapping
    public List<Map<String,Object>> queue(@AuthenticationPrincipal AppUser user,@RequestParam(required=false) Integer propertyId){
        admin(user);if(propertyId!=null)Input.id(propertyId);
        return AccountStore.transaction(connection->{
            List<Map<String,Object>> result=new ArrayList<>();
            String sql="SELECT b.booking_id,b.status,b.selected_payment_method,b.payment_review_status,b.transfer_reported_at,"
                +"DATEDIFF_BIG(SECOND,b.transfer_reported_at,SYSUTCDATETIME()) AS waiting_seconds,r.room_code,c.full_name "
                +"FROM Bookings b JOIN Rooms r ON r.room_id=b.room_id JOIN Customers c ON c.customer_id=b.customer_id "
                +"WHERE b.status IN('Confirmed','CheckedIn') AND (b.status='CheckedIn' OR "+RoomSchedule.activeReservation("b")+") "
                +"AND NOT EXISTS(SELECT 1 FROM Payments pay WHERE pay.booking_id=b.booking_id)"
                +(propertyId==null?"":" AND r.property_id=?")+" ORDER BY CASE WHEN b.payment_review_status='Pending' THEN 0 ELSE 1 END,b.transfer_reported_at,b.booking_id";
            try(var statement=connection.prepareStatement(sql)){
                if(propertyId!=null)statement.setInt(1,propertyId);
                try(var rows=statement.executeQuery()){
                    while(rows.next()){
                        int id=rows.getInt("booking_id");Map<String,Object> row=new LinkedHashMap<>();
                        row.put("bookingId",id);row.put("status",rows.getString("status"));row.put("roomCode",rows.getString("room_code"));row.put("customerName",rows.getString("full_name"));
                        row.put("paymentMethod",rows.getString("selected_payment_method"));row.put("reviewStatus",rows.getString("payment_review_status"));
                        long seconds=rows.getLong("waiting_seconds");row.put("waitingSeconds",Math.max(0,seconds));row.put("reviewOverdue","Pending".equals(rows.getString("payment_review_status")) && seconds>reviewWarningMinutes*60L);
                        row.put("grandTotal",BookingInvoiceSnapshot.build(connection,id).get("grandTotal"));result.add(row);
                    }
                }
            }return result;
        });
    }
    private record Locked(int roomId,String status,String method,String review,boolean active){}
    private Locked lock(Connection connection,int id) throws SQLException{
        Input.id(id);int roomId;
        try(var statement=connection.prepareStatement("SELECT room_id FROM Bookings WHERE booking_id=?")){
            statement.setInt(1,id);try(var rows=statement.executeQuery()){if(!rows.next())throw ApiErrors.notFound("Không tìm thấy đơn.");roomId=rows.getInt(1);}
        }
        RoomSchedule.lockRoom(connection,roomId);
        try(var statement=connection.prepareStatement("SELECT b.room_id,b.status,b.selected_payment_method,b.payment_review_status,CASE WHEN "+RoomSchedule.activeReservation("b")+" THEN 1 ELSE 0 END AS active FROM Bookings b WITH(UPDLOCK,HOLDLOCK) WHERE b.booking_id=?")){
            statement.setInt(1,id);try(var rows=statement.executeQuery()){
                if(!rows.next() || rows.getInt(1)!=roomId)throw new RoomSchedule.Conflict("Đơn vừa thay đổi. Vui lòng tải lại.");
                return new Locked(roomId,rows.getString(2),rows.getString(3),rows.getString(4),rows.getBoolean(5));
            }
        }
    }
    private Integer existingPayment(Connection connection,int id) throws SQLException{
        try(var statement=connection.prepareStatement("SELECT payment_id,payment_status FROM Payments WHERE booking_id=?")){
            statement.setInt(1,id);try(var rows=statement.executeQuery()){
                if(!rows.next())return null;
                if(!"Đã thanh toán".equals(rows.getString(2)))throw new RoomSchedule.Conflict("Bản thanh toán cần kiểm tra trước khi thu tiền.");
                return rows.getInt(1);
            }
        }
    }
    @GetMapping("/{id}")
    public Map<String,Object> preview(@AuthenticationPrincipal AppUser user,@PathVariable int id){
        admin(user);return AccountStore.transaction(connection->{
            lock(connection,id);Integer paymentId=existingPayment(connection,id);
            return paymentId==null?BookingInvoiceSnapshot.build(connection,id):BookingInvoiceSnapshot.read(connection,paymentId);
        });
    }
    @PostMapping("/{id}/confirm")
    public Map<String,Object> confirm(@AuthenticationPrincipal AppUser user,@PathVariable int id,@RequestBody Map<String,Object> body){
        admin(user);CustomerOwnership.allowedFields(body,Set.of("paymentMethod","expectedTotal"));
        String method=Input.oneOf(Input.text(body,"paymentMethod",50,true,null),Set.of("Tiền mặt","Chuyển khoản"),"Phương thức thanh toán");
        long expected=Input.money(body,"expectedTotal",false);
        return AccountStore.transaction(connection->{
            Locked booking=lock(connection,id);Integer existing=existingPayment(connection,id);
            if(existing!=null)return Map.of("success",true,"paymentId",existing,"invoice",BookingInvoiceSnapshot.read(connection,existing),"message","Đơn đã được xác nhận thu tiền trước đó.");
            if(!Set.of("Confirmed","CheckedIn").contains(booking.status()) || ("Confirmed".equals(booking.status()) && !booking.active()))
                throw new RoomSchedule.Conflict("Đơn đã hủy hoặc hết hạn; không thể xác nhận thu tiền. Hãy xử lý với khách trước khi đặt lại.");
            if(booking.method()!=null && !method.equals(booking.method()))throw new RoomSchedule.Conflict("Phương thức phải khớp lựa chọn của đơn.");
            Map<String,Object> invoice=BookingInvoiceSnapshot.build(connection,id);
            if(((BigDecimal)invoice.get("grandTotal")).compareTo(BigDecimal.valueOf(expected))!=0)throw new ApiProblem(409,"PRICE_CHANGED","Tổng tiền đã thay đổi. Vui lòng kiểm tra lại bảng kê.");
            int paymentId=BookingInvoiceSnapshot.store(connection,invoice,method);
            try(var statement=connection.prepareStatement("UPDATE Bookings SET selected_payment_method=?,payment_review_status='Confirmed',payment_reviewed_at=SYSUTCDATETIME(),payment_reviewed_by=?,reservation_expires_at=NULL WHERE booking_id=?")){
                statement.setString(1,method);statement.setInt(2,user.userId());statement.setInt(3,id);statement.executeUpdate();
            }
            return Map.of("success",true,"paymentId",paymentId,"invoice",invoice,"message","Đã xác nhận thu tiền và lưu hóa đơn. Trạng thái lưu trú giữ nguyên.");
        });
    }
    @PostMapping("/{id}/reject")
    public Map<String,Object> reject(@AuthenticationPrincipal AppUser user,@PathVariable int id){
        admin(user);return AccountStore.transaction(connection->{
            Locked booking=lock(connection,id);
            if(existingPayment(connection,id)!=null || !"Pending".equals(booking.review()))throw new RoomSchedule.Conflict("Đơn không còn chờ đối soát.");
            try(var statement=connection.prepareStatement("UPDATE Bookings SET payment_review_status='Rejected',payment_reviewed_at=SYSUTCDATETIME(),payment_reviewed_by=? WHERE booking_id=?")){
                statement.setInt(1,user.userId());statement.setInt(2,id);statement.executeUpdate();
            }
            RoomSchedule.refresh(connection,booking.roomId(),RoomSchedule.today(RoomSchedule.CLOCK));
            return Map.of("success",true,"message","Đã từ chối đối soát. Không ghi nhận thanh toán; hạn giữ phòng ban đầu tiếp tục áp dụng.");
        });
    }
}
