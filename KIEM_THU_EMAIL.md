# Kết quả kiểm thử email local — 10/11/2026

Kết quả cuối: các nhóm kiểm thử email được chạy dưới đây đều đạt. Mã chính và mã test được Maven biên dịch thành công. Đây là kết quả local, chưa xác nhận ứng dụng trên Render đã gửi thư.

## Backend và HTTP/SQL

Lệnh chạy từ root dự án:

```powershell
powershell -NoProfile -File backend/scripts/test-email.ps1 -MavenRepository D:\Thuctap\.maven-cache -SqlServer
```

Lệnh kết thúc với exit code 0. Log: `backend/target/email-http-sql-validation.log` (được Git ignore).

| Phạm vi | Đã kiểm tra | Kết quả |
|---|---|---|
| Brevo HTTP gateway | HTTPS endpoint, payload, sender, UTF-8, header xác thực, timeout | Đạt |
| Phản hồi lỗi | HTTP 301/400/401/403/429/500/503, thiếu ID thư, JSON lỗi, timeout, lỗi mạng, thread bị ngắt, không lộ secret | Đạt |
| Cấu hình | Thiếu biến chặn gửi; `.env` root/backend; chuỗi có dấu nháy; production bỏ qua file và nhận biến môi trường thật; tương thích Resend | Đạt |
| OTP qua HTTP/SQL | CSRF, đúng email đã lưu, trạng thái SENT, gửi lại trong 60 giây, chống dùng mã của tài khoản khác, mã dùng một lần | Đạt |
| Đổi email | Mất xác thực, vô hiệu mã cũ, giữ xác thực khi email không đổi, rollback email trùng | Đạt |
| Quên/đặt lại mật khẩu | Phản hồi không lộ tài khoản; email chưa xác thực không nhận link; URL frontend đúng; link hết hạn/đã dùng bị chặn | Đạt |
| Phiên đăng nhập | Reset mật khẩu thu hồi các phiên cũ; mật khẩu cũ không đăng nhập được; mật khẩu mới đăng nhập được | Đạt |
| Gửi thất bại | OTP trả 503 và ghi FAILED; chưa cấu hình không tạo mã; recovery giữ thông báo có điều kiện | Đạt |
| Giới hạn và đồng thời | Sai 5 lần khóa OTP; mã hết hạn; cooldown tồn tại sau restart; chỉ một yêu cầu OTP/reset đồng thời được dùng mã | Đạt |
| Tắt email và admin | Tắt OTP không gửi/tạo mã; admin cũng cần xác thực email trước khi khôi phục | Đạt |

Kiểm thử dùng ứng dụng Spring thật trên cổng loopback ngẫu nhiên và SQL Server local với database `HomestayEmailTest_<32 ký tự hex>`. Email được giữ trong bộ `CapturedMail`; provider thật bị tắt trong tiến trình kiểm thử. Database thử đã được xóa khi kết thúc. Không chạy các truy vấn ghi hoặc migration lên database nghiệp vụ.

## Giao diện và Docker

```powershell
node backend/src/test/js/profile-email.test.cjs
node backend/src/test/js/email-delivery.test.cjs
node backend/src/test/js/account-policy.test.cjs
python -m unittest discover -s docker/tests -v
```

- Cả 3 tệp JavaScript đạt: trạng thái xác thực, lỗi gửi, cooldown, xác nhận từ server, tắt tính năng, URL trở về an toàn, đăng ký và khôi phục mật khẩu.
- Toàn bộ 5 bài Docker đạt: cổng Render/loopback, storage, cổng không hợp lệ, giữ biến email và giữ chỉnh sửa/xóa tài liệu của admin khi khởi động lại.
- `git diff --check` đạt. Không chạy container thực vì Docker Engine chưa hoạt động.

## Thay đổi trong đợt kiểm thử

- Thêm `backend/src/test/java/com/homestay/security/EmailFlowsHttpSqlServerIT.java`, dùng lại bộ giả email/HTTP sẵn có.
- Thêm lựa chọn `-SqlServer` vào `backend/scripts/test-email.ps1`.
- Sửa kỳ vọng URL cũ trong `backend/src/test/js/profile-email.test.cjs` để khớp trang `reserve.html` hiện tại.
- Sửa `docker/tests/test_start.py`: kiểm tra tài liệu theo bundle thay cho số hardcode và đóng kết nối SQLite cả khi assertion lỗi.
- Cập nhật tài liệu `EMAIL_SETUP.md` cùng báo cáo này. Không đổi code nghiệp vụ, database configuration, API key hoặc secret trong đợt kiểm thử.

## Giới hạn kết luận

- Không gửi thêm email thật trong đợt này. Việc sender Brevo gửi/được mở thư thật trước đó là thông tin chủ tài khoản đã xác nhận.
- Chưa kiểm thử gửi OTP/reset bằng ứng dụng trên Render; chưa commit, push hoặc deploy.
- Không tuyên bố toàn bộ kiểm thử phòng, booking, hóa đơn hay các chức năng ngoài email đã đạt; các nhóm đó chưa chạy trong đợt này.
