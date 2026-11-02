# Dịch vụ AI RAG & Support riêng

Cập nhật giao diện: trang AI dùng `ai-page.css`, vẫn hiển thị giao diện khi chưa đăng nhập nhưng chat/đặt phòng tiếp tục yêu cầu phiên hợp lệ. Mascot `web/mascot.svg` là bé mèo Mochi màu kem, khăn xanh và mầm lá, có hoạt ảnh SVG. Widget luôn ở góc dưới bên phải, chỉ đi lại trong phạm vi 55px, dừng khi hover/focus hoặc mở chat và tôn trọng chế độ giảm chuyển động.

Nếu trang AI mất bố cục hoặc trống phần chat, kiểm tra `/ai/assistant.css` và `/ai/chat-client.js` phải trả CSS/JavaScript, không phải HTML đăng nhập. Cần khởi động backend mới sau khi thay đổi SecurityConfig/WebConfig; đổi HTML trong khi vẫn chạy bản backend cũ không cập nhật được các đường dẫn này.

Mã AI, truy xuất tài liệu, Support, tài liệu admin và giao diện liên quan nằm trong thư mục này. Backend chỉ bổ sung cầu nối HTTP có xác thực/CSRF; frontend thêm trang Support và liên kết.

## Chạy local

Yêu cầu Python 3.10+ và backend Java hiện có. Không cần cài thư viện Python bên ngoài.

1. Trong `Homestay/.env`, đặt `OPENROUTER_API_KEY` bằng key của bạn. Không commit key. Có thể ghi đè cấu hình qua `ai-rag/.env` hoặc biến môi trường (biến môi trường được ưu tiên; `.env` gốc được nạp trước).
2. Nếu backend không chạy ở cổng 8080, cấu hình `HOMESTAY_BACKEND_URL=http://127.0.0.1:<cổng>`.
3. Từ `Homestay`, chạy `python ai-rag/app/server.py`. Dịch vụ chỉ nghe `127.0.0.1:8091`.
4. Build/chạy lại backend để có cầu nối `/api/assistant/**` và tài nguyên `/ai/**`. Khi đóng gói JAR, Maven tự đóng gói `ai-rag/web`. Khi chạy dev từ thư mục backend, các file này được đọc trực tiếp.
5. Mở `/pages/assistant.html` và đăng nhập bằng tài khoản hiện có. Support nằm riêng tại `/pages/support.html`; đặt phòng dùng trang `/pages/bookings.html` hiện có.

Có thể chạy `./ai-rag/start.ps1 -BackendUrl http://127.0.0.1:8082` nếu backend dùng cổng 8082. Cầu nối backend luôn gọi dịch vụ AI ở cổng 8091, còn dịch vụ AI kiểm tra phiên với đúng cổng backend đã cấu hình; hai giá trị phải khớp môi trường đang mở trên trình duyệt.

## Chức năng hiện có

- Trang Support được thiết kế theo bố cục trung tâm trợ giúp: khối liên hệ, sáu chủ đề, tìm kiếm không dấu, FAQ mở/đóng và theo dõi yêu cầu. Có thể đọc FAQ khi chưa đăng nhập; các API gửi/xem yêu cầu và phản hồi vẫn yêu cầu phiên hợp lệ. Header Support và Bảo mật & điều khoản cùng kiểu với landing/Khám phá phòng; các trang vận hành giữ sidebar cũ.

- Chat tiếng Việt với `qwen/qwen3-4b:free`, chỉ cho cấu hình model `:free` hoặc `openrouter/free`; giới hạn giá token bằng 0. Không tự chuyển model có phí.
- RAG tìm kiếm từ khóa tiếng Việt bỏ dấu, chia đoạn tài liệu, lấy bốn đoạn liên quan, trả nguồn và phiên bản. Đây là RAG truy xuất từ khóa, chưa dùng vector embedding; không cần key embedding.
- Admin tạo, sửa/công bố hoặc gỡ tài liệu. Truy xuất đọc dữ liệu mới mỗi lần nên không còn đoạn cũ sau khi sửa/gỡ. Chỉ nhập nội dung đã duyệt.
- Chọn ngày nhận/trả để tra phòng theo đêm từ API backend thật. Dữ liệu gửi tới LLM chỉ gồm thông tin phòng, không gồm danh sách khách hàng.
- Mascot nhỏ ở góc dưới mở chat nhanh; liên kết mở trang AI đầy đủ. Giao diện lấy cảm hứng từ cấu trúc trang AI và FloatingAiButton của dự án P150 SalesMate, dùng màu sắc Homestay.
- Khi chọn phòng từ AI, mở trang bookings hiện có với mã phòng và ngày nhận đã chọn để kiểm tra và xác nhận; AI không trực tiếp tạo đơn. Không duy trì trang đặt phòng thứ hai.
- Gửi yêu cầu Support, thông báo số yêu cầu mới cho admin, admin phản hồi; người gửi chỉ xem yêu cầu của mình. Trạng thái mới và yêu cầu được lưu cùng một bản ghi SQLite.
- Dữ liệu lưu tại `ai-rag/data/assistant.sqlite3`, được Git bỏ qua. Sao lưu file bằng công cụ backup SQLite khi vận hành; bảo vệ quyền truy cập thư mục dữ liệu.

## Giới hạn hiện tại cần biết

Hệ thống tài khoản hiện chỉ có admin/staff, chưa có vai trò khách. Module dùng phiên đăng nhập hiện tại; không tự cấp tài khoản hoặc quyền quản trị cho khách. Cần bổ sung vai trò khách và quan hệ Users–Customers trước khi mở dịch vụ cho khách thật. Luồng xác nhận trực tiếp trong chat, lịch theo giờ và kiểm tra sức chứa chưa triển khai trong module này; dùng chức năng đặt phòng hiện có. Tin nhắn độc lập từng lượt; chưa lưu hội thoại nhiều lượt.

Model miễn phí có thể hết quota hoặc tạm ngừng. Khi lỗi, giao diện báo chưa xử lý được và vẫn cung cấp luồng đặt thông thường. Không coi câu trả lời model là bằng chứng đặt thành công.

Tài liệu mặc định chỉ ghi các quy tắc đã được chủ dự án xác nhận, gồm chưa có chính sách hủy/hoàn tiền/đặt cọc. Không nạp trang chính sách dự thảo làm quy định chính thức.

## Kiểm thử

Từ thư mục `Homestay`:

```powershell
python -m unittest discover -s ai-rag/tests -v
```

Kiểm thử xác thực, CSRF, cách ly yêu cầu hỗ trợ, quyền admin, cập nhật/gỡ tài liệu, truy xuất, phòng trống, lỗi model và giới hạn lượt gọi. Test dùng backend/model giả lập, không tốn quota và không tạo đơn thật.

Nguồn model: https://openrouter.ai/qwen/qwen3-4b:free

Giới hạn dịch vụ: https://openrouter.ai/docs/api/reference/limits
