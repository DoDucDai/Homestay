# Thông tin và kết quả thực hiện dự án Homestay

Cập nhật tài liệu ngày 01/11/2026.

## 1. Tổng quan dự án

Homestay Management System là ứng dụng web quản lý hoạt động homestay, gồm phòng, khách hàng, đặt phòng, nhận phòng, dịch vụ trong thời gian lưu trú, thanh toán, hóa đơn và thống kê doanh thu.

Hệ thống có hai vai trò: quản trị viên (admin) và nhân viên (staff). Phân quyền được kiểm tra tại backend; giao diện hiển thị các thao tác phù hợp với tài khoản đăng nhập.

## 2. Công nghệ và kiến trúc

| Thành phần | Công nghệ đang sử dụng |
| --- | --- |
| Frontend | HTML, CSS, JavaScript |
| Backend | Java 17, Spring Boot 3.2.0, Spring Security |
| Truy cập dữ liệu | JDBC và Microsoft SQL Server JDBC Driver |
| Cơ sở dữ liệu | SQL Server |
| Công cụ xây dựng | Maven Wrapper |
| Xác thực | Phiên server, cookie HttpOnly và CSRF token |

Frontend gọi API của backend; backend kiểm tra quyền, dữ liệu và nghiệp vụ trước khi truy cập SQL Server. Frontend được phục vụ cùng backend và được đóng gói trong bản JAR.

### Cấu trúc chính

| Thư mục | Nội dung |
| --- | --- |
| `backend/src/main/java/com/homestay` | API, xử lý nghiệp vụ, bảo mật và kết nối database |
| `backend/src/main/resources` | Cấu hình ứng dụng |
| `backend/src/test` | Các chương trình kiểm tra backend và frontend |
| `backend/scripts` | Công cụ kiểm tra, nhập dữ liệu và đóng gói |
| `frontend/pages` | Các trang giao diện |
| `frontend/css` và `frontend/js` | Định dạng giao diện và xử lý phía trình duyệt |
| `database` | Schema, dữ liệu mẫu, migration và truy vấn |
| `contributions/hung` và `contributions/long` | Bộ dữ liệu demo và truy vấn kiểm tra đã tích hợp |

### Cơ sở dữ liệu

| Bảng | Thông tin quản lý |
| --- | --- |
| `Users` | Tài khoản, vai trò và thông tin mật khẩu |
| `Rooms` | Phòng, loại phòng, giá và trạng thái |
| `Customers` | Khách hàng, giấy tờ và thông tin liên hệ |
| `Bookings` | Đặt phòng, thời gian lưu trú, trạng thái và giá đã chốt |
| `Services` | Danh mục dịch vụ, giá và trạng thái hoạt động |
| `Booking_Services` | Dịch vụ đã sử dụng, số lượng và giá tại thời điểm gọi |
| `Payments` | Thanh toán, tổng tiền, thời điểm thu và bản hóa đơn đã lưu |

Đã có migration 001–006 cho ràng buộc hóa đơn duy nhất, lưu giá giao dịch, chỉ mục lịch phòng, thông tin mật khẩu, loại giấy tờ khách hàng và đặt phòng theo giờ.

## 3. Các chức năng đã triển khai

### Đăng nhập và tài khoản

- Trang chủ cho phép xem khi chưa đăng nhập; khi chọn chức năng quản lý, người dùng đăng nhập rồi quay lại chức năng đã chọn.
- Trang `explore.html` giới thiệu phòng công khai, có tìm kiếm, lọc loại, sắp xếp giá và xem chi tiết từng phòng. Ảnh phòng để chỗ cập nhật sau. API công khai chỉ trả thông tin giới thiệu và giá, không trả khách hàng hoặc booking; phòng bảo trì không xuất hiện trong danh mục này.
- Đăng nhập, đăng xuất, đọc thông tin tài khoản hiện tại và đổi mật khẩu.
- Chỉnh sửa họ tên, số điện thoại, email, ngày sinh và thành phố của tài khoản đang đăng nhập. Migration `007_account_profile.sql` bổ sung các trường hồ sơ.
- Hồ sơ yêu cầu số điện thoại đúng 10 chữ số bắt đầu bằng 0 và địa chỉ Gmail hợp lệ. Mật khẩu mới có 6–128 ký tự, gồm chữ hoa, chữ thường, chữ số và ký tự đặc biệt. Biểu mẫu đăng ký xem trước yêu cầu tên đăng nhập từ 6 ký tự.
- Trang tài khoản dành cho hồ sơ cá nhân và bảo mật. Các chức năng quản trị truy cập trực tiếp từ thanh bên; menu Khu quản trị mở trang thống kê.
- Xác thực bằng phiên server; mật khẩu được băm, có cơ chế giới hạn thử đăng nhập và bảo vệ CSRF.
- Phân quyền admin/staff cho API và trang quản trị; xử lý phiên hết hạn trên giao diện.

### Quản lý phòng

- Hiển thị danh sách, tìm theo mã hoặc tên, lọc loại phòng và trạng thái.
- Thêm, sửa, xóa phòng theo quyền và điều kiện nghiệp vụ.
- Chuyển phòng sang bảo trì hoặc kích hoạt lại; kiểm tra booking liên quan trước khi thay đổi.
- Hiển thị trạng thái và lịch phòng phục vụ việc tìm phòng còn trống.

### Quản lý khách hàng

- Thêm, sửa, tìm kiếm khách hàng theo tên hoặc giấy tờ; xóa theo quyền và ràng buộc dữ liệu.
- Hỗ trợ CCCD và hộ chiếu, kiểm tra định dạng giấy tờ, điện thoại và trường bắt buộc.
- Xem lịch sử lưu trú và truy cập hóa đơn của khách.
- Bảng khách hàng tự thu gọn nội dung dài bằng dấu ba chấm; bấm thông tin để xem hồ sơ đầy đủ. Màn hình nhỏ hiển thị khách hàng theo dạng thẻ.

### Đặt phòng và nhận phòng

- Tìm phòng trống theo khoảng lưu trú, tạo booking và lưu giá tại thời điểm đặt.
- Hỗ trợ đặt theo đêm và theo giờ. Thời gian lưu trú theo giờ được tính theo giờ Việt Nam, số giờ tính tiền làm tròn lên.
- Kiểm tra lịch trùng, phòng bảo trì và thời gian không hợp lệ trước khi ghi dữ liệu.
- Hủy booking ở trạng thái Confirmed; nhận phòng theo điều kiện nghiệp vụ và chuyển sang CheckedIn.
- Trang nhận phòng có bộ đếm, tìm theo khách/phòng/mã booking, lọc lượt đến lịch/chưa đến lịch/cần xử lý, bảng gọn và dạng thẻ trên điện thoại.
- Giao diện có xác nhận thao tác, xử lý xung đột và hạn chế gửi yêu cầu lặp.

### Dịch vụ trong thời gian lưu trú

- Admin và nhân viên có thể thêm, sửa và xóa dịch vụ chưa được sử dụng; dịch vụ đã có dữ liệu sử dụng được giữ để bảo toàn lịch sử, có thể chuyển sang Ngừng.
- Quản lý danh mục dịch vụ, đơn giá và trạng thái hoạt động.
- Thêm hoặc hủy dòng dịch vụ cho booking đang ở; kiểm tra số lượng, trạng thái dịch vụ và giới hạn tổng tiền.
- Lưu tên và giá dịch vụ tại thời điểm gọi, không tính lại theo giá danh mục mới.
- Đồng bộ thay đổi dịch vụ với thanh toán để tránh ghi dữ liệu không nhất quán.

### Thanh toán và hóa đơn

- Xem trước tiền phòng, tiền dịch vụ và tổng thanh toán.
- Trả phòng, ghi thanh toán và cập nhật trạng thái trong cùng transaction.
- Mỗi booking chỉ có một hóa đơn; có khóa và ràng buộc database để chống thanh toán trùng.
- Lưu bản hóa đơn JSON và hỗ trợ xem, in lại từ dữ liệu đã lưu.
- Thay đổi tên hoặc giá danh mục sau thanh toán không làm đổi hóa đơn đã phát hành.
- Hóa đơn cũ thiếu chi tiết được giữ nguyên tổng đã thu và có cảnh báo đối soát khi cần.

### Thống kê và giao diện

- Có API và giao diện tổng quan, doanh thu theo tháng và tổng hợp theo năm.
- Doanh thu dựa trên thanh toán đã thu và thời điểm `paid_at`.
- Đã triển khai các trang đăng nhập, giới thiệu, phòng, khách hàng, đặt phòng, nhận phòng, trả phòng, dịch vụ, thống kê và tài khoản.
- Có các thành phần giao diện lịch đặt phòng, bảng tổng quan, tìm kiếm, bộ lọc, thông báo và trạng thái tải dữ liệu.

### Kiểm soát dữ liệu và lỗi

- Chuẩn hóa lỗi API với các mã HTTP phù hợp và thông báo rõ ràng.
- Kiểm tra dữ liệu đầu vào tại backend; hiển thị dữ liệu động qua DOM an toàn.
- Dùng transaction, khóa bản ghi và rollback cho các thao tác liên quan đến phòng, booking, dịch vụ và hóa đơn.
- Đã có công cụ xây dựng bản phát hành, khởi động theo cấu hình và các chương trình kiểm tra tích hợp.

## 4. Bộ dữ liệu demo đã hoàn thiện

Bộ dữ liệu Long–Hưng đã được tích hợp và bổ sung kiểm tra. Ngày chốt dữ liệu là **29/10/2026**, kỳ doanh thu **11/2025–10/2026**. Đây là dữ liệu giả lập.

| File | Số dòng dữ liệu |
| --- | ---: |
| `contributions/hung/customers.csv` | 50 |
| `contributions/hung/services.csv` | 12 |
| `contributions/long/rooms.csv` | 20 |
| `contributions/long/bookings.csv` | 60 |
| `contributions/long/booking_services.csv` | 52 |
| `contributions/long/payments.csv` | 40 |

Booking gồm 40 CheckedOut, 10 Cancelled, 6 Confirmed và 4 CheckedIn. Bộ dữ liệu có khách quay lại, phòng được đặt nhiều lần, lưu trú một hoặc nhiều đêm, hóa đơn có hoặc không có dịch vụ và hai phòng bảo trì.

Tổng 40 hóa đơn demo là **70.680.000 VNĐ**. Dữ liệu đã được kiểm tra cấu trúc, tham chiếu, lịch không trùng, trạng thái, tổng tiền và phân bố đủ 12 tháng.

Công cụ nhập đã có ánh xạ mã CSV, transaction, rollback và kiểm tra chạy lại để tránh tạo trùng. Giá lịch sử và bản hóa đơn được giữ tại thời điểm giao dịch. Truy vấn `contributions/long/queries_check.sql` kiểm tra số lượng, ngày, lịch trùng, đối soát tiền, doanh thu, vòng đời thanh toán và bản hóa đơn.

Theo kết quả ghi nhận ngày 29/10/2026, database trên máy Đại đã được sao lưu, kiểm tra phục hồi, nâng cấp và nhập bổ sung dữ liệu demo. Sau nhập có 2 tài khoản, 26 phòng, 55 khách, 17 dịch vụ, 65 booking, 52 dòng dịch vụ và 41 payment. Các số này là kết quả tại thời điểm nhập, gồm cả dữ liệu cũ.

## 5. Kết quả kiểm tra đã ghi nhận

| Phần đã thực hiện | Kết quả được ghi nhận |
| --- | --- |
| FIX-02 — Kiểm soát nhận phòng và thanh toán | 12 nhóm SQL Server đạt, gồm xử lý đồng thời, rollback và hóa đơn duy nhất |
| CORE-01 — Giá giao dịch và bản hóa đơn | 9 nhóm SQL Server đạt, gồm giữ giá đã chốt, lưu và đọc lại hóa đơn |
| CORE-02 — Lịch phòng và hủy booking | 22 nhóm SQL Server đạt, gồm kiểm tra lịch và trạng thái |
| CORE-03 — Dịch vụ trong lưu trú | 21 nhóm SQL Server đạt, gồm thêm, hủy và đồng bộ thanh toán |
| SEC-01 và FIX-03 | Đã ghi nhận kiểm tra HTTP/SQL cho xác thực, phân quyền, validation và lỗi API |
| FE-01 đến FE-05 | Đã ghi nhận kiểm tra DOM và các kiểm tra SQL/HTTP liên quan đến đặt phòng, dịch vụ, phòng, khách hàng và tài khoản |
| Bộ dữ liệu demo | Validator đạt kiểm tra cấu trúc, số lượng, tham chiếu, lịch, tiền và kỳ doanh thu |
| Nhập dữ liệu demo | 7 nhóm SQL/import đạt, gồm rollback, chạy lại không trùng và đối soát hóa đơn |
| Giao diện khách hàng và dịch vụ | 15/15 ca Chrome thật đạt ngày 29/10/2026 trên database QA riêng |

Các ca Chrome bao gồm trường bắt buộc, CCCD/hộ chiếu, điện thoại sai, thêm và sửa khách, tìm tên có dấu hoặc giấy tờ, danh sách rỗng, giấy tờ trùng, thêm/sửa dịch vụ, giá âm và phân quyền staff.

Kết quả bổ sung cho dữ liệu và giao diện ngày 29/10 được thực hiện bởi Đại/Codex trên phần đóng góp của Long và Hưng. Số nhóm kiểm tra thuộc từng đợt, có phần hồi quy trùng nhau nên không cộng thành tổng kiểm thử độc lập. Các kết quả trên phản ánh phạm vi và thời điểm đã ghi nhận, không phải xác nhận nghiệm thu toàn bộ phiên bản hiện tại.
