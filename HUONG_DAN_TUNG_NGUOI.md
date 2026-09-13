# 📚 HƯỚNG DẪN LÀM BÀI THEO TỪNG NGƯỜI

> Mỗi người đọc **đúng phần của mình**. Làm theo thứ tự từng bước, không bỏ qua.

---

# 👤 LONG – Database (Rooms, Bookings, Services, Booking_Services)

## Công cụ cần cài
- SQL Server (đã có)
- SQL Server Management Studio (SSMS)

## Nhiệm vụ của Long
1. Hoàn thiện `schema.sql` – các bảng **Rooms, Bookings, Services, Booking_Services**
2. Hoàn thiện `seed_data.sql` – dữ liệu mẫu cho các bảng đó
3. Viết SQL query cho **Thống kê phòng + Booking** (cung cấp cho Đức Đại dùng)

---

## BƯỚC 1 – Chạy Database

1. Mở **SSMS**, kết nối vào SQL Server
2. Mở file `database/schema.sql`, bôi đen tất cả → **F5** để chạy
3. Mở file `database/seed_data.sql`, bôi đen tất cả → **F5** để chạy
4. Kiểm tra bằng cách chạy:
```sql
USE HomestayDB;
SELECT * FROM Rooms;
SELECT * FROM Customers;
```
Nếu thấy dữ liệu là thành công ✅

---

## BƯỚC 2 – Viết SQL Query Thống Kê (cung cấp cho Đức Đại)

Mở SSMS, test từng query rồi **copy kết quả gửi cho Đức Đại** để Đức Đại dùng trong Java.

### Query 1 – Tổng số phòng theo trạng thái
```sql
USE HomestayDB;

SELECT
    COUNT(*) AS total_rooms,
    SUM(CASE WHEN status = N'Trống'          THEN 1 ELSE 0 END) AS available,
    SUM(CASE WHEN status = N'Đã đặt'         THEN 1 ELSE 0 END) AS booked,
    SUM(CASE WHEN status = N'Đang sử dụng'   THEN 1 ELSE 0 END) AS occupied,
    SUM(CASE WHEN status = N'Bảo trì'        THEN 1 ELSE 0 END) AS maintenance
FROM Rooms;
```

### Query 2 – Tổng booking tháng này
```sql
SELECT COUNT(*) AS total_bookings_this_month
FROM Bookings
WHERE MONTH(created_at) = MONTH(GETDATE())
  AND YEAR(created_at)  = YEAR(GETDATE());
```

### Query 3 – Thống kê phòng được đặt nhiều nhất
```sql
SELECT
    r.room_code,
    r.room_name,
    COUNT(b.booking_id) AS total_bookings
FROM Rooms r
LEFT JOIN Bookings b ON r.room_id = b.room_id
GROUP BY r.room_id, r.room_code, r.room_name
ORDER BY total_bookings DESC;
```

---

## BƯỚC 3 – Push Code Lên GitHub

```bash
git add .
git commit -m "Long: Hoan thien schema va seed data"
git push
```

---
---

# 👤 HƯNG – Database (Users, Customers, Payments) + Thống kê doanh thu

## Công cụ cần cài
- SQL Server + SSMS

## Nhiệm vụ của Hưng
1. Kiểm tra bảng **Users, Customers, Payments** trong `schema.sql`
2. Thêm/chỉnh dữ liệu mẫu trong `seed_data.sql`
3. Viết SQL query **Thống kê doanh thu + Khách hàng** (cung cấp cho Đức Đại)
4. Hỗ trợ viết báo cáo thực tập

---

## BƯỚC 1 – Kiểm Tra Database (Giống Long, chạy schema.sql + seed_data.sql)

---

## BƯỚC 2 – Viết SQL Query Thống Kê Doanh Thu

### Query 1 – Tổng doanh thu tháng này
```sql
SELECT
    COUNT(payment_id)    AS total_invoices,
    SUM(grand_total)     AS total_revenue
FROM Payments
WHERE MONTH(paid_at) = MONTH(GETDATE())
  AND YEAR(paid_at)  = YEAR(GETDATE())
  AND payment_status = N'Đã thanh toán';
```

### Query 2 – Doanh thu theo tháng/năm (Đức Đại sẽ truyền month, year vào)
```sql
-- Thay ? bằng số tháng và năm cụ thể khi test
SELECT
    p.payment_id,
    c.full_name     AS customer_name,
    r.room_code,
    p.room_total,
    p.service_total,
    p.grand_total,
    p.payment_method,
    p.paid_at
FROM Payments p
JOIN Bookings  b ON p.booking_id  = b.booking_id
JOIN Customers c ON b.customer_id = c.customer_id
JOIN Rooms     r ON b.room_id     = r.room_id
WHERE MONTH(p.paid_at) = 9    -- doi so nay
  AND YEAR(p.paid_at)  = 2026  -- doi so nay
ORDER BY p.paid_at DESC;
```

### Query 3 – Tổng số khách hàng
```sql
SELECT COUNT(*) AS total_customers FROM Customers;
```

### Query 4 – Top khách hàng đặt nhiều nhất
```sql
SELECT TOP 5
    c.full_name,
    c.phone,
    COUNT(b.booking_id) AS total_bookings
FROM Customers c
JOIN Bookings b ON c.customer_id = b.customer_id
GROUP BY c.customer_id, c.full_name, c.phone
ORDER BY total_bookings DESC;
```

---

## BƯỚC 3 – Push Code
```bash
git add .
git commit -m "Hung: Them query thong ke doanh thu"
git push
```

---
---

# 👤 SỸ ĐẠI – Frontend HTML + CSS

## Công cụ cần cài
- VS Code
- Extension: **Live Server** (để xem trang trực tiếp)

## Nhiệm vụ của Sỹ Đại
Làm HTML + CSS cho **8 trang** trong `frontend/pages/`. Không cần đụng vào JS.

---

## BƯỚC 1 – Clone và Mở Dự Án

```bash
git clone https://github.com/DoDucDai/Homestay.git
```

Mở VS Code → File → Open Folder → chọn thư mục `Homestay`

---

## BƯỚC 2 – Hiểu Cấu Trúc HTML Có Sẵn

Mỗi trang HTML đã có **khung sườn sẵn** gồm:
- Sidebar (menu trái)
- `main-content` (nội dung chính)
- Bảng dữ liệu
- Modal (hộp thoại)

**Sỹ Đại chỉ cần:** Làm cho nó trông đẹp hơn, thêm CSS nếu cần, KHÔNG xóa các `id` có sẵn vì JS dùng đến.

---

## BƯỚC 3 – Các Class CSS Đã Có Sẵn Trong `style.css`

```html
<!-- Layout -->
<div class="wrapper">
<nav class="sidebar">
<div class="main-content">
<div class="card">
<h1 class="page-title">

<!-- Nút bấm -->
<button class="btn btn-success">Xanh lá</button>
<button class="btn btn-primary">Xanh dương</button>
<button class="btn btn-warning">Vàng - Sửa</button>
<button class="btn btn-danger">Đỏ - Xóa</button>
<button class="btn btn-secondary">Xám - Hủy</button>

<!-- Badge trạng thái phòng -->
<span class="badge badge-available">Trống</span>
<span class="badge badge-booked">Đã đặt</span>
<span class="badge badge-occupied">Đang sử dụng</span>
<span class="badge badge-maintenance">Bảo trì</span>

<!-- Thông báo (JS tự điều khiển) -->
<div id="alert-message" class="alert" style="display:none;"></div>

<!-- Form -->
<div class="form-group">
  <label for="ten-input">Nhãn *</label>
  <input type="text" id="ten-input" placeholder="Nhập...">
</div>
```

---

## BƯỚC 4 – Làm Từng Trang Theo Thứ Tự

### Trang 1: `login.html` ✅ Đã có khung
- Không cần sửa nhiều, chỉ làm đẹp hơn nếu muốn

### Trang 2: `rooms.html` ← LÀM TRƯỚC TIÊN (module mẫu)
Kiểm tra đủ các phần:
```html
<!-- 1. Nút thêm phòng -->
<button class="btn btn-success" onclick="openAddModal()">+ Thêm phòng</button>

<!-- 2. Ô tìm kiếm -->
<input type="text" id="search-input" placeholder="Tìm kiếm...">

<!-- 3. Thông báo -->
<div id="alert-message" class="alert" style="display:none;"></div>

<!-- 4. Bảng - tbody có id để JS điền dữ liệu vào -->
<table>
  <thead>...</thead>
  <tbody id="rooms-tbody"></tbody>   ← QUAN TRỌNG: phải có id này
</table>

<!-- 5. Modal thêm/sửa - có id để JS mở/đóng -->
<div class="modal-overlay" id="room-modal">
  ...
  <input type="hidden" id="room-id">   ← Lưu id khi sửa
  <input type="text" id="room-code">
  ...
</div>
```

### Trang 3-8: `customers.html`, `bookings.html`, `checkin.html`, `checkout.html`, `services.html`, `statistics.html`
Tương tự `rooms.html`, chỉ thay nội dung phù hợp với module.

---

## BƯỚC 5 – Xem Trực Tiếp Trên Trình Duyệt

1. Chuột phải vào file `login.html`
2. Chọn **"Open with Live Server"**
3. Trình duyệt sẽ tự mở và cập nhật khi bạn lưu file

---

## BƯỚC 6 – Push Code
```bash
git pull                              # Lấy code mới nhất trước
git add frontend/
git commit -m "Sy Dai: Hoan thien giao dien trang rooms va customers"
git push
```

> ⚠️ **Lưu ý:** Không xóa bất kỳ `id` nào trong HTML. JS dùng các `id` đó để tìm và cập nhật nội dung.

---
---

# 👤 ĐỨC ĐẠI – Backend Java (Login, Room, Customer, Checkin, Services)

## Công cụ cần cài
- IntelliJ IDEA (hoặc VS Code + Extension Pack for Java)
- JDK 17
- Maven

## Nhiệm vụ của Đức Đại
Vào các file Controller, điền code vào các hàm có `// TODO: Duc Dai viet`

---

## BƯỚC 1 – Chuẩn Bị

```bash
git clone https://github.com/DoDucDai/Homestay.git
cd Homestay

# Tao file .env
copy .env.example .env
# Mo file .env, doi DB_PASSWORD thanh mat khau SQL Server cua may ban
```

---

## BƯỚC 2 – Mở Project Trong IntelliJ

1. Mở IntelliJ IDEA
2. File → Open → chọn thư mục `backend/`
3. Chờ IntelliJ tải Maven dependencies (lần đầu mất 2-3 phút)
4. Chạy `HomestayApplication.java` → chuột phải → **Run**
5. Nếu thấy `Started HomestayApplication` là thành công ✅

---

## BƯỚC 3 – Cách Viết Code Trong Controller

### Template chuẩn cho mọi hàm:

```java
@GetMapping
public List<Map<String, Object>> getAllRooms() {
    List<Map<String, Object>> result = new ArrayList<>();

    String sql = "SELECT * FROM Rooms ORDER BY room_code";

    try (Connection conn = DBConnection.getConnection();
         PreparedStatement stmt = conn.prepareStatement(sql);
         ResultSet rs = stmt.executeQuery()) {

        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("roomId",   rs.getInt("room_id"));
            row.put("roomCode", rs.getString("room_code"));
            row.put("roomName", rs.getString("room_name"));
            row.put("price",    rs.getLong("price"));
            row.put("status",   rs.getString("status"));
            result.add(row);
        }

    } catch (SQLException e) {
        e.printStackTrace();
    }

    return result;
}
```

### Template INSERT (thêm mới):

```java
@PostMapping
public Map<String, Object> createRoom(@RequestBody Map<String, Object> body) {
    String roomCode = (String) body.get("roomCode");
    String roomName = (String) body.get("roomName");
    int    price    = (int) body.get("price");

    String sql = "INSERT INTO Rooms (room_code, room_name, price) VALUES (?, ?, ?)";

    try (Connection conn = DBConnection.getConnection();
         PreparedStatement stmt = conn.prepareStatement(sql)) {

        stmt.setString(1, roomCode);
        stmt.setString(2, roomName);
        stmt.setInt(3, price);
        stmt.executeUpdate();

        return Map.of("success", true, "message", "Them phong thanh cong");

    } catch (SQLException e) {
        e.printStackTrace();
        return Map.of("success", false, "message", e.getMessage());
    }
}
```

### Template UPDATE (sửa):

```java
@PutMapping("/{id}")
public Map<String, Object> updateRoom(@PathVariable int id, @RequestBody Map<String, Object> body) {
    String roomName = (String) body.get("roomName");
    int    price    = (int) body.get("price");

    String sql = "UPDATE Rooms SET room_name=?, price=? WHERE room_id=?";

    try (Connection conn = DBConnection.getConnection();
         PreparedStatement stmt = conn.prepareStatement(sql)) {

        stmt.setString(1, roomName);
        stmt.setInt(2, price);
        stmt.setInt(3, id);
        stmt.executeUpdate();

        return Map.of("success", true);

    } catch (SQLException e) {
        e.printStackTrace();
        return Map.of("success", false, "message", e.getMessage());
    }
}
```

### Template DELETE (xóa):

```java
@DeleteMapping("/{id}")
public Map<String, Object> deleteRoom(@PathVariable int id) {
    String sql = "DELETE FROM Rooms WHERE room_id=?";

    try (Connection conn = DBConnection.getConnection();
         PreparedStatement stmt = conn.prepareStatement(sql)) {

        stmt.setInt(1, id);
        stmt.executeUpdate();
        return Map.of("success", true);

    } catch (SQLException e) {
        e.printStackTrace();
        return Map.of("success", false, "message", e.getMessage());
    }
}
```

---

## BƯỚC 4 – Test API Bằng Postman (hoặc trình duyệt)

Sau khi chạy backend, test từng API:

```
# Test GET rooms (dán thẳng vào trình duyệt)
http://localhost:8080/api/rooms

# Test POST (dùng Postman)
POST http://localhost:8080/api/rooms
Body (JSON): { "roomCode": "P999", "roomName": "Test", "price": 100000 }
```

---

## BƯỚC 5 – Thứ Tự Làm Các Controller

```
1. AuthController.java     → login (LÀM TRƯỚC TIÊN để test đăng nhập)
2. RoomController.java     → CRUD phòng (module mẫu)
3. CustomerController.java → CRUD khách hàng
4. BookingController.java  → chỉ làm hàm checkIn() (PUT /api/checkin/{id})
5. ServiceStatController.java → CRUD services + gọi query của Long/Hưng
```

---

## BƯỚC 6 – Push Code

```bash
git pull
git add backend/
git commit -m "Duc Dai: Hoan thien AuthController va RoomController"
git push
```

---
---

# 👤 LEADER – Backend khó + JavaScript toàn bộ Frontend

## Nhiệm vụ của Leader (làm nhiều nhất)
- **BE Java:** Booking API, Checkout/Payment API, Statistics API
- **FE JS:** Toàn bộ file `.js` trong `frontend/js/`
- **Tích hợp:** Kiểm tra end-to-end, review code Đức Đại

---

## BƯỚC 1 – Làm Backend Phần Booking

File: `BookingController.java`

### Hàm `getBookings` – Lấy danh sách booking
```java
@GetMapping("/bookings")
public List<Map<String, Object>> getBookings(@RequestParam(required = false) String status) {
    List<Map<String, Object>> result = new ArrayList<>();

    String sql = """
        SELECT b.booking_id, b.checkin_date, b.checkout_date, b.status, b.note,
               c.full_name AS customer_name, c.phone,
               r.room_code, r.room_name, r.price
        FROM Bookings b
        JOIN Customers c ON b.customer_id = c.customer_id
        JOIN Rooms r ON b.room_id = r.room_id
        """;

    if (status != null && !status.isEmpty()) {
        sql += " WHERE b.status = ?";
    }
    sql += " ORDER BY b.created_at DESC";

    try (Connection conn = DBConnection.getConnection();
         PreparedStatement stmt = conn.prepareStatement(sql)) {

        if (status != null && !status.isEmpty()) {
            stmt.setString(1, status);
        }

        ResultSet rs = stmt.executeQuery();
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("bookingId",    rs.getInt("booking_id"));
            row.put("customerName", rs.getString("customer_name"));
            row.put("phone",        rs.getString("phone"));
            row.put("roomCode",     rs.getString("room_code"));
            row.put("roomName",     rs.getString("room_name"));
            row.put("price",        rs.getLong("price"));
            row.put("checkinDate",  rs.getString("checkin_date"));
            row.put("checkoutDate", rs.getString("checkout_date"));
            row.put("status",       rs.getString("status"));
            row.put("note",         rs.getString("note"));
            result.add(row);
        }
    } catch (SQLException e) { e.printStackTrace(); }

    return result;
}
```

### Hàm `createBooking` – Tạo booking mới
```java
@PostMapping("/bookings")
public Map<String, Object> createBooking(@RequestBody Map<String, Object> body) {
    int    customerId    = (int) body.get("customerId");
    int    roomId        = (int) body.get("roomId");
    String checkinDate   = (String) body.get("checkinDate");
    String checkoutDate  = (String) body.get("checkoutDate");
    String note          = (String) body.getOrDefault("note", "");

    try (Connection conn = DBConnection.getConnection()) {

        // 1. Kiem tra phong co trong khong
        String checkSql = "SELECT status FROM Rooms WHERE room_id=?";
        PreparedStatement checkStmt = conn.prepareStatement(checkSql);
        checkStmt.setInt(1, roomId);
        ResultSet rs = checkStmt.executeQuery();
        if (rs.next() && !rs.getString("status").equals("Trống")) {
            return Map.of("success", false, "message", "Phong nay khong con trong!");
        }

        // 2. Tao booking
        String insertSql = "INSERT INTO Bookings (customer_id, room_id, checkin_date, checkout_date, note) VALUES (?,?,?,?,?)";
        PreparedStatement insertStmt = conn.prepareStatement(insertSql);
        insertStmt.setInt(1, customerId);
        insertStmt.setInt(2, roomId);
        insertStmt.setString(3, checkinDate);
        insertStmt.setString(4, checkoutDate);
        insertStmt.setString(5, note);
        insertStmt.executeUpdate();

        // 3. Cap nhat trang thai phong
        String updateSql = "UPDATE Rooms SET status=N'Đã đặt' WHERE room_id=?";
        PreparedStatement updateStmt = conn.prepareStatement(updateSql);
        updateStmt.setInt(1, roomId);
        updateStmt.executeUpdate();

        return Map.of("success", true, "message", "Tao booking thanh cong");

    } catch (SQLException e) {
        e.printStackTrace();
        return Map.of("success", false, "message", e.getMessage());
    }
}
```

---

## BƯỚC 2 – Làm Backend Phần Checkout

```java
@PostMapping("/checkout/{bookingId}")
public Map<String, Object> checkOut(@PathVariable int bookingId, @RequestBody Map<String, Object> body) {
    String paymentMethod = (String) body.getOrDefault("paymentMethod", "Tiền mặt");

    try (Connection conn = DBConnection.getConnection()) {

        // 1. Lay thong tin booking va phong
        String infoSql = """
            SELECT b.checkin_date, b.checkout_date, b.room_id, r.price
            FROM Bookings b JOIN Rooms r ON b.room_id = r.room_id
            WHERE b.booking_id = ?
            """;
        PreparedStatement infoStmt = conn.prepareStatement(infoSql);
        infoStmt.setInt(1, bookingId);
        ResultSet rs = infoStmt.executeQuery();

        if (!rs.next()) return Map.of("success", false, "message", "Khong tim thay booking");

        long   price        = rs.getLong("price");
        int    roomId       = rs.getInt("room_id");
        String checkinDate  = rs.getString("checkin_date");
        String checkoutDate = rs.getString("checkout_date");

        // 2. Tinh so dem va tien phong
        long nights    = java.time.temporal.ChronoUnit.DAYS.between(
                            java.time.LocalDate.parse(checkinDate),
                            java.time.LocalDate.parse(checkoutDate));
        long roomTotal = price * nights;

        // 3. Tinh tien dich vu
        String svcSql  = "SELECT ISNULL(SUM(total_price),0) AS svc FROM Booking_Services WHERE booking_id=?";
        PreparedStatement svcStmt = conn.prepareStatement(svcSql);
        svcStmt.setInt(1, bookingId);
        ResultSet svcRs   = svcStmt.executeQuery();
        long serviceTotal = svcRs.next() ? svcRs.getLong("svc") : 0;
        long grandTotal   = roomTotal + serviceTotal;

        // 4. Tao hoa don
        String paymentSql = "INSERT INTO Payments (booking_id, room_total, service_total, grand_total, payment_method) VALUES (?,?,?,?,?)";
        PreparedStatement payStmt = conn.prepareStatement(paymentSql);
        payStmt.setInt(1, bookingId);
        payStmt.setLong(2, roomTotal);
        payStmt.setLong(3, serviceTotal);
        payStmt.setLong(4, grandTotal);
        payStmt.setString(5, paymentMethod);
        payStmt.executeUpdate();

        // 5. Cap nhat trang thai
        conn.prepareStatement("UPDATE Bookings SET status='CheckedOut' WHERE booking_id=" + bookingId).executeUpdate();
        conn.prepareStatement("UPDATE Rooms SET status=N'Trống' WHERE room_id=" + roomId).executeUpdate();

        return Map.of("success", true,
                      "roomTotal",     roomTotal,
                      "serviceTotal",  serviceTotal,
                      "grandTotal",    grandTotal,
                      "nights",        nights);

    } catch (Exception e) {
        e.printStackTrace();
        return Map.of("success", false, "message", e.getMessage());
    }
}
```

---

## BƯỚC 3 – Làm JavaScript (sau khi Đức Đại xong API)

### Cấu trúc chuẩn cho mỗi file JS:

```javascript
// ==============================
// rooms.js
// ==============================

// Chạy khi trang load xong
document.addEventListener('DOMContentLoaded', () => {
    checkLogin();    // Kiểm tra đăng nhập
    loadRooms();     // Tải dữ liệu
});

// Load danh sách
async function loadRooms() {
    try {
        const rooms = await apiGet('/rooms');
        const tbody = document.getElementById('rooms-tbody');
        tbody.innerHTML = '';
        rooms.forEach(room => {
            tbody.innerHTML += `
                <tr>
                    <td>${room.roomCode}</td>
                    <td>${room.roomName}</td>
                    <td>${room.roomType}</td>
                    <td>${formatMoney(room.price)}</td>
                    <td><span class="badge badge-available">${room.status}</span></td>
                    <td>
                        <button class="btn btn-warning" onclick="openEditModal(${room.roomId})">Sửa</button>
                        <button class="btn btn-danger"  onclick="confirmDelete(${room.roomId})">Xóa</button>
                    </td>
                </tr>`;
        });
    } catch {
        showAlert('Lỗi tải dữ liệu!', 'error');
    }
}

// Mở modal thêm mới
function openAddModal() {
    document.getElementById('modal-title').textContent = 'Thêm Phòng Mới';
    document.getElementById('room-id').value   = '';
    document.getElementById('room-code').value = '';
    document.getElementById('room-name').value = '';
    document.getElementById('room-price').value = '';
    openModal('room-modal');
}

// Mở modal sửa
async function openEditModal(id) {
    const rooms = await apiGet('/rooms');
    const room  = rooms.find(r => r.roomId === id);
    document.getElementById('modal-title').textContent  = 'Sửa Phòng';
    document.getElementById('room-id').value    = room.roomId;
    document.getElementById('room-code').value  = room.roomCode;
    document.getElementById('room-name').value  = room.roomName;
    document.getElementById('room-price').value = room.price;
    openModal('room-modal');
}

// Lưu (thêm hoặc sửa)
async function saveRoom() {
    const id    = document.getElementById('room-id').value;
    const code  = document.getElementById('room-code').value.trim();
    const name  = document.getElementById('room-name').value.trim();
    const price = document.getElementById('room-price').value;

    if (!code || !name || !price) {
        showAlert('Vui lòng nhập đủ thông tin!', 'error');
        return;
    }

    const data   = { roomCode: code, roomName: name, price: Number(price) };
    const result = id
        ? await apiPut(`/rooms/${id}`, data)
        : await apiPost('/rooms', data);

    if (result.success) {
        showAlert(id ? 'Cập nhật thành công!' : 'Thêm thành công!', 'success');
        closeModal();
        loadRooms();
    } else {
        showAlert(result.message, 'error');
    }
}

// Xóa
async function confirmDelete(id) {
    if (!confirm('Bạn có chắc muốn xóa phòng này?')) return;
    const result = await apiDelete(`/rooms/${id}`);
    if (result.success) {
        showAlert('Đã xóa phòng!', 'success');
        loadRooms();
    }
}
```

---

## BƯỚC 4 – Thứ Tự Làm JS

```
1. api.js      ← Đã xong, không cần sửa
2. auth.js     ← Đã xong
3. rooms.js    ← Làm đầu tiên (sau khi Đức Đại xong RoomController)
4. customers.js← Tương tự rooms.js
5. bookings.js ← Sau khi tự làm xong Booking API
6. checkin.js  ← Sau khi Đức Đại xong Checkin API
7. checkout.js ← Sau khi tự làm xong Checkout API
8. services.js ← Sau khi Đức Đại xong Services API
9. statistics.js ← Cuối cùng
```

---

## BƯỚC 5 – Push Code

```bash
git pull
git add .
git commit -m "Leader: Hoan thien BookingController + rooms.js + customers.js"
git push
```

---

# ⚠️ QUY TẮC CHUNG CHO CẢ NHÓM

> Đọc và nhớ 5 điều này để tránh conflict code

1. **`git pull` trước khi bắt đầu code mỗi ngày** – tránh bị conflict
2. **Chỉ sửa file trong phần của mình** – không đụng vào file của người khác
3. **Commit thường xuyên** – sau mỗi hàm/trang làm xong, commit luôn
4. **Không xóa `id` trong HTML** – JS dùng các id đó để tìm element
5. **Test trước khi push** – chạy thử, kiểm tra không có lỗi rõ ràng

```bash
# Quy trình làm việc mỗi ngày
git pull                          # 1. Lấy code mới
# ... code ...
git add .                         # 2. Stage file
git commit -m "Mô tả việc làm"   # 3. Commit
git push                          # 4. Push lên
```
