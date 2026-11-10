package com.homestay.controller;

import com.homestay.db.DBConnection;
import com.homestay.api.*;
import org.springframework.web.bind.annotation.*;
import java.sql.*;
import java.time.Clock;
import java.util.*;

// NOTE API: Danh mục phòng tại /api/rooms: admin/customer được xem; thêm, sửa, xóa và đổi trạng thái dành cho admin.
// @...Mapping xác định phương thức HTTP và đường dẫn; @RequestBody đọc JSON,
// @PathVariable đọc ID trong URL, @RequestParam đọc tham số sau dấu ?. Quyền truy cập nằm trong SecurityConfig.
@RestController
@RequestMapping("/api/rooms")

public class RoomController {
    private final Clock clock;
    public RoomController() { this(RoomSchedule.CLOCK); }
    RoomController(Clock clock) { this.clock = clock; }

    public List<Map<String, Object>> getAllRooms() { return getAllRooms(null, null, null); }

    public List<Map<String, Object>> getAllRooms(@RequestParam(required=false) String type,
            @RequestParam(required=false) String status, @RequestParam(required=false) String search) {
        return getAllRooms(type,status,search,null);
    }
    // NOTE API: GET /api/rooms lấy phòng, có thể lọc type, status, search và propertyId.
    // Trạng thái hiển thị được tính theo lịch lưu trú hiện tại; trả thêm giá giờ/đêm và thông tin cơ sở.
    @GetMapping
    public List<Map<String,Object>> getAllRooms(@RequestParam(required=false) String type,
            @RequestParam(required=false) String status,@RequestParam(required=false) String search,@RequestParam(required=false) Integer propertyId) {
        if (type != null && !type.isBlank()) Input.oneOf(type,Set.of("Single","Double","VIP","Family"),"Loại phòng");
        if (status != null && !status.isBlank()) Input.oneOf(status,Set.of("Trống","Đã đặt","Đang sử dụng","Bảo trì"),"Trạng thái phòng");
        String keyword = search == null ? "" : search.trim();
        if (keyword.length() > 100) throw new ApiProblem(400,"INVALID_INPUT","Tìm kiếm tối đa 100 ký tự!");
        List<Map<String, Object>> rooms = new ArrayList<>();
        // Filter the calculated status, including when the business date has changed.
        String query = "SELECT * FROM (" + RoomSchedule.selectRooms() + ") q WHERE 1=1";
        List<String> values = new ArrayList<>();
        if(propertyId!=null){query+=" AND property_id=?";values.add(String.valueOf(Input.id(propertyId)));}
        if (type != null && !type.isBlank()) { query += " AND room_type=?"; values.add(type); }
        if (status != null && !status.isBlank()) { query += " AND status=?"; values.add(status); }
        if (!keyword.isEmpty()) {
            query += " AND (CHARINDEX(?,room_code)>0 OR CHARINDEX(?,room_name)>0)";
            values.add(keyword); values.add(keyword);
        }
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query + " ORDER BY room_code")) {
            RoomSchedule.bindNow(stmt, clock);
            for (int i=0;i<values.size();i++) stmt.setString(i+3,values.get(i));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) rooms.add(RoomSchedule.roomRow(rs));
            }
        } catch (SQLException e) { throw new RoomSchedule.QueryFailure(e); }
        return rooms;
    }

    // NOTE API: PUT /api/rooms/{id}/status nhận status là Trống hoặc Bảo trì.
    // Khóa phòng và kiểm tra lịch lưu trú trước khi đổi; không cho tự đặt trạng thái Đã đặt/Đang sử dụng.
    @PutMapping("/{id}/status")
    public Map<String, Object> updateStatus(@PathVariable int id, @RequestBody Map<String,Object> body) {
        return ApiErrors.guard(() -> {
            Input.id(id);
            String requested = Input.oneOf(Input.text(body,"status",30,true,null),Set.of("Trống","Bảo trì"),"Trạng thái phòng");
            return RoomSchedule.transaction(conn -> {
                RoomSchedule.LockedRoom room = RoomSchedule.lockRoom(conn,id);
                String actual = RoomSchedule.requestedStatus(conn,id,room.status(),requested,RoomSchedule.today(clock));
                try (PreparedStatement stmt = conn.prepareStatement("UPDATE Rooms SET status=? WHERE room_id=?")) {
                    stmt.setString(1,actual); stmt.setInt(2,id); stmt.executeUpdate();
                }
                return Map.of("success",true,"status",actual,"message","Đã cập nhật trạng thái phòng!");
            },"Không thể cập nhật trạng thái phòng!");
        });
    }

    // NOTE API: GET /api/rooms/{id} lấy chi tiết một phòng và trạng thái theo thời điểm hiện tại.
    // Có xử lý ID phòng cũ qua bảng ánh xạ; trả lỗi 404 nếu không tìm thấy.
    @GetMapping("/{id}")
    public Map<String, Object> getRoomById(@PathVariable int id) {
        return ApiErrors.guard(() -> {
            Input.id(id);
            try (Connection conn = DBConnection.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(RoomSchedule.selectRooms() + " WHERE r.room_id=?")) {
                RoomSchedule.bindNow(stmt, clock);
                stmt.setInt(3, com.homestay.db.RoomCatalogMigration.resolveId(conn,id));
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) return RoomSchedule.roomRow(rs);
                }
            } catch (SQLException e) { throw new RoomSchedule.QueryFailure(e); }
            return ApiErrors.missing("Không tìm thấy phòng!");


        });
    }

    // NOTE API: POST /api/rooms tạo phòng từ mã, tên, loại, giá, mô tả và propertyId.
    // Kiểm tra giá giờ/gói/cuối tuần và cơ sở; phòng mới chỉ được ở trạng thái Trống hoặc Bảo trì.
    @PostMapping
    public Map<String, Object> createRoom(@RequestBody Map<String, Object> body) {
        return ApiErrors.guard(() -> {
            Map<String,Object> nightly=NightlyPricing.input(body.get("nightlyPricing"));
            String code = Input.text(body,"roomCode",20,true,null);
            String name = Input.text(body,"roomName",100,true,null);
            String type = Input.oneOf(Input.text(body,"roomType",50,true,null),Set.of("Single","Double","VIP","Family"),"Loại phòng");
            long price = Input.money(body,"price",true);
            Long hourly = body.get("hourlyPrice")==null ? null : Input.money(body,"hourlyPrice",true);
            Map<String,Long> packages=HourlyPricing.validate(body.get("hourlyPackages"));
            if(packages!=null && hourly==null)throw new ApiProblem(400,"INVALID_INPUT","Nhập giá giờ lẻ khi sử dụng giá gói.");
            String status = Input.text(body,"status",30,false,"Trống");
            String description = Input.text(body,"description",500,false,"");
            int propertyId=body.containsKey("propertyId")?Input.integer(body,"propertyId",1,Integer.MAX_VALUE):1;
            if (status.isEmpty()) status = "Trống";
            if (code.isEmpty() || name.isEmpty() || price <= 0 || price > 999999999999L) {
                return ApiErrors.bad("Vui lòng nhập mã, tên phòng và giá nguyên hợp lệ!");
            }
            if (!Set.of("Trống","Bảo trì").contains(status)) {
                return ApiErrors.bad("Phòng mới chỉ có thể Trống hoặc Bảo trì!");
            }
            try (Connection conn = DBConnection.getConnection(); PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO Rooms(room_code,room_name,room_type,price,status,description,hourly_price,hourly_package_2,hourly_package_4,hourly_package_8,weekend_price,weekend_days,property_id) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                PropertyController.require(conn,propertyId);
                stmt.setString(1,code);
                stmt.setString(2,name);
                stmt.setString(3,type);
                stmt.setLong(4,price);
                stmt.setString(5,status);
                stmt.setString(6,description);
                stmt.setObject(7,hourly,Types.DECIMAL);
                for(int i=0;i<3;i++)stmt.setObject(8+i,packages==null?null:packages.get(HourlyPricing.KEYS.get(i)),Types.DECIMAL);
                stmt.setObject(11,nightly==null?null:nightly.get("weekendPrice"),Types.DECIMAL);
                stmt.setString(12,NightlyPricing.days(nightly));
                stmt.setInt(13,propertyId);
                stmt.executeUpdate();
                return Map.of("success",true,"message","Thêm phòng thành công!");
            } catch (SQLException e) {
                return ApiErrors.database(e);
            }


        });
    }

    // NOTE API: PUT /api/rooms/{id} sửa thông tin, bảng giá, trạng thái hoặc cơ sở của phòng.
    // Kiểm tra mã trùng và lịch lưu trú; cập nhật trong giao dịch, không ghi lại giá của các booking cũ.
    @PutMapping("/{id}")
    public Map<String, Object> updateRoom(@PathVariable int id, @RequestBody Map<String, Object> body) {
        return ApiErrors.guard(() -> {
            Map<String,Object> nightly=NightlyPricing.input(body.get("nightlyPricing"));
            Input.id(id);
            String code = Input.text(body,"roomCode",20,false,"");
            String name = Input.text(body,"roomName",100,true,null);
            String type = Input.oneOf(Input.text(body,"roomType",50,true,null),Set.of("Single","Double","VIP","Family"),"Loại phòng");
            long price = Input.money(body,"price",true);
            Long hourly = body.get("hourlyPrice")==null ? null : Input.money(body,"hourlyPrice",true);
            Map<String,Long> packages=HourlyPricing.validate(body.get("hourlyPackages"));
            if(packages!=null && body.containsKey("hourlyPrice") && hourly==null)throw new ApiProblem(400,"INVALID_INPUT","Nhập giá giờ lẻ khi sử dụng giá gói.");
            String requested=Input.text(body,"status",30,false,null);
            if(requested!=null) Input.oneOf(requested,Set.of("Trống","Bảo trì"),"Trạng thái phòng (chỉ Trống hoặc Bảo trì)");
            String description=Input.text(body,"description",500,false,"");
            if (name.isEmpty() || price <= 0 || price > 999999999999L) {
                return ApiErrors.bad("Tên phòng và giá nguyên không hợp lệ!");
            }
            return RoomSchedule.transaction(conn -> {
                RoomSchedule.LockedRoom room = RoomSchedule.lockRoom(conn,id);
                String status = RoomSchedule.requestedStatus(conn,id,room.status(),requested,RoomSchedule.today(clock));
                if (!code.isEmpty()) {
                    try (PreparedStatement stmt = conn.prepareStatement("SELECT room_id FROM Rooms WHERE room_code=? AND room_id<>?")) {
                        stmt.setString(1,code);
                        stmt.setInt(2,id);
                        try (ResultSet rs = stmt.executeQuery()) {
                            if (rs.next()) throw new RoomSchedule.Conflict("Mã phòng đã được sử dụng!");
                        }
                    }
                }
                String query = code.isEmpty()
                        ? "UPDATE Rooms SET room_name=?,room_type=?,price=?,status=?,description=?,hourly_price=CASE WHEN ?=1 THEN ? ELSE hourly_price END,hourly_package_2=CASE WHEN ?=1 THEN ? ELSE hourly_package_2 END,hourly_package_4=CASE WHEN ?=1 THEN ? ELSE hourly_package_4 END,hourly_package_8=CASE WHEN ?=1 THEN ? ELSE hourly_package_8 END WHERE room_id=?"
                        : "UPDATE Rooms SET room_code=?,room_name=?,room_type=?,price=?,status=?,description=?,hourly_price=CASE WHEN ?=1 THEN ? ELSE hourly_price END,hourly_package_2=CASE WHEN ?=1 THEN ? ELSE hourly_package_2 END,hourly_package_4=CASE WHEN ?=1 THEN ? ELSE hourly_package_4 END,hourly_package_8=CASE WHEN ?=1 THEN ? ELSE hourly_package_8 END WHERE room_id=?";
                try (PreparedStatement stmt = conn.prepareStatement(query)) {
                    int index=1;
                    if (!code.isEmpty()) stmt.setString(index++,code);
                    stmt.setString(index++,name);
                    stmt.setString(index++,type);
                    stmt.setLong(index++,price);
                    stmt.setString(index++,status);
                    stmt.setString(index++,description);
                    stmt.setInt(index++,body.containsKey("hourlyPrice")?1:0);
                    stmt.setObject(index++,hourly,Types.DECIMAL);
                    boolean clear=body.containsKey("hourlyPrice") && hourly==null;
                    for(String key:HourlyPricing.KEYS) {
                        stmt.setInt(index++,body.containsKey("hourlyPackages")||clear?1:0);
                        stmt.setObject(index++,packages==null?null:packages.get(key),Types.DECIMAL);
                    }
                    stmt.setInt(index,id);
                    if (stmt.executeUpdate()!=1) throw new SQLException("Room update failed");
                }
                try(var check=conn.prepareStatement("SELECT hourly_price,hourly_package_2,hourly_package_4,hourly_package_8 FROM Rooms WHERE room_id=?")) {
                    check.setInt(1,id);try(var rs=check.executeQuery()){rs.next();if(HourlyPricing.packages(rs)!=null && rs.getBigDecimal("hourly_price")==null)throw new ApiProblem(400,"INVALID_INPUT","Nhập giá giờ lẻ khi sử dụng giá gói.");}
                }
                if(body.containsKey("nightlyPricing"))try(var st=conn.prepareStatement("UPDATE Rooms SET weekend_price=?,weekend_days=? WHERE room_id=?")) {
                    st.setObject(1,nightly==null?null:nightly.get("weekendPrice"),Types.DECIMAL);st.setString(2,NightlyPricing.days(nightly));st.setInt(3,id);st.executeUpdate();
                }
                if(body.containsKey("propertyId")) {
                    int propertyId=Input.integer(body,"propertyId",1,Integer.MAX_VALUE);PropertyController.require(conn,propertyId);
                    try(var st=conn.prepareStatement("UPDATE dbo.Rooms SET property_id=? WHERE room_id=?")){st.setInt(1,propertyId);st.setInt(2,id);st.executeUpdate();}
                }
                return Map.of("success",true,"message","Cập nhật phòng thành công!","status",status);
            },"Không thể cập nhật phòng. Vui lòng tải lại!");


        });
    }

    // NOTE API: DELETE /api/rooms/{id} xóa phòng chưa từng có booking.
    // Phòng có lịch sử đặt sẽ bị từ chối xóa để bảo toàn dữ liệu lưu trú và hóa đơn.
    @DeleteMapping("/{id}")
    public Map<String, Object> deleteRoom(@PathVariable int id) {
        return ApiErrors.guard(() -> {
            Input.id(id);
            return RoomSchedule.transaction(conn -> {
                RoomSchedule.lockRoom(conn,id);
                try (PreparedStatement stmt = conn.prepareStatement("SELECT booking_id FROM Bookings WHERE room_id=?")) {
                    stmt.setInt(1,id);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) throw new RoomSchedule.Conflict("Không thể xóa phòng đã có lịch sử đặt phòng!");
                    }
                }
                try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM Rooms WHERE room_id=?")) {
                    stmt.setInt(1,id);
                    if (stmt.executeUpdate()!=1) throw new SQLException("Room delete failed");
                }
                return Map.of("success",true,"message","Xóa phòng thành công!");
            },"Không thể xóa phòng!");


        });
    }
}
