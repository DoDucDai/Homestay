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

### Đặt phòng và thanh toán

Khách dùng **Đặt phòng** để chọn thời gian, phòng, dịch vụ và thanh toán tại quầy
hoặc chuyển khoản; **Đặt phòng của tôi** giữ mọi đơn, kể cả chưa thanh toán.
Admin kiểm tra tiền thực nhận trong **Xác nhận thu tiền**, sau đó mới nhận/trả phòng.
Thu tiền lưu một hóa đơn; trả phòng sử dụng lại hóa đơn đó.

Thông tin chuyển khoản lấy từ `TRANSFER_BANK_NAME`, `TRANSFER_ACCOUNT_NUMBER`,
`TRANSFER_ACCOUNT_NAME`. Khi thiếu cấu hình, giao diện yêu cầu liên hệ nhân viên.
QR chứa thông tin để đọc bằng trình quét QR; chưa tích hợp VietQR/ứng dụng ngân hàng.
Nút **Tôi đã chuyển khoản** chỉ gửi yêu cầu đối soát; admin xác nhận mới có hóa đơn.
`TRANSFER_HOLD_MINUTES` mặc định 15; `TRANSFER_REVIEW_WARNING_MINUTES` mặc định 30.

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

Website có hai quyền: `admin` và `customer`. Backend mới giới hạn các API vận hành chung cho admin; khách dùng `/api/customer/...` với dữ liệu thuộc tài khoản đang đăng nhập. Migration 009 vẫn chuyển quyền cũ về customer, migration 010 bổ sung thông tin giữ phòng/thanh toán; không tự gán các hồ sơ cũ cho tài khoản chỉ vì trùng tên hoặc email.

Khu khách dùng các trang đặt phòng, đặt phòng của tôi và hóa đơn cá nhân; admin xử lý xác nhận thu tiền riêng.
Hợp đồng endpoint: [API khách hàng](backend/CUSTOMER_API.md).
Các luồng email đã được kiểm thử local qua HTTP/SQL trên database riêng; xem [kết quả kiểm thử email](KIEM_THU_EMAIL.md).
