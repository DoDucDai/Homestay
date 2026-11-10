# Homestay

Ứng dụng quản lý chuỗi homestay: phòng, khách hàng, đặt theo giờ/đêm,
nhận/trả phòng, dịch vụ, hóa đơn, báo cáo và trợ lý Mochi.

## Cấu trúc

- `backend/src/main`: Java 25, Spring Boot, JDBC và bảo mật.
- `frontend`: giao diện HTML/CSS/JavaScript, font và ảnh phòng.
- `database`: schema, migration và truy vấn SQL Server.
- `ai-rag`: dịch vụ Python, dữ liệu kiến thức và giao diện Mochi.
- `backend/src/test`, `ai-rag/tests`, `docker/tests`: mã kiểm thử.
- `backend/scripts`: build, chạy, kiểm thử và nhập dữ liệu demo.
- `contributions`: dữ liệu CSV dùng bởi công cụ nhập và kiểm thử.
- `docker`, `Dockerfile`, `compose.yaml`, `render.yaml`, `vercel.json`: triển khai.

## Chạy local

Cần JDK 25, SQL Server và Python 3.10 trở lên. Node.js dùng cho kiểm thử JavaScript.

1. Tạo `.env` theo `.env.example` và điền cấu hình SQL Server.
2. Khởi tạo database mới bằng `database/schema.sql`; với database hiện có,
   xem các migration trong `database/migrations` trước khi áp dụng.
3. Từ thư mục `backend`, chạy:

```powershell
.\mvnw.cmd "-Dmaven.repo.local=D:\Thuctap\.maven-cache" spring-boot:run
```

4. Từ thư mục dự án, chạy `start-mochi.cmd` trong cửa sổ khác.
5. Mở `http://localhost:8080`. Mochi dùng cổng nội bộ 8091.

## Build và kiểm thử

```powershell
# Từ backend
.\mvnw.cmd "-Dmaven.repo.local=D:\Thuctap\.maven-cache" package

# Từ thư mục dự án
powershell -File backend/scripts/build-release.ps1
python -m unittest discover -s ai-rag/tests -v
```

Kiểm thử backend tích hợp dùng SQL Server và database kiểm thử riêng.
Các script trong `backend/scripts` quản lý những nhóm kiểm thử tương ứng.

Hướng dẫn triển khai: [DEPLOY.md](DEPLOY.md).
Đăng ký khách hàng, xác thực email và khôi phục mật khẩu: [cấu hình Brevo](EMAIL_SETUP.md).
Thông tin chạy Mochi: [ai-rag/README.md](ai-rag/README.md).

Không commit `.env`, khóa API, mật khẩu, SQLite hoặc bản sao dữ liệu cá nhân.

Website chỉ có hai quyền: `admin` và `customer` (khách hàng). Khách hàng dùng chung các chức năng trước đây của nhân viên: đặt phòng, hồ sơ khách lưu trú, nhận/trả phòng, dịch vụ và thanh toán. Quản lý phòng, thống kê, xóa hồ sơ khách và quản trị AI dành cho admin. Khi backend khởi động, migration 009 chuyển tài khoản `staff`/`member` cũ sang `customer`, giữ nguyên mật khẩu và dữ liệu, đồng thời yêu cầu đăng nhập lại cho tài khoản đã đổi quyền. Không còn trang đặt phòng riêng cho member.
