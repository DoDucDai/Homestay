# Mochi

Dịch vụ Python cung cấp chat RAG tiếng Việt, quản lý tài liệu và yêu cầu Support.
Backend Java chuyển tiếp API với phiên đăng nhập và CSRF của hệ thống.

## Chạy

Cần Python 3.10 trở lên và backend Java đang chạy. Đặt `OPENROUTER_API_KEY`
trong `.env` hoặc biến môi trường, sau đó chạy từ root dự án:

```powershell
python ai-rag/app/server.py
```

Hoặc chạy `start-mochi.cmd`. Nếu backend dùng cổng khác:

```powershell
powershell -File ai-rag/start.ps1 -BackendUrl http://127.0.0.1:8082
```

Dịch vụ nghe tại `127.0.0.1:8091`. Giao diện đi qua Java tại
`/pages/assistant.html`, `/pages/support.html` và `/pages/rag-documents.html`.

## Dữ liệu

- `app/server.py`: dịch vụ HTTP, xác thực, truy xuất và lưu trữ.
- `web`: giao diện trợ lý, widget, Support và quản lý tài liệu.
- `knowledge/base.json`: nguồn nội dung hướng dẫn.
- `knowledge/published.json`: bộ tài liệu đóng gói khi triển khai.
- `data/assistant.sqlite3`: tài liệu và yêu cầu Support phát sinh; không commit.

Tra phòng dùng API backend. Câu trả lời chat không tự tạo booking;
thao tác đặt phòng tiếp tục trên luồng nghiệp vụ hiện có.

## Kiểm tra

```powershell
python -m unittest discover -s ai-rag/tests -v
python ai-rag/check-model.py
```

Lệnh kiểm tra model gọi dịch vụ AI đã cấu hình. Các bài unit test dùng môi trường giả lập.
Quy trình nguồn kiến thức nằm trong [knowledge/README.md](knowledge/README.md).
