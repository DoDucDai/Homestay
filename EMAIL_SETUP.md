# Email tài khoản trên website

Website chỉ dùng hai quyền `admin` và `customer` (khách hàng). Đăng ký luôn tạo tài khoản `customer`; client không được tự chọn quyền. Khách hàng dùng chung luồng đặt phòng, nhận/trả phòng, dịch vụ và thanh toán trước đây của nhân viên.

`EMAIL_VERIFICATION_ENABLED=false` là mặc định để kiểm thử. Khi tắt, giao diện ẩn nút gửi/nhập mã và API không gửi thư hay tạo mã. Email vẫn chưa được đánh dấu xác thực. Khi bật, xác thực email phục vụ bảo vệ tài khoản và khôi phục mật khẩu; không chặn luồng đặt phòng chung.

- Mã xác thực có 6 số, hạn 10 phút, tối đa 5 lần sai; gửi lại sau 60 giây và có giới hạn theo tài khoản/IP.
- Đổi email xóa xác thực và vô hiệu mã/liên kết cũ; đổi tên không làm mất xác thực.
- Quên mật khẩu cần email đã xác thực đối với cả admin và khách hàng; liên kết dùng một lần, hạn 15 phút. Đặt lại mật khẩu thu hồi mọi phiên cũ.
- Gửi email bằng Brevo HTTPS API, có thể cấu hình Resend. Vercel tiếp tục chuyển tiếp đến Java trên Render.

## 1. Chuẩn bị Brevo

1. Tạo tài khoản tại [Brevo](https://www.brevo.com/) và hoàn tất các bước xác minh tài khoản mà Brevo yêu cầu.
2. Trong mục quản lý **Senders & domains**, tạo sender với tên Homestay và địa chỉ email bạn quản lý. Mở thư xác minh sender và hoàn tất xác minh.
3. Nếu dùng tên miền riêng, thực hiện xác thực tên miền/DNS theo hướng dẫn Brevo. Chỉ thêm bản ghi DNS khi bạn quản lý tên miền đó. Tên miền `vercel.app`/`onrender.com` dùng chung không phải tên miền bạn có thể tự thêm DNS.
4. Tạo **API key** cho Brevo API ở phần cài đặt **SMTP & API → API keys** (tên menu có thể thay đổi). Không dùng SMTP key cho API.
5. Kiểm tra tài khoản được phép gửi transactional email; nếu Brevo yêu cầu duyệt hoặc chưa chấp nhận sender, hoàn tất bước đó trước khi thử web. Không mặc định mọi Gmail sender đều được chấp nhận; làm theo trạng thái sender/tên miền trong tài khoản của bạn.

Không gửi API key hoặc mật khẩu email qua tin nhắn, không chèn vào HTML/JavaScript, không commit `.env`.

## 2. Cấu hình Render

Mở service `homestay-mochi-demo` → Environment. Thêm:

| Biến | Giá trị |
|---|---|
| `EMAIL_VERIFICATION_ENABLED` | `true` khi bật lại xác thực; giữ `false` để test không cần email |
| `MAIL_PROVIDER` | `brevo` |
| `MAIL_API_KEY` | API key vừa tạo trong Brevo |
| `MAIL_FROM` | Đúng địa chỉ sender đã được Brevo xác minh |
| `AUTH_TOKEN_SECRET` | Chuỗi ngẫu nhiên bí mật dài ít nhất 32 ký tự |
| `APP_PUBLIC_URL` | `https://homestay-mochi-demo.vercel.app` |

Giữ các cấu hình DB/HTTPS hiện tại. Không thay `COOKIE_SECURE=true`, `DB_ENCRYPT=true` hoặc `DB_TRUST_SERVER_CERTIFICATE=false` trên production.

Có thể tạo secret trên máy bằng PowerShell:

```powershell
$emailTokenBytes = New-Object byte[] 32
$emailTokenRng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$emailTokenRng.GetBytes($emailTokenBytes)
[Convert]::ToBase64String($emailTokenBytes)
$emailTokenRng.Dispose()
```

Chép giá trị sinh ra vào `AUTH_TOKEN_SECRET`; không đưa vào Git. Giữ secret ổn định qua các lần deploy. Đổi secret sẽ làm các mã/liên kết đang chờ không còn hợp lệ.

Save và redeploy backend sau khi cấu hình. `APP_PUBLIC_URL` phải là địa chỉ frontend người dùng mở, vì liên kết đặt lại mật khẩu dẫn về đó. Khi thay tên miền, chỉ cần sửa biến này và sender nếu cần; không sửa JavaScript để chứa địa chỉ gửi thư.

## 3. Migration và cập nhật code

Migration `008_account_registration.sql` và `009_two_roles.sql` được đóng gói vào JAR và chạy khi backend khởi động. Chúng bổ sung bảng email/mã/hạn mức và chuyển quyền `staff`/`member` cũ sang `customer`; giữ nguyên tài khoản, mật khẩu, hồ sơ và hóa đơn. Có thể chạy lặp.

Trước khi deploy production:

1. Sao lưu SQL Server theo quy trình hiện tại.
2. Kiểm tra email trùng trong tài khoản cũ bằng truy vấn chỉ trả số lượng:

```sql
SELECT COUNT(*) AS ambiguous_email_groups
FROM (
  SELECT LOWER(LTRIM(RTRIM(email))) AS email
  FROM Users WHERE email IS NOT NULL AND LEN(LTRIM(RTRIM(email))) > 0
  GROUP BY LOWER(LTRIM(RTRIM(email))) HAVING COUNT(*) > 1
) duplicate_emails;
```

3. Nếu có email trùng, quản lý cập nhật đúng địa chỉ cho từng tài khoản. Phần mềm giữ nguyên các tài khoản này, không cho dùng email mơ hồ để khôi phục/đăng ký mới và không tự gán email giả.
4. Chạy kiểm thử, push code lên nhánh được Render theo dõi và deploy backend. Vercel đang proxy toàn bộ sang Render nên thường không cần thay rewrite.
5. Đợi `/api/health` hoạt động và kiểm tra đăng nhập admin/khách hàng; xác thực đúng email của từng tài khoản để bật khôi phục qua email.

## 4. Kiểm tra email thật sau cấu hình

1. Tạo tài khoản thử bằng email do bạn quản lý.
2. Vào Thông tin cá nhân → Gửi mã → kiểm tra inbox/spam → nhập mã đúng.
3. Thử đặt phòng và xác nhận booking xuất hiện trong danh sách đặt phòng chung. Hủy booking thử khi phù hợp.
4. Đăng xuất → Quên mật khẩu → mở liên kết trong thư → đặt mật khẩu mới → đăng nhập lại.
5. Xác nhận mã/link đã dùng không dùng lại được và khách hàng không truy cập được chức năng quản trị phòng/thống kê.

Thông báo “đã gửi mã” nghĩa là nhà cung cấp đã nhận yêu cầu, không bảo đảm thư nằm trong Inbox. Xem transactional logs của Brevo nếu cần; không sao chép token/nội dung thư vào log ứng dụng.

## 5. Khi chưa có Brevo

Đăng ký/đăng nhập và đặt phòng vẫn hoạt động. Nếu `EMAIL_VERIFICATION_ENABLED=false`, không có thao tác gửi mã. Nếu bật `true` khi chưa cấu hình Brevo, gửi mã báo email tạm chưa sẵn sàng. Quên mật khẩu cần email đã xác thực và dịch vụ gửi email thật. Không có mã mặc định hoặc mã hiện trên màn hình.

Không sử dụng khóa giả để trình diễn gửi thư thành công. Kiểm thử tự động dùng `MailGateway` giả chỉ trong mã test; ứng dụng đóng gói không có API đọc mã hoặc bật xác thực giả.

## 6. Chẩn đoán ngắn

| Hiện tượng | Kiểm tra |
|---|---|
| Email tạm chưa sẵn sàng | Đủ 5 biến trên; sender/API key hợp lệ; secret đủ dài; Brevo cho phép transactional email. |
| Quên mật khẩu báo chung nhưng không có thư | Email có tài khoản duy nhất, sender được duyệt, quota/log Brevo, thư rác. Thông báo chung là chủ ý để không lộ email có tài khoản. |
| Mã đúng nhưng bị từ chối | Đã dùng/hết hạn/sai 5 lần, gửi mã mới, đổi email/mật khẩu/secret sau khi gửi. |
| Gửi lại bị chặn | Chờ đủ 60 giây; nếu vượt hạn mức thì chờ hết cửa sổ hạn mức. Restart không xóa hạn mức. |
| Liên kết về sai website | `APP_PUBLIC_URL` đúng Vercel hoặc tên miền frontend hiện dùng. |
| Render gửi SMTP thất bại | Luồng này dùng HTTPS API, không dùng SMTP. Không đổi sang cổng SMTP trên Render Free. |

Tài liệu chính thức: [Brevo senders/domains](https://developers.brevo.com/docs/getting-started-with-senders-and-domains), [Brevo transactional API](https://developers.brevo.com/docs/send-a-transactional-email), [Render Free và SMTP](https://render.com/docs/free).
