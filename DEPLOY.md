# Triển khai Homestay và Mochi

## Docker local

1. Tạo `.env.docker` theo `.env.docker.example`, điền SQL Server và cấu hình AI.
2. Chạy từ thư mục dự án:

```powershell
docker compose up --build -d
```

Web chạy tại `http://localhost:8080`. Compose dùng volume `rag-data` để lưu SQLite.
SQL Server được cấu hình riêng; Dockerfile không tự tạo database nghiệp vụ.

## Render

- Cấu hình dịch vụ nằm trong `render.yaml`; build bằng `Dockerfile` ở root dự án.
- Khai báo `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` và
  `OPENROUTER_API_KEY` trong biến môi trường của dịch vụ.
- Giữ các cấu hình TLS, cookie và profile `prod` theo `render.yaml`.
- Health check: `/api/health`.
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
