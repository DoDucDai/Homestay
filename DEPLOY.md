# Triển khai Homestay và Mochi

## Tài khoản demo tùy chọn

Mặc định không tạo tài khoản demo. Khi được phép chạy bản demo, đặt `DEMO_ACCOUNTS_ENABLED=true` và `DEMO_ACCOUNT_PASSWORD` trong Environment của Render (hoặc biến môi trường của tiến trình local). Hai biến này không được nạp từ file `.env` local.

Ứng dụng tạo `khachdemo` với vai trò `customer` và `admindemo` với vai trò `admin` nếu chưa tồn tại. Mật khẩu được băm PBKDF2; khởi động lại không đổi mật khẩu, quyền hay thông tin của tài khoản có sẵn. Nếu tên đã thuộc vai trò khác, khởi động thất bại và giao dịch được hoàn tác.

Bản demo theo yêu cầu cho phép mật khẩu `123456` riêng cho hai tên này khi bật cờ. Trước khi tắt cờ, đổi mật khẩu cả hai tài khoản sang mật khẩu mạnh theo quy tắc ứng dụng; nếu còn mật khẩu `123456`, kiểm tra bảo vệ production sẽ từ chối khởi động. Đăng ký công khai vẫn giữ quy tắc mật khẩu hiện tại.

Kiểm thử `DemoAccountsSqlServerIT` dùng database tạm, kiểm tra tạo đúng hai tài khoản, đăng nhập bằng Spring, giữ nguyên tài khoản cũ và từ chối xung đột vai trò; không chạy kiểm thử trên database live.

## Docker local

1. Tạo `.env.docker` theo `.env.docker.example`, điền SQL Server và cấu hình AI.
2. Chạy từ thư mục dự án:

```powershell
docker compose up --build -d
```

Web chạy tại `http://localhost:8080`. Compose dùng volume `rag-data` để lưu SQLite.
SQL Server được cấu hình riêng; Dockerfile không tự tạo database nghiệp vụ.

## Render

- Cấu hình dịch vụ nằm trong `render.yaml`. `Dockerfile` và `Dockerfile.render` đều build trực tiếp từ mã nguồn Java 25 ở root dự án; file thứ hai giữ tương thích đường dẫn đang được service hiện tại sử dụng.
- Khai báo `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` và
  `OPENROUTER_API_KEY` trong biến môi trường của dịch vụ.
- Giữ các cấu hình TLS, cookie và profile `prod` theo `render.yaml`.
- Health check: `/api/health`.
- Tài khoản khách hàng dùng Brevo HTTPS API; khai báo các biến email theo [EMAIL_SETUP.md](EMAIL_SETUP.md) trước khi kiểm tra gửi mã/khôi phục mật khẩu thật.
- `.env` local không được đồng bộ lên Render. Cấu hình `MAIL_PROVIDER`, `MAIL_API_KEY`, `MAIL_FROM`, `MAIL_FROM_NAME`, `AUTH_TOKEN_SECRET`, `APP_PUBLIC_URL` và `EMAIL_VERIFICATION_ENABLED` tại Environment của đúng service; không thay cấu hình DB đang có.
- Service `srv-dbkbpvjbc2fs73f0hh6g` theo nhánh `deploy/free-demo-20261102`. Bản cập nhật ngày 10/11/2026 chuyển nội dung `Dockerfile.render` từ giải nén archive Java 17 sang build nguồn Java 25; không cần đổi nhánh hay đường dẫn Dockerfile trong Settings. Các archive cũ nếu còn trong Git được `.dockerignore` loại khỏi image/build context và không còn là nguồn build.
- `docker/start.py` khởi động Java và Python, nạp `ai-rag/knowledge/published.json`.
- Nếu dùng filesystem tạm, thay đổi SQLite không được giữ sau khi tạo lại container.
  Cần lưu bền vững hoặc sao lưu nếu muốn giữ chỉnh sửa tài liệu và yêu cầu Support.

## Vercel

`vercel.json` chuyển tiếp web/API tới dịch vụ Render. Khi đổi origin Render,
cập nhật địa chỉ rewrite trong tệp này.

## Kiểm tra sau triển khai

Kiểm tra health, danh mục và ảnh phòng, đăng nhập, lọc cơ sở và Mochi.
Kiểm thử nghiệp vụ ghi dữ liệu trên môi trường riêng trước khi triển khai.

Không đưa `.env`, SQLite, mật khẩu hoặc khóa API vào Git hay Docker image.
