# API khách hàng và xác nhận thu tiền

Giao diện khách dùng các API riêng dưới đây. Migration 010/011 được đóng gói cùng backend. Kết quả kiểm thử email được ghi tại [KIEM_THU_EMAIL.md](../KIEM_THU_EMAIL.md); không dùng kết quả đó để suy ra mọi luồng booking/thanh toán đã được nghiệm thu.

Mọi endpoint `/api/customer/...` yêu cầu phiên `customer`. User/customer ID luôn lấy từ session. Các yêu cầu ghi cần `X-CSRF-TOKEN` lấy từ `/api/auth/csrf`. API vận hành chung được giới hạn cho admin; không dùng các API đó để xây giao diện khách.

## Hồ sơ

| Method | Đường dẫn | Chức năng |
|---|---|---|
| GET | `/api/customer/profile` | Hồ sơ thuộc tài khoản, `profileReady=false` nếu chưa liên kết |
| PUT | `/api/customer/profile` | Tạo/hoàn thiện hồ sơ của chính tài khoản |
| PUT | `/api/admin/customer-links/{userId}` | Admin liên kết hồ sơ cũ đã xác minh, body `{ "customerId": 123 }` |

Body hoàn thiện hồ sơ:

```json
{
  "fullName": "Nguyễn An",
  "phone": "0900000001",
  "documentType": "PASSPORT",
  "documentNumber": "AB123456",
  "address": "Địa chỉ người đặt"
}
```

`fullName`/`phone` có thể lấy từ tài khoản nếu không truyền. Email luôn lấy từ tài khoản; đổi email tiếp tục dùng `/api/auth/profile` và quy trình sở hữu email hiện có. Giấy tờ trùng một hồ sơ khác trả `409 DOCUMENT_IN_USE`, không tự nhận hồ sơ đó. Một hồ sơ chỉ thuộc một tài khoản; admin không âm thầm chuyển liên kết đã có.

## Tìm phòng, dịch vụ và báo giá

- `GET /api/customer/availability?billingUnit=Night&checkinDate=2026-12-01&checkoutDate=2026-12-02&propertyId=1`
- Theo giờ: `billingUnit=Hour&checkinAt=2026-12-01T10:00&checkoutAt=2026-12-01T12:00`.
- `GET /api/customer/services`: chỉ danh mục hoạt động, không có chức năng sửa giá.
- `POST /api/customer/quotes`: báo giá phòng và các dịch vụ, lấy giá từ server.

```json
{
  "roomId": 1,
  "billingUnit": "Night",
  "checkinDate": "2026-12-01",
  "checkoutDate": "2026-12-02",
  "services": [{ "serviceId": 1, "quantity": 2 }]
}
```

Phản hồi báo giá có `room`, `roomTotal`, `serviceTotal`, `grandTotal`, `services`, `nights`/`billedHours`, `quoteFingerprint`. Mỗi dịch vụ có `unit` cấu hình bởi admin; số lượng tính cho toàn bộ lượt đặt. Danh sách phòng không trả trạng thái vận hành, khách đang ở hoặc booking của người khác. `roomCode` dùng để chọn đúng ảnh qua thư viện ảnh hiện có.

## Đặt phòng và lịch sử cá nhân

| Method | Đường dẫn | Chức năng |
|---|---|---|
| POST | `/api/customer/bookings` | Tạo booking và dịch vụ của chính tài khoản trong một giao dịch |
| GET | `/api/customer/bookings` | Mọi đơn thuộc tài khoản, gồm chưa thanh toán/đã hủy/hết hạn |
| GET | `/api/customer/bookings/{id}` | Đơn riêng và tạm tính, kiểm tra sở hữu trước khi đọc chi tiết |
| PUT | `/api/customer/bookings/{id}/cancel` | Hủy đơn của mình chưa nhận, chưa trả tiền, không đang đối soát |

Tạo đơn dùng các trường báo giá, thêm `paymentMethod` (`Tiền mặt` hoặc `Chuyển khoản`), `expectedTotal` bằng tổng đã xem, `expectedQuote` bằng `quoteFingerprint` của báo giá và `note` tùy chọn. Nếu chưa có hồ sơ, có thể gửi thêm `profile` với cấu trúc bên trên; lỗi booking sẽ rollback cả hồ sơ mới và các dòng dịch vụ. Nếu giá hoặc bảng kê thay đổi, kể cả tổng cộng không đổi, trả `409 PRICE_CHANGED` và yêu cầu lấy báo giá mới.

Không nhận `customerId`, `userId`, `role` hoặc các trường giá tự đặt trong body. Trường ngoài hợp đồng bị từ chối. Mỗi dịch vụ có một dòng và số lượng nguyên 1–10000; tối đa 30 dịch vụ.

Trạng thái thanh toán trả riêng: `Unpaid`, `PendingReview`, `Paid`. Đơn hết hạn hiển thị `status=Expired`; các trạng thái lưu trú cũ vẫn dùng `Confirmed`, `CheckedIn`, `CheckedOut`, `Cancelled`. `reservationExpiresAt`/`serverTime` là ISO 8601 UTC; thời gian ở theo giờ Việt Nam.

## Thông tin chuyển khoản và hóa đơn

| Method | Đường dẫn | Chức năng |
|---|---|---|
| GET | `/api/customer/bookings/{id}/payment` | Tổng tiền, mã tham chiếu, trạng thái, thời hạn và thông tin nhận tiền nếu có cấu hình |
| POST | `/api/customer/bookings/{id}/payment-report` | Body rỗng hoặc `{}`; báo đã chuyển khoản, không đánh dấu đã nhận tiền |
| GET | `/api/customer/invoices` | Chỉ hóa đơn đã thanh toán của tài khoản |
| GET | `/api/customer/invoices/{id}` | Đọc hóa đơn cố định sau khi kiểm tra quyền sở hữu |

Các biến cấu hình bổ sung:

```text
TRANSFER_HOLD_MINUTES=15
TRANSFER_REVIEW_WARNING_MINUTES=30
TRANSFER_BANK_NAME=
TRANSFER_ACCOUNT_NUMBER=
TRANSFER_ACCOUNT_NAME=
```

Thiếu thông tin ngân hàng thì trả `transferConfigured=false`; không tự dựng thông tin nhận tiền. Giữ phòng dựa trên thời gian SQL Server; reload không gia hạn. Báo chuyển khoản hợp lệ trước hạn chuyển sang `PendingReview`, không tạo Payments hoặc hóa đơn. Truy cập đơn/hóa đơn người khác trả 404.

QR tại giao diện khách mã hóa thông tin nhận tiền từ cấu hình tại trình duyệt, không gửi dữ liệu cho dịch vụ QR ngoài. Đây là QR thông tin, chưa phải VietQR và chưa có kết nối ngân hàng. Hóa đơn chi tiết có cấu trúc `{success:true, invoice:{...}}`; nội dung trong `invoice` lấy từ bản JSON đã lưu.

## Thu tiền và đối soát — chỉ admin

| Method | Đường dẫn | Chức năng |
|---|---|---|
| GET | `/api/admin/booking-payments?propertyId=1` | Đơn còn hiệu lực chưa có thanh toán, ưu tiên đang đối soát; thời gian chờ/cảnh báo quá hạn |
| GET | `/api/admin/booking-payments/{id}` | Bảng kê theo giá đã chốt; nếu đã thu tiền, trả `{success:true,invoice:{...}}` |
| POST | `/api/admin/booking-payments/{id}/confirm` | Body `{paymentMethod,expectedTotal}`; ghi một thanh toán/hóa đơn, lưu người/thời gian xử lý, xóa hạn giữ |
| POST | `/api/admin/booking-payments/{id}/reject` | Từ chối yêu cầu Pending; không thu tiền, giữ hạn gốc thay vì gia hạn |

Xác nhận lặp dùng lại hóa đơn đã có; không tự đổi trạng thái lưu trú. Đơn hết hạn/hủy không được thu tiền hoặc khôi phục phòng. Khóa theo phòng → booking để cùng thứ tự với đặt phòng, hết hạn và báo chuyển khoản. Nhân viên xác minh tiền thực nhận trước khi xác nhận.

`PUT /api/checkin/{id}` yêu cầu đã có thanh toán và thời gian nhận hợp lệ.
`GET /api/checkout/preview/{id}` trả bảng kê trực tiếp khi chưa thu tiền, hoặc `{success:true,invoice:{...}}` khi đã thu tiền.
`POST /api/checkout/{id}` chỉ trả phòng sau khi đã thanh toán; không tạo bản Payments thứ hai, trả lại mã và hóa đơn cũ.

Giao diện tương ứng: `reserve.html`, `my-bookings.html`, `my-invoices.html` cho customer; `payment-review.html` cho admin.

## Bảo trì local và sao lưu

Migration `010_customer_booking_access.sql` bổ sung dữ liệu, không tự ghép khách cũ. Migration `011_service_units.sql` thêm `Services.unit` mặc định `lượt` và `Booking_Services.unit_snapshot`; admin cần kiểm tra đơn vị thực tế của danh mục. Hai migration được đóng gói trong JAR và chạy sau migration 009. Schema tạo mới cũng có các cột tương ứng. Hóa đơn cũ không được viết lại để thêm đơn vị.

`scripts/PrepareCustomerAccess.java` dùng sau khi biên dịch main, với classpath JDBC/Spring của ứng dụng. Công cụ sao lưu copy-only, xác minh khả năng đọc backup, áp dụng migration 010 và liên kết bộ ID được chỉ định; không có kiểm thử API/browser hoặc gửi email. Phải dùng đúng JDK 25 và kiểm tra tên database được chỉ định. Đối số cuối tùy chọn là thư mục giữ backup ngoài target để tránh mất bản sao lưu khi clean.

Sao lưu và báo cáo bảo trì local được giữ ngoài repository tại `D:/Thuctap/Luu_tru_kiem_tra/Customer_Access_20261110/`; không đóng gói dữ liệu hoặc bản sao lưu vào Docker image.
