# 📖 TÀI LIỆU DỰ ÁN – HOMESTAY MANAGEMENT SYSTEM

> Đọc file này trước khi bắt đầu code. Nó giải thích toàn bộ cấu trúc, file nào làm gì, hàm nào dùng như thế nào.

---

## 1. CẤU TRÚC THƯ MỤC

```
homestay-app/
│
├── .env                  ← Config local CỦA BẠN (không lên GitHub)
├── .env.example          ← Mẫu .env, copy ra làm .env
├── README.md             ← Hướng dẫn cài đặt và chạy
│
├── frontend/
│   ├── css/
│   │   └── style.css     ← CSS DÙNG CHUNG cho tất cả trang (Sỹ Đại làm)
│   │
│   ├── pages/            ← CÁC TRANG HTML (Sỹ Đại làm HTML/CSS)
│   │   ├── login.html
│   │   ├── rooms.html
│   │   ├── customers.html
│   │   ├── bookings.html
│   │   ├── checkin.html
│   │   ├── checkout.html
│   │   ├── services.html
│   │   └── statistics.html
│   │
│   └── js/               ← XỬ LÝ LOGIC (Leader + từng người tùy module)
│       ├── api.js         ← HÀM FETCH DÙNG CHUNG – ĐỌC KỸ PHẦN 3
│       ├── auth.js        ← Xử lý đăng nhập (Leader)
│       ├── rooms.js       ← Xử lý trang phòng (Leader)
│       ├── customers.js   ← Xử lý trang khách hàng (Leader)
│       ├── bookings.js    ← Xử lý trang booking (Leader)
│       ├── checkin.js     ← Xử lý trang check-in (Leader)
│       ├── checkout.js    ← Xử lý trang check-out (Leader)
│       ├── services.js    ← Xử lý trang dịch vụ (Leader)
│       └── statistics.js  ← Xử lý trang thống kê (Leader)
│
├── backend/
│   ├── pom.xml            ← Khai báo thư viện Java (không cần sửa)
│   └── src/main/
│       ├── java/com/homestay/
│       │   ├── HomestayApplication.java   ← Điểm chạy của backend (không sửa)
│       │   ├── db/
│       │   │   └── DBConnection.java      ← Kết nối SQL Server (không sửa)
│       │   └── controller/               ← API ENDPOINTS (Đức Đại + Leader)
│       │       ├── AuthController.java
│       │       ├── RoomController.java
│       │       ├── CustomerController.java
│       │       ├── BookingController.java
│       │       └── ServiceStatController.java
│       └── resources/
│           └── application.properties    ← Config Spring Boot (không sửa)
│
└── database/
    ├── schema.sql         ← Tạo 7 bảng trong SQL Server (Long + Hưng)
    └── seed_data.sql      ← Dữ liệu mẫu để test (Long + Hưng)
```

---

## 2. DATABASE – 7 BẢNG

```
Users          → Tài khoản đăng nhập
Rooms          → Danh sách phòng
Customers      → Khách hàng
Bookings       → Đặt phòng (nối Customers + Rooms)
Services       → Các dịch vụ (giặt ủi, thuê xe...)
Booking_Services → Dịch vụ của từng booking
Payments       → Hóa đơn thanh toán
```

### Quan hệ giữa các bảng:
```
Customers ──┐
            ├──► Bookings ──► Booking_Services ──► Services
Rooms ──────┘       │
                    └──► Payments
```

### Trạng thái phòng (Rooms.status):
| Giá trị | Ý nghĩa |
|---|---|
| `Trống` | Phòng có thể đặt |
| `Đã đặt` | Đã có booking, chờ check-in |
| `Đang sử dụng` | Đã check-in |
| `Bảo trì` | Không cho đặt |

### Trạng thái booking (Bookings.status):
| Giá trị | Ý nghĩa |
|---|---|
| `Confirmed` | Đã đặt, chờ check-in |
| `CheckedIn` | Đang ở |
| `CheckedOut` | Đã trả phòng |
| `Cancelled` | Hủy |

---

## 3. CÁC HÀM DÙNG CHUNG TRONG `api.js`

> ⚠️ Mọi file JS đều phải dùng các hàm này thay vì tự viết fetch. File `api.js` được load trước nên tất cả hàm đều dùng được.

### 3.1 Gọi API

```javascript
// Lấy dữ liệu (GET)
const data = await apiGet('/rooms');
// Tương đương: GET http://localhost:8080/api/rooms

// Tạo mới (POST)
const result = await apiPost('/rooms', { roomCode: 'P101', roomName: 'Phòng 101', price: 300000 });

// Cập nhật (PUT)
const result = await apiPut('/rooms/5', { roomName: 'Phòng mới', price: 400000 });

// Xóa (DELETE)
const result = await apiDelete('/rooms/5');
```

### 3.2 Hiển thị thông báo

```javascript
// Thông báo thành công (màu xanh, tự mất sau 3 giây)
showAlert('Thêm phòng thành công!', 'success');

// Thông báo lỗi (màu đỏ)
showAlert('Vui lòng nhập đủ thông tin!', 'error');

// Thông báo thông tin (màu xanh dương)
showAlert('Đang tải dữ liệu...', 'info');
```
> ⚠️ Trang HTML phải có thẻ: `<div id="alert-message" class="alert" style="display:none;"></div>`

### 3.3 Mở / Đóng Modal

```javascript
// Mở modal (truyền vào id của modal-overlay)
openModal('room-modal');

// Đóng tất cả modal
closeModal();
```

### 3.4 Định dạng hiển thị

```javascript
// Định dạng tiền VNĐ
formatMoney(500000)        // → "500,000 VNĐ"
formatMoney(1200000)       // → "1,200,000 VNĐ"

// Định dạng ngày tháng
formatDate('2026-09-15')   // → "15/09/2026"
formatDate(null)           // → "--"
```

### 3.5 Kiểm tra đăng nhập

```javascript
// Gọi đầu mỗi trang (nếu chưa đăng nhập → tự chuyển về login.html)
const user = checkLogin();
// user = { userId: 1, username: "admin", fullName: "Nguyễn Văn Admin", role: "admin" }
```

---

## 4. API ENDPOINTS – DANH SÁCH ĐẦY ĐỦ

> Backend chạy tại: `http://localhost:8080`
> Tất cả API đều có prefix `/api`

### Auth
| Method | URL | Mô tả | Người làm |
|---|---|---|---|
| POST | `/api/auth/login` | Đăng nhập | Đức Đại |

### Rooms
| Method | URL | Mô tả | Người làm |
|---|---|---|---|
| GET | `/api/rooms` | Lấy danh sách tất cả phòng | Đức Đại |
| POST | `/api/rooms` | Thêm phòng mới | Đức Đại |
| PUT | `/api/rooms/{id}` | Sửa phòng | Đức Đại |
| DELETE | `/api/rooms/{id}` | Xóa phòng | Đức Đại |

### Customers
| Method | URL | Mô tả | Người làm |
|---|---|---|---|
| GET | `/api/customers` | Lấy danh sách khách hàng | Đức Đại |
| POST | `/api/customers` | Thêm khách hàng | Đức Đại |
| PUT | `/api/customers/{id}` | Sửa khách hàng | Đức Đại |
| DELETE | `/api/customers/{id}` | Xóa khách hàng | Đức Đại |

### Booking / Checkin / Checkout
| Method | URL | Mô tả | Người làm |
|---|---|---|---|
| GET | `/api/bookings?status=X` | Lấy booking theo trạng thái | Leader |
| POST | `/api/bookings` | Tạo booking mới | Leader |
| PUT | `/api/checkin/{id}` | Thực hiện check-in | Đức Đại |
| POST | `/api/checkout/{id}` | Thực hiện check-out + tạo hóa đơn | Leader |

### Services
| Method | URL | Mô tả | Người làm |
|---|---|---|---|
| GET | `/api/services` | Lấy danh sách dịch vụ | Đức Đại |
| POST | `/api/services` | Thêm dịch vụ | Đức Đại |
| PUT | `/api/services/{id}` | Sửa dịch vụ | Đức Đại |
| DELETE | `/api/services/{id}` | Xóa dịch vụ | Đức Đại |

### Statistics
| Method | URL | Mô tả | Người làm |
|---|---|---|---|
| GET | `/api/statistics/overview` | Tổng hợp: phòng, khách, doanh thu | Đức Đại (SQL: Long+Hưng) |
| GET | `/api/statistics/revenue?month=9&year=2026` | Doanh thu theo tháng | Đức Đại (SQL: Hưng) |

---

## 5. CSS CLASSES SẴN CÓ TRONG `style.css`

> Sỹ Đại dùng các class này khi làm HTML, không cần viết CSS thêm.

### Layout
```html
<div class="wrapper">          <!-- Bọc toàn bộ trang -->
<nav class="sidebar">          <!-- Thanh menu bên trái -->
<div class="main-content">     <!-- Nội dung chính bên phải -->
<div class="card">             <!-- Khung trắng có shadow -->
<h1 class="page-title">        <!-- Tiêu đề trang -->
```

### Bảng dữ liệu
```html
<table>
  <thead><tr><th>Cột 1</th></tr></thead>
  <tbody id="data-tbody"></tbody>   <!-- JS sẽ điền dữ liệu vào đây -->
</table>
```

### Nút bấm
```html
<button class="btn btn-primary">Xanh dương</button>
<button class="btn btn-success">Xanh lá – Thêm mới</button>
<button class="btn btn-warning">Vàng – Sửa</button>
<button class="btn btn-danger">Đỏ – Xóa</button>
<button class="btn btn-secondary">Xám – Hủy</button>
```

### Form nhập liệu
```html
<div class="form-group">
  <label for="input-id">Nhãn *</label>
  <input type="text" id="input-id" placeholder="Nhập...">
</div>
```

### Modal (hộp thoại popup)
```html
<div class="modal-overlay" id="ten-modal">
  <div class="modal-box">
    <h3>Tiêu đề</h3>
    <!-- nội dung form -->
    <div class="modal-footer">
      <button class="btn btn-secondary" onclick="closeModal()">Hủy</button>
      <button class="btn btn-primary" onclick="luuDuLieu()">Lưu</button>
    </div>
  </div>
</div>
```

### Badge trạng thái
```html
<span class="badge badge-available">Trống</span>
<span class="badge badge-booked">Đã đặt</span>
<span class="badge badge-occupied">Đang sử dụng</span>
<span class="badge badge-maintenance">Bảo trì</span>
<span class="badge badge-paid">Đã thanh toán</span>
<span class="badge badge-unpaid">Chưa thanh toán</span>
```

### Thông báo
```html
<!-- Thêm vào HTML, JS sẽ tự điều khiển hiển thị -->
<div id="alert-message" class="alert" style="display:none;"></div>
```

---

## 6. VÍ DỤ CODE MẪU

### Ví dụ: Lấy danh sách phòng và render vào bảng (JS)

```javascript
// Trong rooms.js
async function loadRooms() {
    try {
        const rooms = await apiGet('/rooms');
        const tbody = document.getElementById('rooms-tbody');
        tbody.innerHTML = '';

        rooms.forEach(room => {
            const row = `
                <tr>
                    <td>${room.roomCode}</td>
                    <td>${room.roomName}</td>
                    <td>${room.roomType}</td>
                    <td>${formatMoney(room.price)}</td>
                    <td><span class="badge badge-available">${room.status}</span></td>
                    <td>
                        <button class="btn btn-warning" onclick="openEdit(${room.roomId})">Sửa</button>
                        <button class="btn btn-danger"  onclick="deleteRoom(${room.roomId})">Xóa</button>
                    </td>
                </tr>`;
            tbody.innerHTML += row;
        });
    } catch (err) {
        showAlert('Không tải được dữ liệu!', 'error');
    }
}

// Gọi khi trang load xong
document.addEventListener('DOMContentLoaded', () => {
    checkLogin();   // Kiểm tra đăng nhập
    loadRooms();    // Tải danh sách phòng
});
```

### Ví dụ: Thêm phòng mới (JS)

```javascript
async function saveRoom() {
    const roomCode = document.getElementById('room-code').value.trim();
    const roomName = document.getElementById('room-name').value.trim();
    const price    = document.getElementById('room-price').value;

    if (!roomCode || !roomName || !price) {
        showAlert('Vui lòng nhập đủ thông tin!', 'error');
        return;
    }

    try {
        const result = await apiPost('/rooms', { roomCode, roomName, price: Number(price) });
        if (result.success) {
            showAlert('Thêm phòng thành công!', 'success');
            closeModal();
            loadRooms(); // Tải lại bảng
        } else {
            showAlert(result.message, 'error');
        }
    } catch (err) {
        showAlert('Lỗi kết nối server!', 'error');
    }
}
```

### Ví dụ: GET request với tham số (JS)

```javascript
// Lấy booking có status = Confirmed
const bookings = await apiGet('/bookings?status=Confirmed');

// Lấy doanh thu tháng 9 năm 2026
const revenue = await apiGet('/statistics/revenue?month=9&year=2026');
```

### Ví dụ: Controller Java lấy dữ liệu từ DB (Java)

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
            row.put("roomId",      rs.getInt("room_id"));
            row.put("roomCode",    rs.getString("room_code"));
            row.put("roomName",    rs.getString("room_name"));
            row.put("roomType",    rs.getString("room_type"));
            row.put("price",       rs.getLong("price"));
            row.put("status",      rs.getString("status"));
            row.put("description", rs.getString("description"));
            result.add(row);
        }
    } catch (SQLException e) {
        e.printStackTrace();
    }

    return result;
}
```

---

## 7. QUY TẮC CODE CHUNG

### Đặt tên
| Loại | Quy tắc | Ví dụ |
|---|---|---|
| Java class | PascalCase | `RoomController`, `DBConnection` |
| Java method | camelCase | `getAllRooms()`, `createBooking()` |
| JS function | camelCase | `loadRooms()`, `saveCustomer()` |
| JS variable | camelCase | `roomId`, `customerName` |
| HTML id | kebab-case | `room-code`, `booking-modal` |
| CSS class | kebab-case | `btn-primary`, `modal-overlay` |

### JSON response từ API (quy tắc chung)
```json
// Khi thành công
{ "success": true, "message": "..." }

// Khi thất bại
{ "success": false, "message": "Mô tả lỗi" }

// Khi trả về danh sách
[ { ... }, { ... } ]
```

### Thứ tự load file trong HTML
```html
<!-- Cuối thẻ </body>, luôn load api.js TRƯỚC file JS của trang -->
<script src="../js/api.js"></script>
<script src="../js/rooms.js"></script>   ← File JS của trang này
```

---

## 8. LỖI THƯỜNG GẶP & CÁCH XỬ LÝ

| Lỗi | Nguyên nhân | Cách sửa |
|---|---|---|
| `Failed to fetch` | Backend chưa chạy | Chạy `mvn spring-boot:run` trong thư mục `backend/` |
| `CORS error` | Thiếu `@CrossOrigin` | Đảm bảo mọi Controller đều có `@CrossOrigin(origins = "*")` |
| `Connection refused` | SQL Server chưa chạy | Mở SQL Server Configuration Manager, start service |
| `Login failed for user 'sa'` | Sai password trong `.env` | Mở file `.env`, sửa lại `DB_PASSWORD` |
| Tiếng Việt bị lỗi `???` | Query thiếu `N''` | Dùng `N'Trống'` thay vì `'Trống'` trong SQL Server |
