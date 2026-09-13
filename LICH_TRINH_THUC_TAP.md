# 📅 LỊCH TRÌNH THỰC TẬP & PHÂN CÔNG CÔNG VIỆC THEO TUẦN

> **Mục tiêu dự án:** Hoàn thành hệ thống Quản lý Homestay đủ chức năng demo để báo cáo thực tập (Target: 8 điểm).
> **Thời gian dự kiến:** 4 tuần (có thể điều chỉnh tùy theo deadline của trường).

---

## TUẦN 1: KHỞI TẠO DỰ ÁN & CƠ SỞ DỮ LIỆU
**Mục tiêu:** Chạy được dự án trên máy tất cả thành viên, hoàn thiện database và giao diện (UI) cơ bản.

| Thành viên | Nhiệm vụ chi tiết trong Tuần 1 |
|---|---|
| **Cả nhóm** | - Clone code từ GitHub về máy.<br>- Cài đặt đủ tool: SQL Server, Java 17, IntelliJ, VS Code.<br>- Mở dự án lên, cấu hình file `.env` chạy thử để đảm bảo mọi máy đều start được backend. |
| **Long & Hưng** | - Chạy file `schema.sql` và `seed_data.sql` trên SQL Server.<br>- Thống nhất kiểu dữ liệu, xác nhận các bảng đã liên kết đúng (Foreign Key).<br>- Test thử các câu query thống kê cơ bản trong SQL Server. |
| **Sỹ Đại (FE)** | - Hoàn thiện UI/UX trang `login.html`.<br>- Hoàn thiện UI khung chuẩn (Sidebar, Navbar) cho trang `rooms.html` để làm mẫu. |
| **Đức Đại (Leader)**| - Code xong API Login (`AuthController.java`).<br>- Code xong các API cơ bản (GET, POST) cho phòng ở `RoomController.java`.<br>- Hỗ trợ các bạn setup ban đầu.<br>- Review code của Sỹ Đại.<br>- Viết Javascript (`auth.js`) để kết nối API Login với giao diện `login.html`. |

---

## TUẦN 2: HOÀN THIỆN CÁC MODULE CƠ BẢN (KHÁCH HÀNG & PHÒNG)
**Mục tiêu:** Làm xong hoàn toàn các chức năng Thêm, Sửa, Xóa, Xem (CRUD) cho Phòng và Khách hàng.

| Thành viên | Nhiệm vụ chi tiết trong Tuần 2 |
|---|---|
| **Long & Hưng** | - Viết xong toàn bộ các câu lệnh SQL query cho phần Thống kê theo hướng dẫn.<br>- Gửi các câu lệnh SQL cho Đức Đại.<br>- Bắt đầu viết dàn ý cho báo cáo thực tập (Phần khảo sát hiện trạng, biểu đồ Use Case). |
| **Sỹ Đại (FE)** | - Dựa vào khung chuẩn, làm tiếp giao diện cho các trang: `customers.html`, `services.html`.<br>- Đảm bảo các form nhập liệu, bảng danh sách hiển thị đẹp mắt. |
| **Đức Đại (Leader)**| - Hoàn thiện các API (PUT, DELETE) cho `RoomController.java`.<br>- Code toàn bộ các API (GET, POST, PUT, DELETE) cho `CustomerController.java` và `ServiceStatController.java` (phần Services).<br>- Viết Javascript (`rooms.js`) để gọi API, hiển thị danh sách phòng, xử lý thêm/sửa/xóa phòng.<br>- Viết Javascript (`customers.js` và `services.js`) để hoàn thiện chức năng cho trang Khách hàng và Dịch vụ. |

---

## TUẦN 3: MODULE CỐT LÕI (BOOKING, CHECK-IN, CHECK-OUT)
**Mục tiêu:** Hoàn thành quy trình nghiệp vụ chính của Homestay (Đặt phòng -> Nhận phòng -> Trả phòng & Thanh toán).

| Thành viên | Nhiệm vụ chi tiết trong Tuần 3 |
|---|---|
| **Long & Hưng** | - Hỗ trợ test lỗi (nhập dữ liệu vào form xem có lưu đúng vào DB không).<br>- Viết tiếp báo cáo thực tập (Phần Thiết kế cơ sở dữ liệu, vẽ sơ đồ ERD). |
| **Sỹ Đại (FE)** | - Hoàn thiện giao diện các trang: `bookings.html`, `checkin.html`, `checkout.html`.<br>- Chú ý thiết kế hóa đơn thanh toán ở trang checkout sao cho chuyên nghiệp. |
| **Đức Đại (Leader)**| - Code API Check-in (`PUT /api/checkin/{id}`) trong `BookingController.java`.<br>- Code API Tạo Booking và API Check-out (tính tiền) trong `BookingController.java` (phần logic phức tạp).<br>- Viết Javascript (`bookings.js`, `checkin.js`, `checkout.js`) để gọi API, xử lý nghiệp vụ đặt phòng và trả phòng.<br>- Hỗ trợ test các phần liên kết dữ liệu giữa các bảng. |

---

## TUẦN 4: THỐNG KÊ, TỔNG DUYỆT & VIẾT BÁO CÁO
**Mục tiêu:** Hoàn thiện nốt phần Thống kê, test lỗi toàn hệ thống, đóng gói source code và hoàn thành Báo cáo quyển.

| Thành viên | Nhiệm vụ chi tiết trong Tuần 4 |
|---|---|
| **Sỹ Đại (FE)** | - Hoàn thiện giao diện trang `statistics.html` (có thể dùng thư viện Chart.js để vẽ biểu đồ nếu muốn điểm cao hơn).<br>- Rà soát lại CSS toàn bộ hệ thống, chỉnh sửa các phần bị lệch. |
| **Đức Đại (Leader)**| - Dùng các câu query của Long và Hưng để viết API Thống kê (`ServiceStatController.java`).<br>- Viết Javascript (`statistics.js`) gọi API để hiển thị số liệu thống kê lên màn hình.<br>- Ghép nối toàn bộ hệ thống, test end-to-end (từ lúc tạo khách hàng -> đặt phòng -> check out -> xem thống kê).<br>- Sửa các lỗi lặt vặt (bugs) phát sinh khi test và fix các bug khó. |
| **Long & Hưng** | - Chụp ảnh màn hình các chức năng đã hoàn thiện.<br>- Tổng hợp báo cáo quyển, chuẩn bị slide thuyết trình (PowerPoint). |
| **Cả nhóm** | - Họp nhóm tổng duyệt: chạy thử dự án từ đầu đến cuối.<br>- Phân công người thuyết trình và người thao tác demo dự án khi báo cáo với thầy cô. |

---

## 📌 LƯU Ý KHI LÀM VIỆC:
- **Deadline hàng tuần:** Cuối mỗi tuần (Vd: Tối Chủ Nhật) cả nhóm sẽ họp 15-30 phút để review xem tiến độ đến đâu.
- **Quy tắc đẩy code:** Làm xong phần nào (dù nhỏ), commit và push lên GitHub ngay, đừng để dồn đến cuối tuần.
- **Nếu gặp khó khăn (Stuck):** Không ôm việc, sau 2-3 tiếng không sửa được lỗi thì nhắn lên nhóm nhờ Leader hoặc các bạn khác hỗ trợ ngay.
