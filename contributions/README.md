# Bộ dữ liệu demo Long–Hưng

Ngày chốt: **29/10/2026**, giờ Việt Nam. Kỳ doanh thu: **11/2025–10/2026**; không có thanh toán sau ngày chốt. Dữ liệu giả lập, không dùng làm dữ liệu kinh doanh thật.

Nguồn: Hưng `Hungg@ccf373b738fea7a7276b8734a2f0782f32b93e50`, Long `feature/long-database@493776d3723d8713d465c8e3f72b0a979d1288b2`. Đại/Codex sửa và bổ sung theo yêu cầu ngày 29/10; không ghi nhận các kiểm thử bổ sung này là do Hưng/Long tự chạy.

| File | Số dòng dữ liệu |
| --- | ---: |
| hung/customers.csv | 50 |
| hung/services.csv | 12 |
| long/rooms.csv | 20 |
| long/bookings.csv | 60 |
| long/booking_services.csv | 52 |
| long/payments.csv | 40 |

Booking gồm 40 CheckedOut, 10 Cancelled, 6 Confirmed, 4 CheckedIn. Có khách quay lại, phòng dùng nhiều lần, ở một/nhiều đêm, hóa đơn có/không có dịch vụ; hai phòng bảo trì không có booking đang hoạt động. Khóa KH/DV/BK chỉ là mã của CSV, không phải ID database. Không tự thêm cột schema cho customer_code, service_code hoặc unit_label.

## Đối soát

Tổng 40 hóa đơn: **70.680.000 VNĐ**. Các giá lịch sử trong ba booking mẫu đầu tiên được giữ, dù khác giá danh mục hiện tại.

| Tháng | Doanh thu VNĐ |
| --- | ---: |
| 2025-11 | 3.585.000 |
| 2025-12 | 4.950.000 |
| 2026-01 | 8.660.000 |
| 2026-02 | 2.895.000 |
| 2026-03 | 11.780.000 |
| 2026-04 | 5.955.000 |
| 2026-05 | 3.090.000 |
| 2026-06 | 5.360.000 |
| 2026-07 | 8.185.000 |
| 2026-08 | 3.260.000 |
| 2026-09 | 7.080.000 |
| 2026-10 | 5.880.000 |

## Kiểm tra và nhập

Yêu cầu Python 3, Java 17, Maven Wrapper và SQL Server; cấu hình DB theo DOCS. Database phải qua migrations 001–005 và chuyển đổi mật khẩu SEC-01 theo hướng dẫn hiện có trước khi nhập. Không chạy seed lên database đang dùng.

Từ gốc repository:

```powershell
python backend/scripts/validate-demo-data.py contributions
.\backend\scripts\import-demo.ps1 -Database HomestayDB -Operation preview
# Tự tạo/drop database UUID riêng, không nhập vào DB đã chỉ định:
.\backend\scripts\import-demo.ps1 -Database HomestayDB -Operation test
# Sao lưu COPY_ONLY/CHECKSUM và VERIFYONLY trước khi nhập:
.\backend\scripts\import-demo.ps1 -Database HomestayDB -Operation apply
```

`-Database` phải trùng database cấu hình; không truyền password trên dòng lệnh. Chế độ mặc định chỉ preview. Apply yêu cầu ngày chốt vẫn là ngày hiện tại, để không đưa nhầm booking đang ở/tương lai sau khi bộ dữ liệu đã cũ. Chạy apply trên ngày khác cần xem xét/chốt lại bộ dữ liệu và validator trước.

Bộ nhập ánh xạ mã, dùng transaction và khóa nhập; tất cả lỗi trước commit đều rollback. Phòng mới dùng mã `DEMO-P101`… để không ghi đè phòng hiện có. Khách được tra bằng giấy tờ, chỉ dùng lại khi thông tin khớp. Dịch vụ cùng tên/cùng trạng thái được dùng lại mà không sửa giá catalog; nếu trạng thái khác, tạo tên riêng `DEMO-LH DVxxx - ...`, giữ nguyên dịch vụ vận hành. Giá/tên trên dòng sử dụng lấy từ dữ liệu lịch sử. 40 hóa đơn được tạo qua helper snapshot hiện tại trong cùng transaction.

Note booking bắt đầu bằng `[DEMO-LH-20261029:BKxxx]`. Chạy lại kiểm tra toàn bộ booking, dòng dịch vụ, payment và snapshot rồi bỏ qua, không chèn trùng hoặc viết lại hóa đơn. Nếu dữ liệu đã được người dùng thay đổi, bộ nhập từ chối; không tự sửa lại lịch sử. Không có thao tác xóa dữ liệu vận hành.

Receipt/backup path nằm trong thư mục evidence (mặc định backend/target), không commit lên Git. Bản .bak nằm ở thư mục dịch vụ SQL Server; giữ bản sao lưu trước nâng cấp/nhập theo quy trình vận hành.

## Kết quả ngày 29/10/2026

- Validator đạt toàn bộ cấu trúc, số lượng, tham chiếu, trạng thái/ngày chốt, lịch không trùng, tiền và đủ 12 tháng.
- 7 nhóm kiểm tra SQL/import đạt trên database UUID: rollback lỗi muộn; nhập đủ và giữ dữ liệu cũ; chạy lại không trùng; từ chối payment đầu vào thay đổi; SELECT kiểm tra dữ liệu mới; đổi giá catalog không làm lệch đối soát; loại dòng chưa thanh toán khỏi doanh thu.
- 15 ca Chrome thật về khách/dịch vụ đạt trên database QA riêng, dùng bản giao diện local hiện tại: xem `hung/UI_TESTS.md`. Không suy rộng thành kiểm tra mọi luồng của website.
- HomestayDB cấu hình được sao lưu, thử phục hồi và nâng cấp bằng migrations hiện có trước nhập. Giữ thông tin kinh doanh cũ và mật khẩu đăng nhập, không đặt lại password. Hóa đơn mẫu cũ thiếu dòng dịch vụ được giữ nguyên số tiền và gắn cảnh báo estimated/reconciliation theo migration; không tự thêm dòng dịch vụ để làm khớp.
- Sau nhập trên máy Đại: 2 tài khoản, 26 phòng, 55 khách, 17 dịch vụ, 65 booking, 52 dòng dịch vụ, 41 payment. Phần demo mới là 40 payment/70.680.000 VNĐ; tổng database gồm cả hóa đơn mẫu cũ, không được coi là doanh thu chỉ của bộ demo.

## Truy vấn của Long

Chọn đúng database trong SSMS rồi chạy `long/queries_check.sql`. Script chỉ SELECT và trả 9 nhóm: đếm từng bảng; ngày sai; trùng lịch; khoản tiền không khớp snapshot; doanh thu đã thu tháng/năm/phương thức; vòng đời payment; snapshot thiếu/sai JSON. Các nhóm phát hiện lỗi kỳ vọng rỗng đối với dữ liệu mới.

Database có hóa đơn legacy chưa đủ chi tiết vẫn có thể xuất hiện trong nhóm đối soát. Không sửa tổng hoặc viết lại hóa đơn đó chỉ để truy vấn trả rỗng. Doanh thu luôn lọc `payment_status = N'Đã thanh toán'` và dùng paid_at; không lấy giá catalog hiện tại để tính lại hóa đơn đã phát hành.
