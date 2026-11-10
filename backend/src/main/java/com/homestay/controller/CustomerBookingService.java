package com.homestay.controller;

import com.homestay.api.*;
import com.homestay.security.*;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;

/** Private customer operations. No shared operational list is fetched and filtered in Java. */
final class CustomerBookingService {
    private static final Set<String> REQUEST_FIELDS=Set.of("roomId","billingUnit","checkinDate","checkoutDate",
        "checkinAt","checkoutAt","services","note","paymentMethod","profile","expectedTotal","expectedQuote");
    private final Clock clock;
    private final int holdMinutes;
    CustomerBookingService(Clock clock,int holdMinutes) {
        if(holdMinutes<1 || holdMinutes>120) throw new IllegalArgumentException("Reservation hold must be 1–120 minutes");
        this.clock=clock;this.holdMinutes=holdMinutes;
    }
    private record Stay(String unit,LocalDate startDate,LocalDate endDate,LocalDateTime start,LocalDateTime end) {}
    private record ServiceLine(int serviceId,String name,String unit,int quantity,BigDecimal price,BigDecimal total) {}
    private record Priced(Stay stay,Map<String,Object> room,BigDecimal base,BigDecimal hourly,
                          Map<String,Long> packages,Map<String,Object> nightly,long units,BigDecimal roomTotal,
                          List<ServiceLine> services,BigDecimal serviceTotal,BigDecimal total) {}

    Map<String,Object> profile(AppUser user) {
        return AccountStore.transaction(connection->{
            Map<String,Object> profile=CustomerOwnership.profile(connection,user,false);
            Integer id=(Integer)profile.get("customerId");
            profile.put("profileReady",id!=null);
            if(id!=null) try(var statement=connection.prepareStatement("SELECT document_type,cccd,address FROM Customers WHERE customer_id=?")) {
                statement.setInt(1,id);
                try(var rows=statement.executeQuery()) {
                    if(!rows.next()) throw new ApiProblem(409,"DATA_INCONSISTENT","Hồ sơ tài khoản cần được quản trị viên kiểm tra.");
                    profile.put("documentType",rows.getString(1));profile.put("documentNumber",rows.getString(2));profile.put("address",rows.getString(3));
                }
            }
            return Map.of("success",true,"profile",profile);
        });
    }

    Map<String,Object> saveProfile(AppUser user,Map<String,Object> body) {
        return AccountStore.transaction(connection->CustomerOwnership.saveProfile(connection,user,body));
    }

    List<Map<String,Object>> services(AppUser user) {
        CustomerOwnership.requireCustomer(user);
        return AccountStore.transaction(connection->{
            List<Map<String,Object>> result=new ArrayList<>();
            try(var statement=connection.prepareStatement("SELECT service_id,service_name,unit_price,unit FROM Services WHERE status=N'Hoạt động' ORDER BY service_id");var rows=statement.executeQuery()) {
                while(rows.next()) result.add(Map.of("serviceId",rows.getInt(1),"serviceName",rows.getString(2),"unitPrice",BookingInvoiceSnapshot.money(rows.getBigDecimal(3)),"unit",rows.getString(4)));
            }
            return result;
        });
    }

    private Stay stay(Map<String,Object> body) {
        String unit=Input.oneOf(Input.text(body,"billingUnit",5,false,"Night"),Set.of("Night","Hour"),"Cách tính tiền");
        if("Hour".equals(unit)) {
            var range=HourlyStay.range(Input.text(body,"checkinAt",16,true,null),Input.text(body,"checkoutAt",16,true,null),clock);
            // The legacy DATE constraint requires a later departure date even for a same-day hourly stay.
            LocalDate endDate=range.end().toLocalDate();
            if(!endDate.isAfter(range.start().toLocalDate())) endDate=range.start().toLocalDate().plusDays(1);
            return new Stay(unit,range.start().toLocalDate(),endDate,range.start(),range.end());
        }
        var range=RoomSchedule.range(Input.text(body,"checkinDate",10,true,null),Input.text(body,"checkoutDate",10,true,null),RoomSchedule.today(clock));
        if(range.checkout().getYear()>9998) throw ApiErrors.invalid("Ngày trả phòng vượt giới hạn cho phép.");
        return new Stay(unit,range.checkin(),range.checkout(),range.checkin().atStartOfDay(),range.checkout().atStartOfDay());
    }

    private void bindAvailability(PreparedStatement statement,int first,Stay stay) throws SQLException {
        statement.setTimestamp(first,Timestamp.valueOf(stay.end()));statement.setTimestamp(first+1,Timestamp.valueOf(stay.start()));
        statement.setTimestamp(first+2,Timestamp.valueOf(stay.start()));
        statement.setTimestamp(first+3,Timestamp.valueOf("Hour".equals(stay.unit())?HourlyStay.now(clock):RoomSchedule.today(clock).atStartOfDay()));
    }

    List<Map<String,Object>> availability(AppUser user,Map<String,Object> body,Integer propertyId) {
        CustomerOwnership.requireCustomer(user);
        CustomerOwnership.allowedFields(body,Set.of("billingUnit","checkinDate","checkoutDate","checkinAt","checkoutAt"));
        Stay stay=stay(body);
        if(propertyId!=null) Input.id(propertyId);
        return AccountStore.transaction(connection->{
            List<Map<String,Object>> result=new ArrayList<>();
            String sql="SELECT r.*,p.property_name,p.address AS property_address FROM Rooms r JOIN Properties p ON p.property_id=r.property_id "
                +"WHERE r.status<>N'Bảo trì' AND NOT "+RoomSchedule.unavailablePredicate()
                +(propertyId==null?"":" AND r.property_id=?")+("Hour".equals(stay.unit())?" AND r.hourly_price>0":"")+" ORDER BY r.room_code";
            try(var statement=connection.prepareStatement(sql)) {
                bindAvailability(statement,1,stay);if(propertyId!=null) statement.setInt(5,propertyId);
                try(var rows=statement.executeQuery()) {
                    while(rows.next()) {
                        Map<String,Object> room=publicRoom(rows);
                        BigDecimal price=BookingInvoiceSnapshot.money(rows.getBigDecimal("price"));
                        BigDecimal total="Hour".equals(stay.unit())
                            ? HourlyPricing.quote(rows.getBigDecimal("hourly_price"),HourlyPricing.packages(rows),HourlyStay.billedHours(stay.start(),stay.end())).total()
                            : NightlyPricing.quote(price,NightlyPricing.room(rows),stay.startDate(),stay.endDate()).total();
                        room.put("estimatedRoomTotal",total);room.put("billingUnit",stay.unit());
                        room.put("nights","Night".equals(stay.unit())?java.time.temporal.ChronoUnit.DAYS.between(stay.startDate(),stay.endDate()):0);
                        if("Hour".equals(stay.unit())) room.put("billedHours",HourlyStay.billedHours(stay.start(),stay.end()));
                        result.add(room);
                    }
                }
            }
            return result;
        });
    }

    private Map<String,Object> publicRoom(ResultSet rows) throws SQLException {
        Map<String,Object> room=new LinkedHashMap<>();
        room.put("roomId",rows.getInt("room_id"));room.put("roomCode",rows.getString("room_code"));
        room.put("roomName",rows.getString("room_name"));room.put("roomType",rows.getString("room_type"));
        room.put("propertyId",rows.getInt("property_id"));room.put("propertyName",rows.getString("property_name"));
        room.put("propertyAddress",rows.getString("property_address"));room.put("description",rows.getString("description"));
        room.put("price",rows.getBigDecimal("price"));room.put("hourlyPrice",rows.getBigDecimal("hourly_price"));
        room.put("hourlyPackages",HourlyPricing.packages(rows));room.put("nightlyPricing",NightlyPricing.room(rows));
        // Deliberately no occupancy status, guest information, operational counts or other booking IDs.
        return room;
    }

    private List<ServiceLine> serviceLines(Connection connection,Object input) throws SQLException {
        if(input==null) return List.of();
        if(!(input instanceof List<?> list) || list.size()>30) throw ApiErrors.invalid("Chọn tối đa 30 dịch vụ.");
        List<ServiceLine> result=new ArrayList<>();Set<Integer> seen=new HashSet<>();
        for(Object item:list) {
            if(!(item instanceof Map<?,?> raw)) throw ApiErrors.invalid("Dịch vụ không hợp lệ.");
            Map<String,Object> body=new HashMap<>();
            for(var entry:raw.entrySet()) {
                if(!(entry.getKey() instanceof String key)) throw ApiErrors.invalid("Dịch vụ không hợp lệ.");
                body.put(key,entry.getValue());
            }
            CustomerOwnership.allowedFields(body,Set.of("serviceId","quantity"));
            int id=Input.integer(body,"serviceId",1,Integer.MAX_VALUE),quantity=Input.integer(body,"quantity",1,10000);
            if(!seen.add(id)) throw ApiErrors.invalid("Mỗi dịch vụ chỉ chọn một lần; hãy cập nhật số lượng.");
            // Deterministic service order prevents inverted catalog locks across concurrent bookings.
            result.add(new ServiceLine(id,"","",quantity,BigDecimal.ZERO,BigDecimal.ZERO));
        }
        result.sort(Comparator.comparingInt(ServiceLine::serviceId));
        List<ServiceLine> priced=new ArrayList<>();
        for(ServiceLine line:result) try(var statement=connection.prepareStatement("SELECT service_name,unit_price,unit FROM Services WITH(HOLDLOCK) WHERE service_id=? AND status=N'Hoạt động'")) {
            statement.setInt(1,line.serviceId());
            try(var rows=statement.executeQuery()) {
                if(!rows.next()) throw new ApiProblem(409,"SERVICE_UNAVAILABLE","Dịch vụ đã ngừng cung cấp hoặc không tồn tại. Vui lòng chọn lại.");
                BigDecimal price=BookingInvoiceSnapshot.money(rows.getBigDecimal(2));
                priced.add(new ServiceLine(line.serviceId(),rows.getString(1),rows.getString(3),line.quantity(),price,
                    BookingInvoiceSnapshot.money(price.multiply(BigDecimal.valueOf(line.quantity())))));
            }
        }
        return priced;
    }

    private Priced price(Connection connection,Map<String,Object> body,Stay stay) throws SQLException {
        int id=Input.integer(body,"roomId",1,Integer.MAX_VALUE);
        RoomSchedule.lockRoom(connection,id);
        try(var statement=connection.prepareStatement("SELECT r.room_id FROM Rooms r WHERE r.room_id=? AND (r.status=N'Bảo trì' OR "+RoomSchedule.unavailablePredicate()+")")) {
            statement.setInt(1,id);bindAvailability(statement,2,stay);
            try(var rows=statement.executeQuery()) {if(rows.next()) throw new RoomSchedule.Conflict("Phòng không còn trống trong thời gian đã chọn.");}
        }
        Map<String,Object> room;BigDecimal base,hourly;Map<String,Long> packages;Map<String,Object> nightly;
        try(var statement=connection.prepareStatement("SELECT r.*,p.property_name,p.address AS property_address FROM Rooms r JOIN Properties p ON p.property_id=r.property_id WHERE r.room_id=?")) {
            statement.setInt(1,id);
            try(var rows=statement.executeQuery()) {
                if(!rows.next()) throw ApiErrors.notFound("Không tìm thấy phòng.");
                room=publicRoom(rows);base=BookingInvoiceSnapshot.money(rows.getBigDecimal("price"));
                hourly=rows.getBigDecimal("hourly_price");packages=HourlyPricing.packages(rows);nightly=NightlyPricing.room(rows);
            }
        }
        long units;BigDecimal roomTotal;
        if("Hour".equals(stay.unit())) {
            units=HourlyStay.billedHours(stay.start(),stay.end());roomTotal=HourlyPricing.quote(hourly,packages,units).total();
        } else {
            var quote=NightlyPricing.quote(base,nightly,stay.startDate(),stay.endDate());units=quote.nights();roomTotal=quote.total();
        }
        List<ServiceLine> services=serviceLines(connection,body.get("services"));
        BigDecimal serviceTotal=BigDecimal.ZERO;
        for(ServiceLine line:services) serviceTotal=BookingInvoiceSnapshot.money(serviceTotal.add(line.total()));
        return new Priced(stay,room,base,hourly,packages,nightly,units,roomTotal,services,serviceTotal,
            BookingInvoiceSnapshot.money(roomTotal.add(serviceTotal)));
    }

    private Map<String,Object> quoteView(Priced price) {
        Map<String,Object> result=new LinkedHashMap<>();result.put("success",true);result.put("room",price.room());
        result.put("billingUnit",price.stay().unit());result.put("roomTotal",price.roomTotal());
        result.put("serviceTotal",price.serviceTotal());result.put("grandTotal",price.total());
        result.put("nights","Night".equals(price.stay().unit())?price.units():0);
        result.put("billedHours","Hour".equals(price.stay().unit())?price.units():0);
        List<Map<String,Object>> services=new ArrayList<>();
        for(ServiceLine line:price.services()) services.add(Map.of("serviceId",line.serviceId(),"serviceName",line.name(),"unit",line.unit(),
            "quantity",line.quantity(),"unitPrice",line.price(),"totalPrice",line.total()));
        result.put("services",services);
        // Detect changes to the displayed breakdown even when the grand total happens to match.
        try{
            byte[] encoded=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(result);
            result.put("quoteFingerprint",HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(encoded)));
        }catch(java.io.IOException | java.security.NoSuchAlgorithmException error){throw new IllegalStateException("Cannot identify quote",error);}
        return result;
    }

    Map<String,Object> quote(AppUser user,Map<String,Object> body) {
        CustomerOwnership.requireCustomer(user);CustomerOwnership.allowedFields(body,REQUEST_FIELDS);Stay stay=stay(body);
        return AccountStore.transaction(connection->quoteView(price(connection,body,stay)));
    }

    Map<String,Object> create(AppUser user,Map<String,Object> body) {
        CustomerOwnership.requireCustomer(user);CustomerOwnership.allowedFields(body,REQUEST_FIELDS);Stay stay=stay(body);
        String method=Input.oneOf(Input.text(body,"paymentMethod",50,true,null),Set.of("Tiền mặt","Chuyển khoản"),"Phương thức thanh toán");
        String note=Input.text(body,"note",500,false,"");long expected=Input.money(body,"expectedTotal",false);
        String expectedQuote=Input.text(body,"expectedQuote",64,true,null);
        return AccountStore.transaction(connection->{
            // Preserve room -> booking ordering before any account/profile updates.
            Priced price=price(connection,body,stay);
            if(price.total().compareTo(BigDecimal.valueOf(expected))!=0 || !expectedQuote.equals(quoteView(price).get("quoteFingerprint")))
                throw new ApiProblem(409,"PRICE_CHANGED","Giá đã thay đổi. Vui lòng lấy lại báo giá và xác nhận tổng tiền.");
            if(body.get("profile")!=null) {
                if(!(body.get("profile") instanceof Map<?,?> raw)) throw ApiErrors.invalid("Hồ sơ không hợp lệ.");
                Map<String,Object> profile=new HashMap<>();
                for(var entry:raw.entrySet()) {
                    if(!(entry.getKey() instanceof String key)) throw ApiErrors.invalid("Hồ sơ không hợp lệ.");
                    profile.put(key,entry.getValue());
                }
                CustomerOwnership.saveProfile(connection,user,profile);
            }
            int customerId=CustomerOwnership.requireProfile(connection,user),bookingId;
            String sql="""
                INSERT Bookings(customer_id,room_id,checkin_date,checkout_date,status,note,price_per_night,created_at,
                    billing_unit,checkin_at,checkout_at,price_per_hour,hourly_packages_snapshot,nightly_pricing_snapshot,
                    selected_payment_method,reservation_expires_at)
                VALUES(?,?,?,?,'Confirmed',?,?,?,?,?,?,?,?,?,?,CASE WHEN ?=1 THEN DATEADD(minute,?,SYSUTCDATETIME()) ELSE NULL END)
                """;
            try(var statement=connection.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)) {
                boolean hourly="Hour".equals(stay.unit());
                statement.setInt(1,customerId);statement.setInt(2,(Integer)price.room().get("roomId"));
                statement.setDate(3,java.sql.Date.valueOf(stay.startDate()));statement.setDate(4,java.sql.Date.valueOf(stay.endDate()));
                statement.setString(5,note);statement.setBigDecimal(6,hourly?BigDecimal.ZERO:price.base());
                statement.setTimestamp(7,Timestamp.valueOf(HourlyStay.now(clock)));statement.setString(8,stay.unit());
                statement.setTimestamp(9,hourly?Timestamp.valueOf(stay.start()):null);statement.setTimestamp(10,hourly?Timestamp.valueOf(stay.end()):null);
                statement.setBigDecimal(11,hourly?price.hourly():null);statement.setString(12,hourly?HourlyPricing.encode(price.packages()):null);
                statement.setString(13,hourly?null:NightlyPricing.encode(price.nightly()));statement.setString(14,method);
                statement.setBoolean(15,"Chuyển khoản".equals(method));statement.setInt(16,holdMinutes);statement.executeUpdate();
                try(var keys=statement.getGeneratedKeys()) {if(!keys.next()) throw new SQLException("Missing booking key");bookingId=keys.getInt(1);}
            }
            for(ServiceLine line:price.services()) try(var statement=connection.prepareStatement("INSERT Booking_Services(booking_id,service_id,quantity,service_name_snapshot,unit_price_snapshot,total_price,unit_snapshot) VALUES(?,?,?,?,?,?,?)")) {
                statement.setInt(1,bookingId);statement.setInt(2,line.serviceId());statement.setInt(3,line.quantity());
                statement.setString(4,line.name());statement.setBigDecimal(5,line.price());statement.setBigDecimal(6,line.total());statement.setString(7,line.unit());statement.executeUpdate();
            }
            RoomSchedule.refresh(connection,(Integer)price.room().get("roomId"),RoomSchedule.today(clock));
            Map<String,Object> result=quoteView(price);result.put("bookingId",bookingId);result.put("paymentStatus","Unpaid");
            result.put("message","Đã tạo đặt phòng.");return result;
        });
    }

    private String ownedSelect() {
        return """
            SELECT b.booking_id,b.room_id,b.checkin_date,b.checkout_date,b.checkin_at,b.checkout_at,b.billing_unit,
                   b.status,b.note,b.created_at,b.selected_payment_method,b.reservation_expires_at,b.payment_review_status,
                   b.cancellation_reason,r.room_code,r.room_name,r.room_type,r.property_id,p.property_name,
                   pay.payment_id,pay.payment_method,pay.grand_total,pay.paid_at,
                   CASE WHEN b.status='Confirmed' AND NOT %s AND pay.payment_id IS NULL THEN 1 ELSE 0 END AS expired,
                   SYSUTCDATETIME() AS server_time
            FROM Bookings b JOIN Users u ON u.customer_id=b.customer_id AND u.user_id=? AND u.role='customer'
            JOIN Rooms r ON r.room_id=b.room_id JOIN Properties p ON p.property_id=r.property_id
            LEFT JOIN Payments pay ON pay.booking_id=b.booking_id AND pay.payment_status=N'Đã thanh toán'
            """.formatted(RoomSchedule.activeReservation("b"));
    }

    private Map<String,Object> bookingRow(ResultSet rows) throws SQLException {
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("bookingId",rows.getInt("booking_id"));result.put("roomId",rows.getInt("room_id"));
        for(String field:List.of("room_code","room_name","room_type","property_name","billing_unit","checkin_date","checkout_date","checkin_at","checkout_at","note","created_at")) {
            String key=switch(field) {
                case "room_code"->"roomCode";case "room_name"->"roomName";case "room_type"->"roomType";case "property_name"->"propertyName";
                case "billing_unit"->"billingUnit";case "checkin_date"->"checkinDate";case "checkout_date"->"checkoutDate";
                case "checkin_at"->"checkinAt";case "checkout_at"->"checkoutAt";case "created_at"->"createdAt";default->field;
            };
            if(Set.of("checkin_at","checkout_at","created_at").contains(field)) {
                Timestamp timestamp=rows.getTimestamp(field);result.put(key,timestamp==null?null:timestamp.toLocalDateTime().toString());
            } else result.put(key,rows.getString(field));
        }
        boolean paid=rows.getObject("payment_id")!=null;
        boolean expired=rows.getBoolean("expired") || "Expired".equals(rows.getString("cancellation_reason"));
        result.put("propertyId",rows.getInt("property_id"));result.put("status",expired?"Expired":rows.getString("status"));
        result.put("paymentStatus",paid?"Paid":"Pending".equals(rows.getString("payment_review_status"))?"PendingReview":"Unpaid");
        result.put("paymentMethod",paid?rows.getString("payment_method"):rows.getString("selected_payment_method"));
        result.put("reservationExpiresAt",utc(rows.getTimestamp("reservation_expires_at")));result.put("serverTime",utc(rows.getTimestamp("server_time")));
        if(paid) {result.put("paymentId",rows.getInt("payment_id"));result.put("paidTotal",rows.getBigDecimal("grand_total"));result.put("paidAt",rows.getString("paid_at"));}
        return result;
    }
    private String utc(Timestamp timestamp) {return timestamp==null?null:timestamp.toLocalDateTime().atOffset(ZoneOffset.UTC).toString();}

    List<Map<String,Object>> bookings(AppUser user) {
        CustomerOwnership.requireCustomer(user);
        return AccountStore.transaction(connection->{
            List<Map<String,Object>> result=new ArrayList<>();
            try(var statement=connection.prepareStatement(ownedSelect()+" ORDER BY b.created_at DESC,b.booking_id DESC")) {
                statement.setInt(1,user.userId());
                try(var rows=statement.executeQuery()) {while(rows.next()) result.add(bookingRow(rows));}
            }
            return result;
        });
    }

    private Map<String,Object> owned(Connection connection,AppUser user,int id) throws SQLException {
        CustomerOwnership.requireCustomer(user);Input.id(id);
        try(var statement=connection.prepareStatement(ownedSelect()+" WHERE b.booking_id=?")) {
            statement.setInt(1,user.userId());statement.setInt(2,id);
            try(var rows=statement.executeQuery()) {if(!rows.next()) throw ApiErrors.notFound("Không tìm thấy đơn đặt phòng.");return bookingRow(rows);}
        }
    }

    Map<String,Object> detail(AppUser user,int id) {
        return AccountStore.transaction(connection->{
            Map<String,Object> result=owned(connection,user,id);
            // Ownership is established before loading any guest identity, services or stored invoice.
            result.put("preview",BookingInvoiceSnapshot.build(connection,id));result.put("success",true);return result;
        });
    }

    Map<String,Object> cancel(AppUser user,int id) {
        return AccountStore.transaction(connection->{
            Map<String,Object> before=owned(connection,user,id);RoomSchedule.lockRoom(connection,(Integer)before.get("roomId"));
            Map<String,Object> booking=owned(connection,user,id);
            if(!"Confirmed".equals(booking.get("status")) || !"Unpaid".equals(booking.get("paymentStatus")))
                throw new RoomSchedule.Conflict("Chỉ hủy được đơn chưa nhận phòng, chưa thanh toán và không đang đối soát.");
            try(var statement=connection.prepareStatement("UPDATE Bookings SET status='Cancelled',cancellation_reason='Customer' WHERE booking_id=? AND status='Confirmed'")) {
                statement.setInt(1,id);if(statement.executeUpdate()!=1) throw new RoomSchedule.Conflict("Đơn vừa thay đổi. Vui lòng tải lại.");
            }
            RoomSchedule.refresh(connection,(Integer)booking.get("roomId"),RoomSchedule.today(clock));
            return Map.of("success",true,"message","Đã hủy đơn đặt phòng.");
        });
    }

    Map<String,Object> payment(AppUser user,int id) {
        return AccountStore.transaction(connection->{
            Map<String,Object> result=owned(connection,user,id);Map<String,Object> preview=BookingInvoiceSnapshot.build(connection,id);
            result.put("success",true);result.put("roomTotal",preview.get("roomTotal"));result.put("serviceTotal",preview.get("serviceTotal"));
            result.put("grandTotal",preview.get("grandTotal"));result.put("reference","HOMESTAY BK"+id);
            result.put("canReportTransfer","Confirmed".equals(result.get("status")) && "Unpaid".equals(result.get("paymentStatus")) && "Chuyển khoản".equals(result.get("paymentMethod")));
            return result;
        });
    }

    Map<String,Object> reportTransfer(AppUser user,int id) {
        return AccountStore.transaction(connection->{
            Map<String,Object> before=owned(connection,user,id);RoomSchedule.lockRoom(connection,(Integer)before.get("roomId"));
            Map<String,Object> booking=owned(connection,user,id);
            if(!"Confirmed".equals(booking.get("status"))) throw new RoomSchedule.Conflict("Đơn không còn hiệu lực để gửi yêu cầu xác nhận chuyển khoản.");
            if("PendingReview".equals(booking.get("paymentStatus"))) return Map.of("success",true,"paymentStatus","PendingReview","message","Yêu cầu đang chờ xác nhận.");
            if(!"Confirmed".equals(booking.get("status")) || !"Unpaid".equals(booking.get("paymentStatus")) || !"Chuyển khoản".equals(booking.get("paymentMethod")))
                throw new RoomSchedule.Conflict("Đơn không còn đủ điều kiện gửi yêu cầu xác nhận chuyển khoản.");
            try(var statement=connection.prepareStatement("UPDATE Bookings SET payment_review_status='Pending',transfer_reported_at=SYSUTCDATETIME() WHERE booking_id=? AND status='Confirmed' AND reservation_expires_at>SYSUTCDATETIME()")) {
                statement.setInt(1,id);if(statement.executeUpdate()!=1) throw new RoomSchedule.Conflict("Đã hết hạn giữ phòng. Vui lòng đặt lại.");
            }
            return Map.of("success",true,"paymentStatus","PendingReview","message","Đã gửi yêu cầu. Vui lòng chờ quản trị viên xác nhận nhận tiền.");
        });
    }

    List<Map<String,Object>> invoices(AppUser user) {
        CustomerOwnership.requireCustomer(user);
        return AccountStore.transaction(connection->{
            List<Map<String,Object>> result=new ArrayList<>();
            try(var statement=connection.prepareStatement("SELECT pay.payment_id,pay.booking_id,pay.paid_at,pay.payment_method,pay.grand_total,r.room_code,r.room_name FROM Payments pay JOIN Bookings b ON b.booking_id=pay.booking_id JOIN Users u ON u.customer_id=b.customer_id AND u.user_id=? AND u.role='customer' JOIN Rooms r ON r.room_id=b.room_id WHERE pay.payment_status=N'Đã thanh toán' ORDER BY pay.paid_at DESC,pay.payment_id DESC")) {
                statement.setInt(1,user.userId());
                try(var rows=statement.executeQuery()) {
                    while(rows.next()) {
                        Map<String,Object> invoice=new LinkedHashMap<>();
                        invoice.put("paymentId",rows.getInt(1));invoice.put("bookingId",rows.getInt(2));
                        Timestamp paidAt=rows.getTimestamp(3);invoice.put("paidAt",paidAt==null?null:paidAt.toLocalDateTime().toString());
                        invoice.put("paymentMethod",rows.getString(4));invoice.put("grandTotal",rows.getBigDecimal(5));
                        invoice.put("roomCode",rows.getString(6));invoice.put("roomName",rows.getString(7));result.add(invoice);
                    }
                }
            }
            return result;
        });
    }

    Map<String,Object> invoice(AppUser user,int id) {
        CustomerOwnership.requireCustomer(user);Input.id(id);
        return AccountStore.transaction(connection->{
            try(var statement=connection.prepareStatement("SELECT pay.payment_id FROM Payments pay JOIN Bookings b ON b.booking_id=pay.booking_id JOIN Users u ON u.customer_id=b.customer_id AND u.user_id=? AND u.role='customer' WHERE pay.payment_id=? AND pay.payment_status=N'Đã thanh toán'")) {
                statement.setInt(1,user.userId());statement.setInt(2,id);
                try(var rows=statement.executeQuery()) {if(!rows.next()) throw ApiErrors.notFound("Không tìm thấy hóa đơn đã thanh toán.");}
            }
            return BookingInvoiceSnapshot.read(connection,id);
        });
    }
}
