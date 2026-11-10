# Email tài khoản trên website

Website chỉ dùng hai quyền `admin` và `customer` (khách hàng). Đăng ký luôn tạo tài khoản `customer`; client không được tự chọn quyền. Khách hàng dùng chung luồng đặt phòng, nhận/trả phòng, dịch vụ và thanh toán trước đây của nhân viên.

`EMAIL_VERIFICATION_ENABLED=false` là mặc định để kiểm thử. Khi tắt, giao diện ẩn nút gửi/nhập mã và API không gửi thư hay tạo mã. Email vẫn chưa được đánh dấu xác thực. Khi bật, xác thực email phục vụ bảo vệ tài khoản và khôi phục mật khẩu; không chặn luồng đặt phòng chung.

- Mã xác thực có 6 số, hạn 10 phút, tối đa 5 lần sai; gửi lại sau 60 giây và có giới hạn theo tài khoản/IP.
- Đổi email xóa xác thực và vô hiệu mã/liên kết cũ; đổi tên không làm mất xác thực.
- Quên mật khẩu cần email đã xác thực đối với cả admin và khách hàng; liên kết dùng một lần, hạn 15 phút. Đặt lại mật khẩu thu hồi mọi phiên cũ.
- Gửi email bằng Brevo HTTPS API, có thể cấu hình Resend. Vercel tiếp tục chuyển tiếp đến Java trên Render.

## 1. Chuẩn bị Brevo

1. Tạo tài khoản tại [Brevo](https://www.brevo.com/) và hoàn tất các bước xác minh tài khoản mà Brevo yêu cầu.
2. Trong mục quản lý **Senders & domains**, tạo sender với địa chỉ email bạn quản lý. Mở thư xác minh sender và hoàn tất xác minh. Đặt tên hiển thị bằng `MAIL_FROM_NAME` (mặc định `Homestay`).
3. Nếu dùng tên miền riêng, thực hiện xác thực tên miền/DNS theo hướng dẫn Brevo. Chỉ thêm bản ghi DNS khi bạn quản lý tên miền đó. Tên miền `vercel.app`/`onrender.com` dùng chung không phải tên miền bạn có thể tự thêm DNS.
4. Tạo **API key** cho Brevo API ở phần cài đặt **SMTP & API → API keys** (tên menu có thể thay đổi). Không dùng SMTP key cho API.
5. Kiểm tra tài khoản được phép gửi transactional email; nếu Brevo yêu cầu duyệt hoặc chưa chấp nhận sender, hoàn tất bước đó trước khi thử web. Không mặc định mọi Gmail sender đều được chấp nhận; làm theo trạng thái sender/tên miền trong tài khoản của bạn.

Không gửi API key hoặc mật khẩu email qua tin nhắn, không chèn vào HTML/JavaScript, không commit `.env`.

## 2. Cấu hình Render

Mở đúng [Environment của service homestay-mochi-demo](https://dashboard.render.com/web/srv-dbkbpvjbc2fs73f0hh6g/env). ID service: `srv-dbkbpvjbc2fs73f0hh6g`; backend: `https://homestay-mochi-demo.onrender.com`.

`.env` ở máy **không** tự đồng bộ lên Render. Docker loại `.env` khỏi build context và image. Profile `prod` đọc biến môi trường của tiến trình (hoặc Java system properties), không đọc `.env` hay cấu hình email từ file Spring. Thêm từng biến dưới đây, không nhập toàn bộ `.env` local vì file còn chứa cấu hình database riêng của máy:

| Biến | Giá trị |
|---|---|
| `EMAIL_VERIFICATION_ENABLED` | `true` để bật gửi mã sau khi đã nhập đủ cấu hình |
| `MAIL_PROVIDER` | `brevo` |
| `MAIL_API_KEY` | Chép giá trị của biến cùng tên trong `.env` local; không dán vào chat/tài liệu/Git |
| `MAIL_FROM` | `doducdai1704@gmail.com` — sender đã được chủ tài khoản xác nhận xác minh |
| `MAIL_FROM_NAME` | `DucDai` — tên sender đã xác nhận |
| `AUTH_TOKEN_SECRET` | Chép giá trị của biến cùng tên trong `.env` local, tối thiểu 32 ký tự; không tạo lại qua mỗi lần deploy |
| `APP_PUBLIC_URL` | `https://homestay-mochi-demo.vercel.app` |

Giữ các cấu hình DB/HTTPS hiện tại. Không thay `COOKIE_SECURE=true`, `DB_ENCRYPT=true` hoặc `DB_TRUST_SERVER_CERTIFICATE=false` trên production.

`APP_PUBLIC_URL` ở Render phải là URL frontend HTTPS ở bảng trên, **không chép** giá trị `http://localhost:8080` trong `.env` local. Nếu người dùng truy cập trực tiếp backend thay vì qua Vercel, đặt biến này thành `https://homestay-mochi-demo.onrender.com`.

Local hiện đã có `AUTH_TOKEN_SECRET`; dùng giá trị sẵn có. Với môi trường mới chưa có secret, tạo chuỗi ngẫu nhiên mật mã tối thiểu 32 ký tự và lưu trực tiếp vào nơi quản lý secret, không in ra terminal/log. Giữ secret ổn định qua các lần deploy. Đổi secret sẽ làm các mã/liên kết đang chờ không còn hợp lệ.

Save và redeploy backend sau khi cấu hình. `APP_PUBLIC_URL` phải là địa chỉ frontend người dùng mở, vì liên kết đặt lại mật khẩu dẫn về đó. Khi thay tên miền, chỉ cần sửa biến này và sender nếu cần; không sửa JavaScript để chứa địa chỉ gửi thư.

`render.yaml` giữ `EMAIL_VERIFICATION_ENABLED=false` làm mặc định an toàn cho service mới chưa có secret. Khi dùng Blueprint, kiểm tra giá trị sau sync; bật `true` tại Environment sau khi cấu hình đầy đủ. `sync:false` là placeholder cần nhập trên Render, không phải cơ chế đọc `.env` local. Theo [tài liệu Render](https://render.com/docs/configure-environment-variables), **Save only** chưa áp dụng vào tiến trình đang chạy; cần deploy để biến mới có hiệu lực.

## 3. Migration và cập nhật code

Migration `008_account_registration.sql` và `009_two_roles.sql` được đóng gói vào JAR và chạy khi backend khởi động. Chúng bổ sung bảng email/mã/hạn mức và chuyển quyền `staff`/`member` cũ sang `customer`; giữ nguyên tài khoản, mật khẩu, hồ sơ và hóa đơn. Có thể chạy lặp.

Code local còn có migration `010_customer_booking_access.sql` và `011_service_units.sql` từ các thay đổi khu khách hàng hiện có. Chúng phải nằm trong build context/JAR cùng code tương ứng; việc sửa email không tự chạy migration hay sửa database. Cần xem xét toàn bộ thay đổi đang có trước khi triển khai production.

### Nguồn triển khai cập nhật ngày 10/11/2026

- Nhánh làm việc local vẫn là `ai-rag`. Bản phát hành được chuẩn bị trong checkout riêng trên đúng nhánh `deploy/free-demo-20261102`, từ mốc `9f3e18d43c1e3ae120a27dbf5f55a66d396c975d` đã đối chiếu GitHub.
- `Dockerfile.render` đã được cập nhật để build trực tiếp toàn bộ mã nguồn Java 25, tương đương `Dockerfile`; không dùng các source archive Java 17 cũ. Không đổi nhánh theo dõi hay đường dẫn Dockerfile của service.
- Snapshot nguồn có `HttpMailGateway`, `AccountSettings`, `AccountEmailActions`, giao diện email và migration 008–011. Các kế hoạch công việc cũ đã bỏ; báo cáo kiểm thử đã chạy vẫn giữ lại.
- Không force-push hoặc thay thế lịch sử Git. Mã nguồn không chứa secret và không tự đồng bộ `.env` local; email production chỉ hoạt động sau khi Environment được cấu hình và bản mới được Render triển khai thành công. Trạng thái live cần xác minh riêng sau push/deploy.

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

Luồng hiện có chỉ gồm gửi OTP khi người dùng bấm **Gửi mã** và gửi liên kết **Quên mật khẩu** cho tài khoản đủ điều kiện. Đăng ký không tự gửi thư chào mừng; không có thư booking/hóa đơn. `AccountEmailActions` ghi `SENT` sau khi gateway nhận HTTP 2xx và ID xác nhận, ghi `FAILED` nếu gửi lỗi; mã gửi thất bại không dùng được. Phản hồi Quên mật khẩu vẫn dùng thông báo có điều kiện để không lộ email có tài khoản, không khẳng định đã gửi thư. Gateway kết nối tối đa 4 giây, yêu cầu tối đa 12 giây, không tự thử lại để tránh gửi trùng và không ghi nội dung thư/khóa/lỗi chi tiết từ provider vào log.

### Kiểm thử local không gửi thư thật

```powershell
# Từ root dự án; dùng Maven cache trong workspace nếu cần.
powershell -File backend/scripts/test-email.ps1 -MavenRepository D:\Thuctap\.maven-cache
node backend/src/test/js/email-delivery.test.cjs
python -m unittest discover -s docker/tests -k environment -v
```

`test-email.ps1` kiểm tra payload HTTPS, timeout, lỗi HTTP/mạng, phản hồi thiếu ID, không lộ secret, nạp `.env` từ root/backend và biến môi trường thật ở profile `prod`. Không mở Spring server, không kết nối SQL và không gửi thư ngoài. Kiểm thử này khác với kiểm tra sau deploy và xác nhận Inbox trên Render.

Có thể chạy thêm HTTP/SQL local với quyền tạo database thử riêng:

```powershell
powershell -File backend/scripts/test-email.ps1 -MavenRepository D:\Thuctap\.maven-cache -SqlServer
node backend/src/test/js/profile-email.test.cjs
node backend/src/test/js/email-delivery.test.cjs
node backend/src/test/js/account-policy.test.cjs
python -m unittest discover -s docker/tests -v
```

`-SqlServer` chạy `EmailFlowsHttpSqlServerIT`: tạo database tên ngẫu nhiên `HomestayEmailTest_<32 ký tự hex>`, khởi động ứng dụng trên cổng loopback ngẫu nhiên và chỉ dùng `MailGateway` giả. Tiến trình con từ chối chạy nếu tên database không đúng mẫu; provider thật bị tắt. Database thử được xóa sau khi kết thúc, kể cả kiểm thử lỗi. Không chạy migration hoặc truy vấn ghi lên database nghiệp vụ. Bộ kiểm tra bao phủ OTP, gửi thất bại, hết hạn, nhập sai 5 lần, gửi lại, đổi email, liên kết reset, hai yêu cầu dùng mã đồng thời, thu hồi phiên, restart server, cờ tắt email và khôi phục tài khoản admin.

Chủ tài khoản đã xác nhận thư thử “Homestay - Kiem tra cau hinh email” tới Gmail lúc 19:20, được mở lúc 19:21 ngày 10/11/2026, và sender `DucDai` đã xác minh. Đây là bằng chứng Brevo/sender hoạt động, không phải bằng chứng ứng dụng trên Render đã tích hợp email.

### Kết quả kiểm tra mã nguồn ngày 10/11/2026

- Build Maven thành công với Java 25, JAR riêng: `backend/target/email-ready/homestay-backend-1.0.0.jar`. Lệnh build: từ `backend`, chạy `mvnw.cmd -Dmaven.repo.local=D:\Thuctap\.maven-cache -Dhomestay.build.directory=target/email-ready package` (Maven wrapper home dùng cache workspace).
- `test-email.ps1`: đạt toàn bộ nhóm backend email/config, dùng transport HTTP giả và các tiến trình Java riêng để kiểm tra biến môi trường thật; không gửi thư ngoài.
- Đợt kiểm thử bổ sung `test-email.ps1 -SqlServer` đã đạt toàn bộ luồng email qua HTTP thật/SQL Server với email giả: OTP, đổi email, khôi phục mật khẩu, lỗi gửi, hết hạn/giới hạn, gửi đồng thời, restart và admin. Database thử riêng đã được xóa. Xem [kết quả kiểm thử email](KIEM_THU_EMAIL.md) và log `backend/target/email-http-sql-validation.log`.
- `email-delivery.test.cjs`, `account-policy.test.cjs`: đạt. Docker: bài kiểm tra truyền cấu hình email và 3 bài kiểm tra cổng/storage đạt. `git diff --check`: đạt.
- JAR có các lớp email và migration 008–011. Đã quét JAR cùng diff: không chứa `.env`, API key email hay signing secret local; `.env` được Git ignore.
- Đợt kiểm thử tiếp theo đã sửa fixture `profile-email.test.cjs` để dùng URL `reserve.html` theo giao diện hiện tại; bài Docker bootstrap kiểm tra đúng các tài liệu trong bundle thay cho hardcode 102, đồng thời đóng SQLite cả khi lỗi. Cả 3 bài JavaScript email/tài khoản và toàn bộ 5 bài Docker đã đạt. Không kết luận toàn bộ suite nghiệp vụ đạt vì chỉ chạy phạm vi email và các kiểm tra Docker liên quan.
- Chưa build/run image Docker thực vì Docker Engine chưa chạy. Health công khai Render đã xác minh trả HTTP 200 với trạng thái `ready`; chưa có kết nối quản trị Render để đọc/đổi Environment hoặc xác minh email của ứng dụng production. Không gửi thêm email thật trong đợt sửa này.

Các tệp chỉnh trong đợt email: `AccountSettings.java`, `HttpMailGateway.java`, `backend/pom.xml`, `.env.example`, `.env.docker.example`, `.dockerignore`, `render.yaml`, `EMAIL_SETUP.md`, `DEPLOY.md`; thêm `EmailDeliveryChecks.java`, `email-delivery.test.cjs`, `test-email.ps1`; đăng ký bộ kiểm tra trong `test-all.ps1`, thêm bài Docker vào `docker/tests/test_start.py`, cô lập cấu hình mặc định trong `CustomerAccountsHttpSqlServerIT.java`. `.env` local chỉ bổ sung `MAIL_FROM_NAME=DucDai`; không thay API key/signing secret, cấu hình DB hay dữ liệu nghiệp vụ. Các thay đổi chưa commit có trước được giữ nguyên; nhánh làm việc vẫn là `ai-rag`.

## 5. Khi chưa có Brevo

Đăng ký/đăng nhập và đặt phòng vẫn hoạt động. Nếu `EMAIL_VERIFICATION_ENABLED=false`, không có thao tác gửi mã. Nếu bật `true` khi chưa cấu hình Brevo, gửi mã báo email tạm chưa sẵn sàng. Quên mật khẩu cần email đã xác thực và dịch vụ gửi email thật. Không có mã mặc định hoặc mã hiện trên màn hình.

Không sử dụng khóa giả để trình diễn gửi thư thành công. Kiểm thử tự động dùng `MailGateway` giả chỉ trong mã test; ứng dụng đóng gói không có API đọc mã hoặc bật xác thực giả.

## 6. Chẩn đoán ngắn

| Hiện tượng | Kiểm tra |
|---|---|
| Email tạm chưa sẵn sàng | Đủ các biến ở bảng trên; sender/API key hợp lệ; secret đủ dài; Brevo cho phép transactional email; đã deploy code email. |
| Chạy từ backend không đọc email local | Bản mới tìm `.env` trong thư mục chạy, thư mục cha và `backend`; biến môi trường/system properties được ưu tiên; phải restart sau khi sửa `.env`. |
| API key gửi thư được nhưng GET account/senders báo 403 | Khóa giới hạn quyền có thể chỉ cho transactional email; không yêu cầu mở thêm quyền quản trị nếu gửi thư đang hoạt động. |
| Quên mật khẩu báo chung nhưng không có thư | Email có tài khoản duy nhất, sender được duyệt, quota/log Brevo, thư rác. Thông báo chung là chủ ý để không lộ email có tài khoản. |
| Mã đúng nhưng bị từ chối | Đã dùng/hết hạn/sai 5 lần, gửi mã mới, đổi email/mật khẩu/secret sau khi gửi. |
| Gửi lại bị chặn | Chờ đủ 60 giây; nếu vượt hạn mức thì chờ hết cửa sổ hạn mức. Restart không xóa hạn mức. |
| Liên kết về sai website | `APP_PUBLIC_URL` đúng Vercel hoặc tên miền frontend hiện dùng. |
| Render gửi SMTP thất bại | Luồng này dùng HTTPS API, không dùng SMTP. Không đổi sang cổng SMTP trên Render Free. |

Tài liệu chính thức: [Brevo senders/domains](https://developers.brevo.com/docs/getting-started-with-senders-and-domains), [Brevo transactional API](https://developers.brevo.com/docs/send-a-transactional-email), [Render Free và SMTP](https://render.com/docs/free).
