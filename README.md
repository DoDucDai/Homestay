# 🏠 Homestay Management System

Dự án thực tập – Hệ thống quản lý Homestay
**Stack:** HTML/CSS/JS → Java Spring Boot → Microsoft SQL Server (JDBC)

---

## 👥 Nhóm phát triển

| Thành viên | Vai trò |
|---|---|
| Đức Đại (Leader) | Backend Java Spring Boot (tất cả API) + JavaScript toàn bộ Frontend |
| Sỹ Đại | Frontend HTML + CSS (tất cả các trang) |
| Long | Database SQL Server (Rooms, Bookings, Services) + query thống kê |
| Hưng | Database SQL Server (Users, Customers, Payments) + query thống kê |

---

## 📁 Cấu trúc dự án

```
homestay-app/
├── .env.example          ← Mẫu cấu hình (copy thành .env)
├── frontend/
│   ├── css/style.css
│   ├── pages/            ← 8 trang HTML
│   └── js/               ← JS xử lý từng trang
├── backend/              ← Java Spring Boot
│   ├── pom.xml
│   └── src/main/java/com/homestay/
│       ├── controller/   ← API endpoints
│       ├── db/           ← Kết nối JDBC
│       └── HomestayApplication.java
└── database/
    ├── schema.sql        ← Tạo bảng
    └── seed_data.sql     ← Dữ liệu mẫu
```

---

## ⚡ Hướng dẫn cài đặt và chạy

### Bước 1 – Clone dự án về máy

```bash
git clone https://github.com/DoDucDai/Homestay.git
cd Homestay
```

### Bước 2 – Tạo file `.env`

```bash
# Windows
copy .env.example .env
```

Mở file `.env` lên và **đổi mật khẩu SQL Server của máy bạn**:

```
DB_HOST=localhost
DB_PORT=1433
DB_NAME=HomestayDB
DB_USER=sa
DB_PASSWORD=mat_khau_cua_ban   <-- doi cai nay
```

### Bước 3 – Tạo Database (Long + Hưng làm)

1. Mở **SQL Server Management Studio (SSMS)**
2. Chạy file `database/schema.sql` → tạo database và 7 bảng
3. Chạy file `database/seed_data.sql` → thêm dữ liệu mẫu

### Bước 4 – Chạy Backend (Đức Đại làm)

Mở terminal trong thư mục `backend/`:

```bash
mvn spring-boot:run
```

> Backend chạy tại: `http://localhost:8080`
> Nếu lỗi, kiểm tra lại file `.env` và SQL Server đang chạy chưa.

### Bước 5 – Mở Frontend

Mở trực tiếp file `frontend/pages/login.html` bằng trình duyệt.

> ⚠️ Phải chạy Backend trước rồi mới mở Frontend.

---

## 🔧 Yêu cầu cài đặt

| Công cụ | Version |
|---|---|
| Java JDK | 17+ |
| Maven | 3.6+ |
| SQL Server | 2019+ (hoặc SQL Server Express) |
| SSMS | Bất kỳ |
| Trình duyệt | Chrome / Edge / Firefox |

---

## 🌐 API Endpoints

| Method | URL | Mô tả | Người làm BE |
|---|---|---|---|
| POST | `/api/auth/login` | Đăng nhập | Đức Đại |
| GET/POST/PUT/DELETE | `/api/rooms` | CRUD phòng | Đức Đại |
| GET/POST/PUT/DELETE | `/api/customers` | CRUD khách hàng | Đức Đại |
| GET/POST | `/api/bookings` | Booking + kiểm tra phòng trống | Đức Đại |
| PUT | `/api/checkin/{id}` | Check-in | Đức Đại |
| POST | `/api/checkout/{id}` | Check-out + tính tiền | Đức Đại |
| GET/POST/PUT/DELETE | `/api/services` | CRUD dịch vụ | Đức Đại |
| GET | `/api/statistics/overview` | Thống kê tổng hợp | Đức Đại (SQL: Long+Hưng) |
| GET | `/api/statistics/revenue` | Doanh thu theo tháng | Đức Đại (SQL: Hưng) |

---

## 🔄 Quy trình làm việc với Git

```bash
# Truoc khi bat dau code moi ngay, pull code moi nhat ve
git pull

# Sau khi code xong, push len
git add .
git commit -m "Mo ta nhung gi ban vua lam"
git push
```

> ⚠️ File `.env` đã được gitignore – **KHÔNG** bị push lên GitHub. Mỗi người tự tạo file `.env` trên máy mình.
