# Tài liệu tổng hợp dự án Homestay

Cập nhật ngày 26/10/2026. Đây là file tài liệu duy nhất của dự án, gộp các báo cáo, hướng dẫn sử dụng/nâng cấp, kế hoạch và tài liệu kỹ thuật trước đây.

## Đọc nhanh

- **Chạy và đăng nhập:** chạy backend code hiện tại, mở `http://localhost:8080/pages/login.html`. Frontend mặc định được phục vụ cùng backend; xác thực bằng cookie phiên server và CSRF.
- **Database cũ:** sao lưu, dừng ghi và đọc kỹ [hướng dẫn nâng cấp](#migration). Cần đủ migration 001–005 và công cụ chuyển mật khẩu SEC-01; không chạy lại schema/seed để ghi đè database có dữ liệu.
- **Database mới:** dùng schema/seed hiện tại theo hướng dẫn cài đặt. Các đợt FE-01 đến FE-05 không thêm migration.
- **Sử dụng chức năng:** [đặt/hủy/nhận phòng](#guide-fe01), [dịch vụ/thanh toán/in lại và bảo trì phòng](#guide-fe0203), [khách hàng/lịch sử/tài khoản](#guide-fe0405).
- **Tiến độ:** xem [kế hoạch hiện tại](#plan). FE-01 đến FE-05 đã hoàn thành code và các kiểm tra được ghi trong báo cáo; REPORT-01/02, QA-01 và RELEASE-01 còn theo kế hoạch.
- **Bằng chứng kiểm tra:** các số lượng test thuộc từng đợt, có thể chứa hồi quy trùng nhau; không cộng thành số test độc lập. Kiểm tra DOM mô phỏng và HTTP/SQL riêng chưa thay thế QA trực quan/browser tích hợp.

Các mục có chữ **gốc** được giữ để không mất nội dung phân công, lịch trình và hướng dẫn cũ. Chúng có thể nhắc sessionStorage, mở file HTML/Live Server hoặc kết luận tiến độ cũ; nếu khác với kế hoạch, hướng dẫn FE và migration hiện tại, ưu tiên phần hiện tại. Tên file cũ trong cây thư mục/ví dụ lịch sử chỉ dùng để đối chiếu; tài liệu hiện nằm trong file này.

## Mục lục

- [Kế hoạch và tiến độ hiện tại](#plan)
- [Nâng cấp database và quy tắc API](#migration)
- [Hướng dẫn đặt, hủy và nhận phòng — FE-01](#guide-fe01)
- [Hướng dẫn dịch vụ, hóa đơn và phòng — FE-02/03](#guide-fe0203)
- [Hướng dẫn khách hàng và tài khoản — FE-04/05](#guide-fe0405)
- [Báo cáo FIX-02](#report-fix02)
- [Báo cáo CORE-01](#report-core01)
- [Báo cáo CORE-02](#report-core02)
- [Báo cáo CORE-03](#report-core03)
- [Báo cáo SEC-01](#report-sec01)
- [Báo cáo FIX-03](#report-fix03)
- [Báo cáo FE-01](#report-fe01)
- [Báo cáo FE-02](#report-fe02)
- [Báo cáo FE-03](#report-fe03)
- [Báo cáo FE-04](#report-fe04)
- [Báo cáo FE-05](#report-fe05)
- [Giới thiệu và hướng dẫn cài đặt gốc](#legacy-readme)
- [Tài liệu kỹ thuật gốc](#legacy-docs)
- [Hướng dẫn Đức Đại gốc](#legacy-dai)
- [Hướng dẫn phân công từng người gốc](#legacy-team)
- [Lịch trình thực tập gốc](#legacy-schedule)

---

<a id="plan"></a>

## Kế hoạch và tiến độ hiện tại

<a id="plan-h1"></a>

### KẾ HOẠCH HOÀN THIỆN WEB HOMESTAY — CÔNG VIỆC GIAO CHO AGENT

Cập nhật: 26/10/2026. Phụ trách: Đức Đại — backend Java, toàn bộ frontend JavaScript và tích hợp.

Tài liệu này thay thế kế hoạch cũ. Hiện trạng được đối chiếu bằng cách đọc mã nguồn; chưa xác nhận ứng dụng chạy đúng với SQL Server. Không dùng kết luận “đã hoàn thành 100%” trong nhật ký cũ làm bằng chứng nghiệm thu.

Bản chính duy nhất: mục Kế hoạch và tiến độ trong `Homestay/DOCS.md`. Bản kế hoạch trùng ở thư mục cha đã được gộp và bỏ; chỉ cập nhật mục này.


<a id="plan-h2"></a>

#### 1. Phạm vi và cách giao việc

- Đức Đại: API Java, JS, hợp đồng dữ liệu và tích hợp. Sỹ Đại: bố cục HTML/CSS; Long/Hưng: hỗ trợ SQL theo phân công gốc.
- Agent được giao một việc có thể sửa HTML/CSS hoặc migration SQL được nêu trong việc đó để hoàn thành chức năng. Không tự làm lại giao diện toàn dự án.
- Mỗi lần giao một mã việc. Agent phải đọc code thực tế, kiểm tra hướng dẫn AGENTS.md nếu có, hoàn thành đúng phạm vi và báo những gì đã kiểm chứng.
- Không đánh dấu hoàn thành chỉ vì đã có hàm hoặc nút. Phải đạt tiêu chí nghiệm thu; nếu thiếu SQL Server, ghi rõ phần chỉ mới đọc/biên dịch và phần chưa kiểm tra được.
- Không ghi đè thay đổi của người khác, không tự commit/push/deploy, không in nội dung `.env`, không chạy lại schema/seed trên database đang có dữ liệu.
- Kiểm tra và test được yêu cầu trong từng việc dưới đây. Chỉ dùng database thử nghiệm và dữ liệu riêng; không xóa dữ liệu thật để test.
- Không “đóng băng backend vĩnh viễn”. Ghi rõ thay đổi API và cập nhật frontend gọi API trong cùng việc.


<a id="plan-h3"></a>

##### Mẫu lệnh giao cho agent

> Đọc `Homestay/DOCS.md#plan`, thực hiện duy nhất việc **[MÃ VIỆC]**. Kiểm tra các việc phụ thuộc đã hoàn thành. Đọc code hiện tại trước khi sửa; làm đủ backend/frontend/migration được mô tả trong phạm vi. Chạy kiểm tra phù hợp và các tình huống nghiệm thu bằng database thử nghiệm nếu có. Không sửa ngoài phạm vi, không tự commit/push/deploy, không làm mất dữ liệu hoặc thay đổi của người khác. Khi xong báo: file đã sửa, hành vi sau sửa, kiểm tra đã chạy và kết quả, phần chưa kiểm tra được, thay đổi API/SQL và cách chạy nếu có. Chỉ đánh dấu hoàn thành khi có bằng chứng; nếu bị thiếu môi trường hãy ghi trạng thái chờ kiểm chứng.

Chỉ cần thay `[MÃ VIỆC]` bằng `FIX-01`, `CORE-01`… Không giao toàn bộ kế hoạch cho một agent trong một lần.


<a id="plan-h4"></a>

#### 2. Thứ tự thực hiện và phụ thuộc

| Thứ tự | Mã | Công việc | Phụ thuộc |
|---|---|---|---|
| 1 | FIX-01 | Sửa sai tên trường và trạng thái tải hóa đơn | Không |
| 2 | FIX-02 | Chặn check-in/checkout sai trạng thái, thanh toán lặp | FIX-01 |
| 3 | CORE-01 | Migration lưu giá và thông tin hóa đơn tại thời điểm giao dịch | FIX-02 |
| 4 | CORE-02 | Đặt phòng theo ngày, chống trùng lịch đồng thời, hủy booking | CORE-01 |
| 5 | CORE-03 | Gọi/hủy dịch vụ cho booking đang ở | CORE-01, CORE-02 |
| 6 | SEC-01 | Xác thực phía server, phân quyền, đổi mật khẩu | CORE-03 |
| 7 | FIX-03 | Chuẩn hóa lỗi API, chống chèn HTML/script, validation | SEC-01 |
| 8 | FE-01 | Hoàn thiện màn hình đặt phòng/nhận phòng | CORE-02, SEC-01, FIX-03 |
| 9 | FE-02 | Dịch vụ, hóa đơn chi tiết, thanh toán và in | CORE-03, SEC-01, FIX-03 |
| 10 | FE-03 | Phòng, bộ lọc và bảo trì | CORE-02, SEC-01, FIX-03 |
| 11 | FE-04 | Khách hàng và lịch sử đặt phòng | CORE-01, SEC-01, FIX-03 |
| 12 | FE-05 | Header tài khoản, đăng nhập/đăng xuất | SEC-01, FIX-03 |
| 13 | REPORT-01 | Thống kê chính xác và biểu đồ | CORE-01, SEC-01, FIX-03 |
| 14 | REPORT-02 | Xuất CSV dùng chung | FE-03, FE-04, REPORT-01 |
| 15 | QA-01 | Kiểm tra xuyên suốt và cập nhật tài liệu | Tất cả việc trên |
| 16 | RELEASE-01 | Cấu hình và hướng dẫn triển khai thực tế | QA-01 |

Làm tuần tự theo bảng là cách mặc định. Nếu muốn nhiều agent cùng làm: chỉ tách FE-01/FE-03/FE-04 sau FIX-03 và sau khi chốt API; mỗi agent sửa nhóm file riêng. FE-02 và FE-05 có thể cùng đụng CSS/HTML; REPORT-02 có thể cùng đụng api.js nên cần người tích hợp và tránh sửa đồng thời. Tài liệu này không tự khởi chạy agent.


<a id="plan-h5"></a>

#### 3. Quy ước nghiệp vụ và API cần giữ thống nhất


<a id="plan-h6"></a>

##### 3.1 Trạng thái và lịch phòng

- Booking: `Confirmed -> CheckedIn -> CheckedOut`; chỉ `Confirmed -> Cancelled`. Không nhận phòng lại booking đã hủy/kết thúc; không thu tiền cho booking chưa nhận phòng.
- Khoảng lưu trú là `[checkinDate, checkoutDate)`: được đặt nối tiếp đúng ngày checkout. Trùng lịch khi `existing.checkin_date < checkoutInput AND existing.checkout_date > checkinInput`, với booking `Confirmed` hoặc `CheckedIn`.
- `Rooms.status` phản ánh thời điểm hiện tại, không thay thế lịch trống theo ngày: Bảo trì; Đang sử dụng nếu có CheckedIn; Đã đặt nếu có Confirmed bao phủ ngày hiện tại; còn lại Trống. Đặt tháng sau không khóa toàn bộ phòng từ hôm nay.
- Booking quá ngày nhưng còn CheckedIn phải chặn nhận phòng khác vào cùng phòng cho đến khi xử lý trả phòng. Tìm phòng theo lịch phải loại thêm phòng còn khách nếu khoảng đặt bắt đầu hôm nay hoặc trong quá khứ. Lúc check-in luôn kiểm tra lại hiện trạng vật lý.
- Trạng thái hiển thị cần tính lại khi đọc danh sách/thống kê, hoặc dùng một hàm truy vấn dùng chung có cùng quy tắc; không phụ thuộc riêng vào lần ghi cuối cùng vì ngày có thể thay đổi mà không có request ghi.
- Hủy/checkout không được luôn gán phòng Trống. Phải tính lại theo các booking còn hiệu lực và bảo trì.
- Bảo trì hiện quản lý dạng bật/tắt, chưa quản lý lịch bảo trì theo ngày. Chặn bật bảo trì nếu còn booking Confirmed/CheckedIn; xử lý các booking trước. Chặn đặt và nhận phòng đang bảo trì.
- Tiền phòng tính theo số đêm đã đặt, tối thiểu một đêm hợp lệ. Nhận/trả sớm, gia hạn, phụ thu, đặt cọc và hoàn tiền chưa thuộc đợt này; không tự thu qua cổng thanh toán.
- Check-in chỉ từ ngày nhận dự kiến đến trước ngày trả dự kiến, theo ngày Asia/Ho_Chi_Minh; ngoài khoảng phải báo lỗi rõ. Checkout dùng số đêm đã đặt. Đây là quy tắc cần hiển thị trong hướng dẫn sử dụng.


<a id="plan-h7"></a>

##### 3.2 Hợp đồng dữ liệu

- Giữ camelCase ở JSON. Giữ các trường đang được frontend dùng nếu không cần thay đổi.
- Giữ GET danh sách trả mảng; thao tác ghi trả `{success, message, ...}`. Chưa bắt buộc bọc lại toàn bộ dữ liệu vào `data` vì sẽ gây thay đổi đồng loạt.
- Lỗi phải có HTTP status phù hợp và `{success:false, message, code?}`; frontend đọc được thông báo kể cả khi HTTP không phải 2xx. Không trả mảng rỗng khi query database lỗi.
- Trường thống nhất: `pricePerNight`, `totalBookingsThisMonth`. Preview có `services: [{bookingServiceId, serviceName, quantity, unitPrice, totalPrice}]`.
- Hóa đơn chính thức phải lấy số tiền/thông tin đã lưu lúc giao dịch, không tính lại bằng bảng giá hiện tại.
- Tiền dùng kiểu decimal/BigDecimal trong Java, không dùng float/double. Với VND, xác định làm tròn về đồng và kiểm tra giới hạn dữ liệu.


<a id="plan-h8"></a>

##### 3.3 API cần bổ sung

| Method | URL | Nội dung |
|---|---|---|
| GET | `/api/bookings/available-rooms?checkin=YYYY-MM-DD&checkout=YYYY-MM-DD` | Phòng phù hợp khoảng ngày; dữ liệu phòng như GET rooms |
| PUT | `/api/bookings/{id}/cancel` | Hủy Confirmed, tính lại trạng thái phòng |
| POST | `/api/bookings/{id}/services` | `{serviceId, quantity}`; trả ID dòng dịch vụ và giá đã lưu |
| DELETE | `/api/bookings/services/{id}` | Xóa dòng dịch vụ của booking đang CheckedIn |
| PUT | `/api/rooms/{id}/status` | `{status}`; chỉ thao tác Bảo trì hoặc kích hoạt Trống, kết quả trả trạng thái thực tế |
| GET | `/api/rooms?type=&status=&search=` | Lọc kết hợp; giữ tương thích khi không truyền query |
| GET | `/api/customers?search=` | Tìm tên/CCCD/hộ chiếu/điện thoại |
| GET | `/api/customers/{id}/bookings` | Booking, trạng thái, số đêm, tiền đã thanh toán; không nhân đôi do JOIN |
| GET | `/api/customers/{id}/history` | Khách, mảng bookings và totalPaid dạng chuỗi số nguyên đồng cho modal lịch sử |
| GET | `/api/statistics/yearly?year=2026` | Mảng đúng 12 số, tháng không doanh thu trả 0 |
| GET | `/api/payments/{id}` | Hóa đơn đã thanh toán để xem/in lại; số tiền và thông tin đã lưu |
| GET | `/api/auth/me` | Tài khoản hiện tại từ phiên phía server |
| POST | `/api/auth/logout` | Hủy phiên phía server |
| PUT | `/api/auth/change-password` | `{oldPassword,newPassword}`; lấy user từ phiên, không tin userId client |

`POST /api/auth/login` và các API cũ tiếp tục tồn tại; SEC-01 bổ sung cơ chế phiên. Việc bảo vệ CSRF phải chốt cách lấy/gửi token và cập nhật api.js cùng lúc.


<a id="plan-h9"></a>

#### 4. Các việc sửa lỗi trước


<a id="plan-h10"></a>

##### [ ] FIX-01 — Sửa sai dữ liệu giữa frontend và backend

**Hiện trạng:** checkout.js đọc `preview.roomPrice` trong khi backend trả `pricePerNight`; statistics.js đọc `bookingsThisMonth` trong khi backend trả `totalBookingsThisMonth`. Preview lỗi nghiệp vụ chưa được chặn trước khi điền hóa đơn.

**Phạm vi:** `frontend/js/checkout.js`, `frontend/js/statistics.js`; chỉ sửa HTML checkout nếu cần khóa nút thanh toán.

**Yêu cầu:** dùng đúng tên trường; xóa dữ liệu hóa đơn cũ khi mở modal; hiện loading; chỉ cho xác nhận sau khi preview hợp lệ; chặn bấm gửi lặp ở client; mở lại nút khi request thất bại và đặt lại ID booking khi đóng/kết thúc. Không thay đổi phép tính tiền của backend trong việc này.

**Nghiệm thu:** đơn giá và KPI booking khớp JSON; preview lỗi/404/mất kết nối không hiện tổng 0 như hóa đơn hợp lệ và không cho thanh toán; không gửi hai request do bấm liên tiếp. Ghi rõ nếu mới kiểm tra bằng mock.

**Lệnh giao:** dùng mẫu với mã `FIX-01`.

**Tiến độ 26/10/2026 — Chờ kiểm chứng thực tế:** đã sửa `checkout.js`, `statistics.js`, `checkout.html`. Đã kiểm tra cú pháp bằng `node --check` và chạy kiểm tra Node VM với DOM/API mô phỏng: đúng đơn giá/KPI; khóa thanh toán khi tải; xóa dữ liệu cũ; lỗi nghiệp vụ/404/mất mạng/dữ liệu thiếu; bỏ phản hồi cũ hoặc sau đóng modal; bấm liên tiếp chỉ gửi một request; mở lại nút khi thất bại; reset sau thành công. Tất cả kiểm tra mô phỏng đạt. Chưa chạy trình duyệt và SQL Server thực tế; chưa đánh dấu hoàn thành. Chống thanh toán trùng phía server thuộc FIX-02.


<a id="plan-h11"></a>

##### [x] FIX-02 — Kiểm soát chuyển trạng thái và thanh toán duy nhất

**Phạm vi:** `BookingController.java`; thêm migration riêng dưới `database/migrations/` cho ràng buộc Payments nếu cần.

**Yêu cầu:** check-in chỉ nhận Confirmed; preview/checkout chỉ xử lý CheckedIn; kiểm tra phòng không có khách khác và không bảo trì. Dùng transaction, khóa/UPDATE có điều kiện để hai request không cùng thành công. Khóa phòng trước rồi booking theo cùng thứ tự giữa các luồng; rollback rõ trong catch trước khi connection đóng. Checkout chỉ có một Payments cho mỗi booking; bổ sung UNIQUE phù hợp sau khi kiểm tra dữ liệu trùng. Migration phải dừng kèm hướng dẫn nếu dữ liệu cũ vi phạm; không tự xóa hóa đơn trùng. Không tự mở lại booking đã kết thúc.

**Nghiệm thu:** gọi check-in trên Cancelled/CheckedOut bị từ chối; checkout Confirmed bị từ chối; hai request checkout đồng thời chỉ tạo một hóa đơn, doanh thu cộng một lần; lỗi ở giữa thao tác không để booking/phòng/payment cập nhật dở. Kiểm tra đúng HTTP/JSON hiện tại và ghi thay đổi cho FIX-03.

**Lệnh giao:** dùng mẫu với mã `FIX-02`.

**Tiến độ 26/10/2026 — Hoàn thành code và kiểm tra tích hợp:** backend/test-compile đạt; 12 nhóm kiểm tra controller/JDBC trên SQL Server thật trong database thử riêng đạt, gồm đồng thời, rollback, UNIQUE và migration gặp dữ liệu trùng. Database thử đã xóa. Xem `DOCS.md#report-fix02` và lệnh `backend/scripts/test-fix02.ps1`. Migration chưa áp dụng vào database đang dùng, cần sao lưu rồi chạy theo `DOCS.md#migration`; chưa restart ứng dụng hoặc kiểm tra HTTP/trình duyệt. HTTP lỗi nghiệp vụ giữ 200 + success:false, chuẩn hóa ở FIX-03.


<a id="plan-h12"></a>

#### 5. Nghiệp vụ cần hoàn thiện


<a id="plan-h13"></a>

##### [x] CORE-01 — Lưu giá và dữ liệu hóa đơn tại thời điểm giao dịch

**Vấn đề:** hiện checkout lấy Rooms.price hiện tại; preview lấy Services.unit_price hiện tại. Sửa bảng giá có thể làm sai giao dịch đã đặt và hóa đơn cũ.

**Phạm vi:** migration SQL mới; `BookingController.java`; DTO/helper nếu cần; schema.sql cho cài mới phải đồng bộ. Đọc seed để không làm hỏng dữ liệu mẫu. Không chạy script tạo lại database.

**Yêu cầu:** lưu giá/đêm trong Bookings lúc tạo booking; lưu đơn giá và tên dịch vụ trong Booking_Services lúc gọi; lưu thông tin cần in lại hóa đơn trong Payments hoặc cấu trúc chi tiết hóa đơn phù hợp, gồm khách, phòng, ngày, số đêm, giá và chi tiết dịch vụ. Chốt schema và hợp đồng trước khi code; có thể thêm bảng/cột khi có lý do. Giá cũ chưa lưu chỉ có thể backfill theo dữ liệu hiện có: ghi rõ là ước tính, không tuyên bố khôi phục được giá lịch sử. Với hóa đơn đã thanh toán ưu tiên số tổng đã lưu trong Payments và báo các dòng không đối soát được. Cung cấp GET `/api/payments/{id}` và trả paymentId sau checkout. Việc tạo hóa đơn và chi tiết phải cùng transaction.

**Nghiệm thu:** đặt phòng rồi đổi giá phòng không đổi tiền booking; gọi dịch vụ rồi đổi giá/tên không đổi dòng đã gọi; xem lại hóa đơn sau đổi tên khách/phòng vẫn đúng bản đã phát hành; tổng các dòng khớp tổng hóa đơn. Migration có hướng dẫn thứ tự chạy, backup, xử lý dữ liệu cũ và chạy lại an toàn.

**Lệnh giao:** dùng mẫu với mã `CORE-01`.

**Tiến độ 26/10/2026 — Đã làm code và migration:** Bookings lưu giá; Booking_Services lưu tên/giá; checkout lưu JSON hóa đơn trong Payments cùng transaction; GET payments đọc bản lưu. Đã thêm POST gọi dịch vụ để có đường ghi snapshot thực tế; CORE-03 tiếp tục phần xóa và kiểm tra bổ sung, không viết lại POST từ đầu. Chạy `backend/scripts/test-core01.ps1` đạt 9 nhóm SQL Server thật. Hướng dẫn nâng cấp, dữ liệu cũ ước tính và hợp đồng JSON ở `DOCS.md#migration`. Migration 002 chưa áp dụng database đang dùng; báo cáo đầy đủ ở `DOCS.md#report-core01`.


<a id="plan-h14"></a>

##### [x] CORE-02 — Đặt phòng theo ngày, chống trùng và hủy booking

**Phạm vi:** `BookingController.java`, `RoomController.java`, helper truy vấn trạng thái dùng chung; migration/index nếu cần. Chưa làm UI đầy đủ ở việc này.

**Yêu cầu:** thêm API available-rooms và cancel theo mục 3; createBooking kiểm tra cùng quy tắc với API tìm phòng, không chỉ dựa Rooms.status. Kiểm tra customer/room tồn tại, ngày hợp lệ, không đặt ngày nhận trong quá khứ; dùng ngày Việt Nam nhất quán. Giữ khóa phòng xuyên suốt kiểm tra overlap và INSERT, bảo đảm các thao tác đặt/hủy/nhận/trả/bảo trì dùng cùng thứ tự khóa để hạn chế deadlock. Cập nhật check-in theo quy tắc ngày tại mục 3. Tính trạng thái hiện tại bằng helper dùng chung khi đọc phòng; sau hủy/trả phòng không xóa trạng thái của booking khác. Dùng giá đã lưu theo CORE-01.

**Nghiệm thu:** trùng ngày bị chặn; hai khoảng không giao nhau cùng phòng được đặt; checkout hôm trước bằng checkin hôm sau được phép; hai request đặt cùng phòng cùng khoảng chỉ một thành công; booking tháng sau không chặn toàn bộ lịch; hủy không làm phòng đang có khách thành Trống; booking quá hạn vẫn CheckedIn không cho khách khác nhận cùng phòng; phòng bảo trì không được đặt.

**Lệnh giao:** dùng mẫu với mã `CORE-02`.

**Tiến độ 26/10/2026 — Hoàn thành code/test:** đã có available-rooms/cancel, overlap dùng chung khi tìm/tạo, khóa phòng xuyên suốt transaction, check-in theo ngày Việt Nam, tính lại trạng thái sau hủy/checkout và khi đọc danh sách/chi tiết/thống kê. API sửa/bảo trì/xóa phòng cũ dùng cùng khóa; chặn bảo trì khi còn booking. `backend/scripts/test-core02.ps1` đạt 22 nhóm SQL Server thật; hồi quy FIX-02 12 nhóm và CORE-01 9 nhóm đều đạt. Xem `DOCS.md#report-core02`. Migration 003 mới thêm index/audit xung đột, chưa áp dụng DB đang dùng. UI đặt/hủy vẫn thuộc FE-01; bộ lọc/API status riêng thuộc FE-03.


<a id="plan-h15"></a>

##### [x] CORE-03 — Gọi và hủy dịch vụ cho khách đang ở

**Phạm vi:** `BookingController.java`, helper dịch vụ/hóa đơn; migration chỉ khi CORE-01 chưa đáp ứng trường cần thiết.

**Yêu cầu:** thêm POST/DELETE dịch vụ ở bảng API. Chỉ CheckedIn được thêm/xóa; service phải tồn tại và đang Hoạt động; quantity là số nguyên dương có giới hạn, không tự cắt 1.5 thành 1. Tính tiền server bằng giá hiện tại và lưu giá/tên theo CORE-01; trả bookingServiceId. Preview trả đầy đủ ID, tên, số lượng, đơn giá và thành tiền đã lưu. Mỗi lần gọi tạo một dòng riêng để dễ đối soát. Dùng khóa booking để đồng bộ với checkout; dịch vụ không được thêm sau khi tổng tiền đã chốt. Từ chối thao tác với ID không tồn tại.

**Nghiệm thu:** 2 dịch vụ × nhiều số lượng tính đúng; hủy một dòng cập nhật tổng; không gọi được dịch vụ Ngừng/quantity sai; không sửa booking kết thúc; gọi dịch vụ và checkout đồng thời không để phát sinh tiền chưa thu.

**Lệnh giao:** dùng mẫu với mã `CORE-03`.

**Tiến độ 26/10/2026 — Hoàn thành code/test:** hoàn thiện POST, thêm DELETE dòng dịch vụ, giữ snapshot giá/tên, chặn dữ liệu sai và tổng cộng dồn vượt giới hạn. Thêm/xóa dùng khóa Rooms → Bookings → dòng dịch vụ, đồng bộ với checkout; không sửa sau thanh toán. `backend/scripts/test-core03.ps1` đạt 21 nhóm SQL Server thật, hồi quy CORE-01 9 nhóm đạt. Xem `DOCS.md#report-core03`. Không cần migration mới; UI ở FE-02, phân quyền ở SEC-01, chuẩn hóa HTTP lỗi ở FIX-03.


<a id="plan-h16"></a>

#### 6. Xác thực và xử lý dữ liệu trước khi hoàn thiện giao diện


<a id="plan-h17"></a>

##### [x] SEC-01 — Phiên đăng nhập, phân quyền và đổi mật khẩu

**Phạm vi:** `AuthController.java`, cấu hình bảo mật/filter, pom.xml nếu cần; `api.js`, `auth.js`; migration mật khẩu, cấu hình CORS/session và tài liệu chạy.

**Quyết định:** dùng phiên phía server với cookie HttpOnly; ưu tiên frontend được phục vụ cùng origin qua WebConfig. Không dùng localStorage/sessionStorage làm bằng chứng đăng nhập. Nếu hỗ trợ Live Server khác origin phải cấu hình danh sách origin cụ thể, credentials và CSRF tương ứng; không dùng wildcard kèm credentials.

**Yêu cầu:** hash mật khẩu bằng thư viện được duy trì; migration tài khoản cũ không reset mật khẩu âm thầm, không giữ cơ chế đăng nhập plaintext lâu dài. Có `/me`, `/logout`, đổi mật khẩu lấy user từ phiên; xác minh mật khẩu cũ, kiểm tra độ dài/giới hạn và buộc đăng nhập lại sau đổi. Không trim mật khẩu làm thay đổi mật khẩu người dùng. Bảo vệ CSRF cho request ghi, cập nhật api.js; hạn chế thử đăng nhập liên tiếp, lỗi đăng nhập không tiết lộ tài khoản tồn tại. Chặn API khi chưa đăng nhập và kiểm tra vai trò tại server.

**Ma trận quyền:** admin được CRUD danh mục, bảo trì, xem thống kê và toàn bộ nghiệp vụ; staff được xem phòng/khách/dịch vụ, thêm/sửa khách, đặt/hủy/nhận/trả phòng, gọi dịch vụ và xem hóa đơn; staff không xóa khách, không sửa/xóa danh mục phòng/dịch vụ, không xem thống kê quản trị. Cả hai đổi mật khẩu của mình. Login và tài nguyên trang đăng nhập là public. Quyền giao diện sẽ hoàn thiện ở FE-05.

**Nghiệm thu:** gọi trực tiếp API khi chưa login nhận 401; staff vượt quyền nhận 403; sửa dữ liệu người dùng trong browser không nâng quyền; thiếu/sai CSRF bị chặn; logout/phiên hết hạn không gọi API tiếp; đổi mật khẩu cũ sai bị từ chối, mật khẩu mới đăng nhập được. Không ghi mật khẩu/token vào log.

**Lệnh giao:** dùng mẫu với mã `SEC-01`.

**Tiến độ 26/10/2026 — Hoàn thành code/test:** Spring Security + cookie phiên HttpOnly, CSRF, role server, /me, /logout, đổi mật khẩu và giới hạn thử. SQL 004 + công cụ offline hash toàn bộ legacy, không reset hoặc fallback plaintext; frontend chờ /me và gửi cookie/token. Có header/form đổi mật khẩu tối thiểu; FE-05 tiếp tục polish và đầy đủ nút theo quyền. `backend/scripts/test-sec01.ps1` đạt 13 nhóm HTTP thật/SQL Server trong DB thử; Node test frontend đạt 8 nhóm. Xem `DOCS.md#report-sec01`. Migration/công cụ hash chưa áp dụng DB đang dùng, cần backup/dừng ghi rồi làm theo `DOCS.md#migration` trước chạy backend mới.


<a id="plan-h18"></a>

##### [x] FIX-03 — Lỗi API, validation và hiển thị dữ liệu an toàn

**Phạm vi:** các controller, handler lỗi chung/DTO nếu phù hợp; `api.js`, các JS đang render dữ liệu. Không thay bố cục trang.

**Yêu cầu:** chuẩn hóa lỗi theo mục 3; GET database lỗi không trả []/{} như thành công. Client đọc lỗi JSON khi HTTP 400/401/403/404/409/500; 401 điều hướng login, 403 thông báo thiếu quyền. Không gửi chi tiết SQL/stacktrace về client. Validate trường bắt buộc, độ dài, ID, số tiền, enum và ngày ở server; FE dùng cùng quy tắc. CCCD là 12 chữ số; vì dự án có hộ chiếu, bổ sung lựa chọn loại giấy tờ và validation hộ chiếu riêng, không ép mọi khách thành CCCD. SĐT cho phạm vi Việt Nam là 10 chữ số bắt đầu 0. Khi cập nhật khách phải giữ tương thích dữ liệu cũ và nêu các bản ghi cần chuẩn hóa.

Thay việc nhét tên/ghi chú trực tiếp vào innerHTML/onclick bằng textContent, DOM và addEventListener hoặc escape theo đúng ngữ cảnh. Sửa cả tên có dấu nháy gây hỏng nút. Giữ nguyên API thành công hiện có nếu không cần đổi; đồng bộ mọi caller nếu có thay đổi. Lỗi bất ngờ log phía server, thông báo thân thiện phía client.

**Nghiệm thu:** tên `O'Neil` không làm hỏng thao tác; chuỗi HTML/script trong tên/ghi chú hiện thành văn bản; số âm/quantity thập phân/enum lạ bị chặn ở server; mất DB hiện lỗi, không hiện “không có dữ liệu”; phân biệt 401/403 và không redirect lặp.

**Lệnh giao:** dùng mẫu với mã `FIX-03`.

**Tiến độ 26/10/2026 — Hoàn thành code/test:** hợp đồng lỗi/status chung, kiểm tra kiểu/độ dài/ID/tiền/enum/ngày, không giấu lỗi DB thành dữ liệu rỗng; thêm loại giấy tờ/cảnh báo legacy và migration 005; render động bằng DOM/textContent/callback. FIX-03 HTTP/SQL Server 24 nhóm đạt (gồm SEC-01), hồi quy FIX-02/CORE-01/02/03 đủ 64 nhóm đạt, frontend 9 + 8 nhóm đạt. Xem `DOCS.md#report-fix03`. Migration 005 chưa áp dụng DB đang dùng; bố cục trên trình duyệt thật chưa kiểm chứng. Tiếp theo FE-01.


<a id="plan-h19"></a>

#### 7. Các chức năng giao diện cần thêm


<a id="plan-h20"></a>

##### [x] FE-01 — Đặt phòng, hủy và nhận phòng theo lịch

**Phạm vi:** `bookings.js/html`, `checkin.js/html`; CSS tối thiểu.

**Yêu cầu:** chọn ngày trước hoặc ngay khi đổi ngày phải gọi available-rooms; bỏ lọc bằng status Trống trên toàn bộ rooms. Xóa lựa chọn phòng hết hợp lệ, xử lý response đến sai thứ tự khi đổi ngày nhanh; trạng thái loading/rỗng/lỗi riêng. Nút Hủy chỉ hiện cho Confirmed, có xác nhận và tải lại dữ liệu. Giữ tính tiền dự kiến đã có; không biến ngày sai thành một đêm để hiển thị hợp lệ. Check-in giải thích điều kiện ngày, trạng thái và lỗi server. Dịch vụ chỉ hiển thị sau khi check-in thành công hoặc chuyển sang trang checkout của khách đang ở.

**Nghiệm thu:** đổi ngày cập nhật đúng phòng; ngày bằng/ngược bị chặn; đặt nối tiếp được; server báo phòng vừa có người đặt thì UI báo rõ và cập nhật lại; hủy/nhận phòng không gửi lặp, danh sách đúng sau reload.

**Lệnh giao:** dùng mẫu với mã `FE-01`.

**Tiến độ 26/10/2026 — Hoàn thành code và kiểm tra mô phỏng:** form gọi available-rooms theo ngày, bỏ lọc Rooms.status, chặn phản hồi cũ, tách loading/rỗng/lỗi, tính tiền và khóa gửi khi chưa đủ dữ liệu. Có hủy Confirmed, chống gửi lặp, refresh sau xung đột. Check-in có modal xác nhận hoạt động, điều kiện ngày, lỗi server, chặn double submit và liên kết sang trang khách đang ở chỉ sau thành công. Sửa lệch cột HTML/JS. `backend/src/test/js/booking-checkin.test.cjs` đạt 19 nhóm; hồi quy frontend FIX-03/SEC-01 đạt 17 nhóm. Xem `DOCS.md#report-fe01`. Chưa kiểm tra trình duyệt thật/ghép frontend với SQL Server trực tiếp trong đợt này; không thay backend hoặc cần migration mới.


<a id="plan-h21"></a>

##### [x] FE-02 — Dịch vụ, thanh toán, hóa đơn và in lại

**Phạm vi:** `checkout.js/html`, `style.css`; màn hình/modal xem lại hóa đơn có thể dùng chung với khách hàng.

**Yêu cầu:** modal thêm dịch vụ, số lượng, đơn giá; bảng dịch vụ có nút hủy dòng trước thanh toán. Preview có số đêm, giá đã lưu, tiền phòng, bảng kê và tổng. Không cho thanh toán khi đang tải/lỗi; sau thành công lấy hóa đơn chính thức qua paymentId, cho in và in lại bằng GET payments. Phân biệt nhãn “Tạm tính BK…” với “Hóa đơn HD…”. CSS print chỉ in nội dung hóa đơn, không in sidebar/nút/modal nền. Khi hủy hộp thoại in không tạo thêm thanh toán. Phương thức Tiền mặt/Chuyển khoản/Thẻ là thông tin nhân viên ghi nhận đã thu; không mô tả là tự xác minh tiền về/đã tích hợp cổng thanh toán, không tạo QR ngân hàng giả.

**Nghiệm thu:** gọi 2 chai nước + 1 thuê xe đúng tổng; hủy đúng dòng; đổi bảng giá không đổi giá đã ghi; thanh toán thành công tạo đúng một hóa đơn; in đủ tiếng Việt, chi tiết và phương thức; đóng/mở lại vẫn xem được hóa đơn đã thu.

**Lệnh giao:** dùng mẫu với mã `FE-02`.

**Tiến độ 26/10/2026 — Hoàn thành code/test:** modal gọi/hủy dịch vụ, bảng kê giá đã lưu, chặn gửi lặp và response cũ, phân biệt tạm tính BK/hóa đơn HD; lấy bản chính thức qua GET payments, có danh sách xem/in lại và đối soát mất phản hồi thanh toán bằng GET. Print CSS chỉ giữ hóa đơn. Xem `DOCS.md#report-fe02`, `DOCS.md#guide-fe0203`. Frontend FE-02/03 12 nhóm + 36 nhóm hồi quy đạt; SQL/HTTP CORE-02 mở rộng 26, SEC-01 13, CORE-03 21, CORE-01 9 nhóm đạt. Chưa kiểm chứng trực quan/in do Chrome headless bị chặn tạo profile; QA-01 tiếp tục. Không có migration mới.


<a id="plan-h22"></a>

##### [x] FE-03 — Bộ lọc phòng và bảo trì an toàn

**Phạm vi:** `RoomController.java`, `rooms.js/html`; dùng helper trạng thái CORE-02.

**Yêu cầu:** GET rooms hỗ trợ kết hợp type/status/search; search mã/tên; thêm nút bảo trì/kích hoạt và API status. Chỉ admin được sửa danh mục/bảo trì. Chặn sửa trạng thái tùy ý qua API cập nhật phòng cũ để không bỏ qua ràng buộc. Không cho gán trực tiếp Đã đặt/Đang sử dụng từ form danh mục. Bảo trì theo quy tắc mục 3, không thay đổi giá đã lưu ở booking. Trạng thái và bộ lọc không bị lỗi khi qua ngày mới.

**Nghiệm thu:** kết hợp ba bộ lọc đúng; kích hoạt phòng trở về trạng thái thực tế; phòng có booking hiệu lực không bật bảo trì; request PUT cũ cũng không lách được; staff bị chặn phía server.

**Lệnh giao:** dùng mẫu với mã `FE-03`.

**Tiến độ 26/10/2026 — Hoàn thành code/test:** GET rooms kết hợp type/status/search trên trạng thái tính theo ngày Việt Nam; có PUT rooms/{id}/status khóa phòng và kiểm tra booking, API cũ không gán trạng thái tự động hoặc lách bảo trì. UI có bộ lọc, refresh theo ngày/focus, chặn response cũ và chỉ admin có nút danh mục/bảo trì. Kiểm tra SQL/HTTP và DOM cùng đợt FE-02 đạt; xem `DOCS.md#report-fe03`. Cần QA-01 kiểm tra trực quan/UI tích hợp; không có migration mới.


<a id="plan-h23"></a>

##### [x] FE-04 — Tìm khách và xem lịch sử lưu trú

**Phạm vi:** `CustomerController.java`, `customers.js/html`; liên kết xem hóa đơn FE-02.

**Yêu cầu:** thêm query search và API lịch sử. Modal hiển thị booking, phòng, ngày, đêm, trạng thái, khoản đã thanh toán và link hóa đơn. Tổng chi tiêu chỉ cộng Payments đã thanh toán, không cộng tiền dự kiến của booking hủy/chưa trả; không cộng trùng do JOIN nhiều dịch vụ. Booking chưa thanh toán hiển thị rõ. Validation giấy tờ theo FIX-03; giữ chặn xóa khách có lịch sử. Xem lịch sử khách không tồn tại trả 404.

**Nghiệm thu:** tìm theo tên/giấy tờ/SĐT; khách có nhiều dịch vụ không bị lặp booking hoặc tiền; khách mới không lịch sử hiển thị đúng; tổng chi tiêu khớp hóa đơn; staff không xóa khách qua API.

**Lệnh giao:** dùng mẫu với mã `FE-04`.

**Tiến độ 26/10/2026 — Hoàn thành code/test:** tìm khách theo tên/giấy tờ/SĐT trên server; API bookings trả mảng và history có tổng khoản đã thanh toán, không JOIN dòng dịch vụ. Modal có ngày/đêm/trạng thái, tổng chi tiêu và link hóa đơn FE-02; có loading/rỗng/lỗi/race guard. Staff không xóa khách; giữ validation dữ liệu cũ. Bộ HTTP/SQL FIX-03 mở rộng đạt 29 nhóm; frontend FE-04/05 14, SEC-01/FE-05 11, FE-01 19, FE-02/03 12, FIX-03 9 nhóm đạt. Xem `DOCS.md#report-fe04`, `DOCS.md#guide-fe0405`. Không migration mới; QA-01 tiếp tục trực quan/UI tích hợp.


<a id="plan-h24"></a>

##### [x] FE-05 — Header tài khoản và trải nghiệm phiên đăng nhập

**Phạm vi:** `api.js`, `auth.js`, các trang HTML và CSS dùng chung. Không gắn mã chỉ dành cho login-form lên trang khác gây null error.

**Yêu cầu:** lấy tên/quyền qua `/me`, hiển thị header dùng chung; nút đổi mật khẩu và logout gọi server; login khi còn phiên chuyển vào trang chính; phiên hết hạn trở về login. Ẩn/khóa thao tác theo ma trận quyền SEC-01; vẫn dựa vào server để bảo vệ. Bỏ cơ chế kiểm tra chỉ có currentUser trong sessionStorage. Xử lý tải `/me` trước khi tải dữ liệu được bảo vệ; không để mỗi trang tự tạo một cơ chế auth riêng.

**Nghiệm thu:** mở URL trực tiếp khi chưa login được xử lý đúng; refresh vẫn có phiên; logout rồi back không dùng được API; tên/quyền đúng; đổi mật khẩu thành công buộc đăng nhập lại; không lỗi JS do thiếu phần tử trang login.

**Lệnh giao:** dùng mẫu với mã `FE-05`.

**Tiến độ 26/10/2026 — Hoàn thành code/test:** account.js dùng chung trên 7 trang, tên/quyền từ /me, header và đổi mật khẩu/logout có khóa gửi. Nội dung chờ xác thực, có retry khi lỗi, pagehide xóa mật khẩu và pageshow kiểm tra phiên; phản hồi auth/401/CSRF cũ không ghi đè phiên mới. Login thiếu form không lỗi, còn phiên chuyển rooms. Đủ nút theo quyền cho phòng/dịch vụ/khách/thống kê; server tiếp tục bảo vệ. 65 lượt nhóm frontend và 29 nhóm HTTP/SQL cùng FE-04 đạt. Xem `DOCS.md#report-fe05`; cần QA-01 kiểm tra trực quan/mobile/BFCache thật.


<a id="plan-h25"></a>

#### 8. Báo cáo và xuất dữ liệu


<a id="plan-h26"></a>

##### [ ] REPORT-01 — Thống kê tháng/năm và biểu đồ

**Phạm vi:** `ServiceStatController.java`, `statistics.js/html`, CSS tối thiểu; dùng trạng thái CORE-02.

**Yêu cầu:** giữ tên `totalBookingsThisMonth`; tổng booking tính theo created_at trong đúng tháng và năm, ghi rõ đây là số đơn tạo trong tháng. Doanh thu tháng/năm chỉ tính Payments đã thanh toán theo paid_at, thống nhất múi giờ. Sửa revenue query đang thiếu lọc payment_status. API yearly trả đủ 12 tháng, validate month/year; KPI phòng và biểu đồ trạng thái dùng cùng nguồn. Thêm biểu đồ cột doanh thu năm và doughnut trạng thái phòng; dùng phiên bản Chart.js cố định, destroy/update chart khi đổi năm. Giữ bảng hóa đơn tháng, hiện phương thức thanh toán; lỗi không giả thành doanh thu 0. Chỉ admin truy cập thống kê.

**Nghiệm thu:** tháng không doanh thu trả 0; tháng cùng số nhưng khác năm không bị cộng chung; tổng 12 tháng bằng tổng hóa đơn hợp lệ cả năm; đổi bộ lọc không chồng chart; KPI khớp danh sách; staff gọi trực tiếp bị từ chối.

**Lệnh giao:** dùng mẫu với mã `REPORT-01`.


<a id="plan-h27"></a>

##### [ ] REPORT-02 — Xuất CSV dùng chung

**Phạm vi:** helper export trong `api.js` hoặc file export riêng; rooms/customers/statistics JS và nút HTML liên quan.

**Yêu cầu:** đợt này xuất CSV mở bằng Excel, đặt nhãn “Xuất CSV”; không đặt đuôi .xlsx cho dữ liệu CSV. Xuất dữ liệu đang lọc, không xuất nút thao tác; xác định danh sách cột rõ ràng thay vì đoán cột cuối. UTF-8 BOM; escape dấu nháy, dấu phẩy, xuống dòng; chống công thức bắt đầu =,+,-,@ và ký tự điều khiển phù hợp với dữ liệu người dùng. Giữ CCCD/SĐT như văn bản khi nhập bằng Excel, hướng dẫn nhập cột Text nếu Excel tự bỏ số 0; không dùng công thức để giữ định dạng. Thu hồi object URL sau tải. Không xuất khi dữ liệu lỗi/chưa tải; nếu không có dòng cần thông báo rõ.

**Nghiệm thu:** file mở được tiếng Việt; tên có dấu phẩy/nháy/xuống dòng không vỡ cột; không có cột thao tác; tổng doanh thu khớp bảng; giá trị thử công thức không được thực thi. Nếu cần XLSX thật, lập việc riêng sau đợt này.

**Lệnh giao:** dùng mẫu với mã `REPORT-02`.


<a id="plan-h28"></a>

#### 9. Nghiệm thu toàn hệ thống và chuẩn bị sử dụng thực tế


<a id="plan-h29"></a>

##### [ ] QA-01 — Kiểm tra xuyên suốt và đồng bộ tài liệu

**Phạm vi:** toàn dự án để kiểm tra; bổ sung bộ kiểm tra có ý nghĩa, hướng dẫn và báo cáo kết quả. Lỗi lớn ngoài phạm vi cần ghi lại thành việc riêng, không che bằng đổi kỳ vọng.

**Yêu cầu:** biên dịch bằng Maven Wrapper trong backend; kiểm tra JavaScript và các luồng dưới đây trên SQL Server thử nghiệm. Chạy ít nhất các test tích hợp cho thanh toán lặp, overlap đồng thời và transaction rollback; mock đơn thuần không đủ chứng minh khóa database. Không ghi “pass” nếu chưa có môi trường. Cập nhật README, DOCS và DOCS.md#legacy-dai cho đúng API/schema/cách chạy; sửa tuyên bố 100% cũ thành tiến độ có bằng chứng. Lưu báo cáo `Homestay/BAO_CAO_NGHIEM_THU.md` gồm môi trường, ngày, case, kỳ vọng, thực tế, kết quả và hạn chế; không kèm thông tin bí mật.

**Kịch bản bắt buộc:**
1. Login admin/staff, kiểm tra 401/403/CSRF, đổi mật khẩu, logout.
2. Tạo khách/phòng, đặt phòng, nhận phòng, gọi dịch vụ, preview, thanh toán, xem/in lại hóa đơn.
3. Booking trùng ngày, nối tiếp, tương lai, hủy, phòng bảo trì, khách quá hạn chưa trả.
4. Hai request đặt cùng lúc; hai request checkout cùng lúc; gọi dịch vụ đồng thời checkout; gây lỗi giữa transaction.
5. Đổi giá sau đặt/gọi dịch vụ, đổi tên sau thanh toán; hóa đơn đã phát hành giữ đúng dữ liệu.
6. Lọc/lịch sử/CSV/biểu đồ, tháng rỗng, đối soát tổng doanh thu.
7. Tên có dấu nháy/HTML, input sai, mất mạng/mất DB, bấm liên tiếp và phiên hết hạn.
8. Màn hình điện thoại và in hóa đơn không cắt mất nội dung thiết yếu.

**Nghiệm thu:** có báo cáo thật và danh sách lỗi còn tồn; không còn lỗi mất dữ liệu, thu trùng, trùng lịch, vượt quyền; các mục chờ môi trường ghi rõ. Ảnh demo/báo cáo Word và diễn tập nhóm do người phụ trách thực hiện sau khi luồng đã đạt.

**Lệnh giao:** dùng mẫu với mã `QA-01`.


<a id="plan-h30"></a>

##### [ ] RELEASE-01 — Cấu hình triển khai có thể làm lại

**Phạm vi:** cấu hình backend, WebConfig, frontend API_BASE, README/.env.example, hướng dẫn vận hành và script đóng gói phù hợp. Không tự xuất bản lên Internet.

**Yêu cầu:** API_BASE dùng đường dẫn cùng origin `/api` theo cấu hình phục vụ frontend; bỏ URL localhost cố định cho bản triển khai. Đóng gói frontend cùng ứng dụng hoặc ghi rõ cách phân phối và cấu hình thư mục ngoài; không để bản jar phụ thuộc ngầm vào cwd của máy dev. Tách cấu hình dev/prod; không có mật khẩu mặc định cho prod, không đóng gói .env. Hướng dẫn HTTPS/reverse proxy, cookie Secure/HttpOnly/SameSite, timeout phiên, tài khoản DB giới hạn quyền, CORS theo môi trường, thứ tự migration, backup và phục hồi, log lỗi không chứa bí mật. Không coi cấu hình trustServerCertificate=true và mật khẩu mẫu là cấu hình production mặc định.

**Nghiệm thu:** chạy gói build trên thư mục sạch với cấu hình mẫu đã điền riêng; truy cập trang login và API cùng origin; thiếu cấu hình bắt buộc báo rõ; có quy trình migration/backup/khôi phục kiểm chứng trên DB thử. Ghi lại những bước cần quản trị viên cung cấp hạ tầng. Chỉ triển khai thật khi người dùng yêu cầu cụ thể.

**Lệnh giao:** dùng mẫu với mã `RELEASE-01`.


<a id="plan-h31"></a>

#### 10. Những mục không tự mở rộng trong đợt này

- Cổng thanh toán/đối soát ngân hàng tự động, đặt cọc/hoàn tiền, hóa đơn điện tử thuế.
- Đặt nhiều phòng trong một đơn, nhiều chi nhánh, website cho khách tự đặt, thông báo email/SMS.
- Gia hạn, trả sớm, phụ thu, khuyến mãi và lịch bảo trì theo khoảng ngày.
- Đổi framework frontend hoặc chuyển toàn bộ JDBC sang ORM.

Khi cần các chức năng này, thêm đầu việc và thiết kế hợp đồng tương ứng; không hứa schema hiện tại đáp ứng mọi mở rộng.


<a id="plan-h32"></a>

#### 11. Bảng theo dõi cho người giao việc

Chọn một trạng thái: Chưa làm / Đang làm / Chờ kiểm chứng / Hoàn thành. Ghi bằng chứng như tên báo cáo hoặc test đã chạy; không tự đánh dấu dựa trên checklist cũ.

| Mã | Trạng thái ban đầu | Bằng chứng / ghi chú |
|---|---|---|
| FIX-01 | Chờ kiểm chứng | Đã sửa; cú pháp và DOM/API mô phỏng đạt, chưa kiểm chứng trình duyệt + SQL Server |
| FIX-02 | Hoàn thành code/test | 12 nhóm SQL Server thật đạt; xem DOCS.md#report-fix02; migration DB đang dùng chưa áp dụng |
| CORE-01 | Hoàn thành code/test | 9 nhóm SQL Server thử đạt; migration DB đang dùng chưa áp dụng; xem DOCS.md#report-core01 |
| CORE-02 | Hoàn thành code/test | 22 nhóm SQL Server thử + 21 nhóm hồi quy đạt; xem DOCS.md#report-core02; UI ở FE-01 |
| CORE-03 | Hoàn thành code/test | POST/DELETE và 21 nhóm SQL Server thử + 9 nhóm hồi quy đạt; xem DOCS.md#report-core03 |
| SEC-01 | Hoàn thành code/test | 13 nhóm HTTP/SQL Server + 8 nhóm frontend đạt; SQL 004/hash DB đang dùng chưa áp dụng |
| FIX-03 | Hoàn thành code/test | 24 nhóm HTTP/SQL + 64 nhóm hồi quy + 17 nhóm frontend đạt; SQL 005 chưa áp dụng DB đang dùng |
| FE-01 | Hoàn thành code/test mô phỏng | 19 nhóm FE-01 + 17 nhóm hồi quy frontend đạt; xem DOCS.md#report-fe01; cần kiểm chứng trình duyệt ở QA-01 |
| FE-02 | Hoàn thành code/test | Dịch vụ, bản hóa đơn đã lưu, in lại; DOM + SQL/HTTP đạt; cần QA trực quan; xem DOCS.md#report-fe02 |
| FE-03 | Hoàn thành code/test | Bộ lọc server, bảo trì/activation có khóa và phân quyền; DOM + SQL/HTTP đạt; xem DOCS.md#report-fe03 |
| FE-04 | Hoàn thành code/test | Tìm server, lịch sử/chi tiêu đã thu, link hóa đơn; HTTP/SQL và DOM đạt; xem DOCS.md#report-fe04 |
| FE-05 | Hoàn thành code/test | Header, quyền UI, đổi mật khẩu/logout, kiểm tra phiên/Back; DOM và HTTP/SQL đạt; xem DOCS.md#report-fe05 |
| REPORT-01 | Chưa làm | Đã có tổng quan và báo cáo tháng cơ bản |
| REPORT-02 | Chưa làm | Chưa có helper xuất CSV |
| QA-01 | Chưa làm | Chưa có báo cáo nghiệm thu của đợt này |
| RELEASE-01 | Chưa làm | Chưa kiểm chứng gói triển khai độc lập |

---

<a id="migration"></a>

## Nâng cấp database và quy tắc API

<a id="migration-h1"></a>

### Migration database Homestay


<a id="migration-h2"></a>

#### FIX-03 — Hợp đồng lỗi, validation và loại giấy tờ


<a id="migration-h3"></a>

##### Nâng cấp dữ liệu khách hàng

Database cũ: sao lưu/dừng ghi, hoàn thành 001–004 và công cụ chuyển mật khẩu SEC-01, sau đó chạy `005_customer_document_type.sql` trước khi chạy backend hiện tại. Migration 005 thêm `Customers.document_type`; các số cũ đúng 12 chữ số được đánh dấu CCCD, các giá trị khác là LEGACY, không tự đoán thành hộ chiếu và không thay số giấy tờ/điện thoại/email. Script chạy lại được và liệt kê bản ghi cần chuẩn hóa. Database mới có cột trong schema.sql; seed dùng CCCD mặc định.

**Migration 005 chưa áp dụng lên database đang dùng trong phiên làm việc.** Kiểm tra migration và các thao tác lỗi chỉ chạy trong database UUID thử riêng.

| Trường | Quy tắc khi tạo mới/thay đổi |
|---|---|
| fullName | Chuỗi bắt buộc, tối đa 100 ký tự |
| documentType | CCCD hoặc PASSPORT; LEGACY chỉ giữ cho dữ liệu cũ chưa xác định |
| cccd | Giữ tên field để tương thích API. CCCD: 12 chữ số ASCII; PASSPORT: 6–20 chữ cái ASCII hoặc chữ số theo quy tắc nhập liệu của dự án |
| phone | 10 chữ số, bắt đầu 0; phạm vi điện thoại Việt Nam |
| email | Không bắt buộc, tối đa 100; có @ và phần tên miền, không chứa khoảng trắng |
| address | Không bắt buộc, tối đa 200 ký tự |

GET customers/chi tiết bổ sung `documentType` và `validationWarnings` (mảng thông báo), giữ các field cũ. Khi sửa bản ghi cũ, số giấy tờ/phone/email chưa hợp lệ nhưng không đổi vẫn được giữ nguyên để sửa địa chỉ/tên; có cảnh báo. Giá trị mới/thay đổi phải hợp lệ. Có thể chọn loại giấy tờ đúng và nhập số mới để chuẩn hóa; khi đúng, cảnh báo tự hết. Không dùng regex hộ chiếu này như một cam kết hỗ trợ mọi định dạng giấy tờ của tất cả quốc gia.

Form khách hàng có lựa chọn loại giấy tờ và thông báo cần chuẩn hóa. Không ép số cũ thành CCCD hoặc tự xóa khoảng trắng trong giá trị cũ không được sửa. Ràng buộc UNIQUE của cccd tiếp tục dùng chung như schema cũ, chưa tách uniqueness theo quốc gia/loại giấy tờ.


<a id="migration-h4"></a>

##### Hợp đồng lỗi HTTP

Mọi lỗi API trả `{success:false,code,message}`; message không chứa SQL/driver/stacktrace. JSON thành công của API cũ giữ nguyên; GET danh sách vẫn trả mảng. Các thay đổi dưới đây thay thế ghi chú cũ rằng lỗi nghiệp vụ trả 200:

| Status | Ý nghĩa |
|---|---|
| 400 | Input sai, thiếu trường, ID/kiểu/độ dài/ngày/số tiền/enum sai |
| 401 | Chưa đăng nhập, phiên hết hiệu lực hoặc credential sai |
| 403 | Thiếu quyền hoặc CSRF sai |
| 404 | ID tài nguyên hợp lệ nhưng không tồn tại |
| 405 / 415 | Method hoặc định dạng nội dung không hỗ trợ |
| 409 | Trùng dữ liệu, liên kết không cho xóa, sai trạng thái hoặc xung đột nghiệp vụ |
| 429 | Giới hạn thử đăng nhập/đổi mật khẩu |
| 500 | Lỗi xử lý bất ngờ; có mã tham chiếu cho log server |
| 503 | Không đọc/ghi được dữ liệu hoặc không kiểm tra được phiên; không trả []/{} như thành công |

Frontend đọc JSON cả khi HTTP không phải 2xx, hiển thị message thực, 401 chuyển login, 403 giữ thông báo thiếu quyền. Vẫn nhận diện success:false của server cũ để tránh hiểu nhầm thành công. Không tự retry thao tác ghi. Lỗi tải bảng xóa dữ liệu cũ và hiển thị lỗi; lỗi thống kê hiện KPI `--`, không thay bằng 0.

Server/frontend cùng kiểm tra giới hạn tên/mã/mô tả/ghi chú theo schema, enum phòng/dịch vụ/booking/phương thức thanh toán, số nguyên không âm hoặc dương theo trường, tháng 1–12/năm 1–9999. Room types: Single/Double/VIP/Family; service status: Hoạt động/Ngừng; phương thức: Tiền mặt/Chuyển khoản/Thẻ ngân hàng. Input có sai kiểu không được .toString() rồi âm thầm nhận; số thập phân không bị cắt thành số nguyên. Lỗi SQL bất ngờ được log ở server, không cộng chi tiết exception vào message.

Render dữ liệu động dùng textContent/DOM và addEventListener, không ghép tên/ghi chú/JSON vào innerHTML/onclick. Template HTML tĩnh của form đổi mật khẩu không có dữ liệu người dùng. Dữ liệu như `O'Neil` hay chuỗi trông như HTML được lưu/hiện dưới dạng văn bản, không xóa dấu nháy hoặc tự coi là mã.


<a id="migration-h5"></a>

##### Kiểm tra FIX-03

```powershell
# Từ backend
.\scripts\test-fix03.ps1
node .\src\test\js\validation-render.test.cjs
node .\src\test\js\security-client.test.cjs
```

Test HTTP bao gồm hồi quy SEC-01, migration 005, validation, status 400/404/409/500/503 và lỗi truy vấn được cố ý gây trong database thử. Test DOM gắn chặn innerHTML và kiểm tra callback để phát hiện việc ghép dữ liệu vào HTML. Các kiểm tra frontend là mô phỏng DOM/fetch, chưa phải kiểm tra trình duyệt. Kết quả ở `DOCS.md#report-fix03`.


<a id="migration-h6"></a>

#### SEC-01 — Phiên đăng nhập và mật khẩu hash


<a id="migration-h7"></a>

##### Nâng cấp database đang có tài khoản

1. Sao lưu database và kiểm tra phương án phục hồi. Dừng tất cả bản backend cũ và thao tác ghi, không chạy đồng thời bản cũ/new trong lúc nâng cấp.
2. Chọn đúng database trong SSMS; chạy 001 → 002 → 003 nếu chưa chạy, sau đó `004_account_password_metadata.sql`. Script 004 thêm `password_scheme='legacy'` cho dữ liệu cũ và `auth_version`, không tự reset/hash mật khẩu.
3. Từ thư mục backend chạy công cụ **ngoài tiến trình server**:

   ```powershell
   .\scripts\migrate-passwords.ps1 -Apply
   ```

   Công cụ dùng DBConnection/.env hiện tại, khóa bảng Users trong transaction, hash toàn bộ password_scheme legacy bằng Spring Security PBKDF2, giữ nguyên từng ký tự mật khẩu cũ và tăng auth_version. Tài khoản đã hash hợp lệ được bỏ qua. Scheme lạ/hash sai làm rollback cả đợt, không chuyển một phần và không in mật khẩu/hash. Chạy lại được; không có nhánh đăng nhập plaintext hoặc tự nâng cấp khi login. `-Apply` chỉ là tham số xác định thao tác ghi, không thực hiện tự động trong quá trình build/test.
4. Kiểm tra thông báo số tài khoản đã chuyển, chạy tiếp 005 theo phần FIX-03 rồi khởi động backend hiện tại. Backend từ chối khởi động nếu còn legacy/hash không đúng định dạng hoặc chưa có cột 004. Không chạy lại seed để sửa tài khoản cũ. Không quay về backend cũ mà không phục hồi backup tương ứng vì mật khẩu đã hash.
5. Mở `http://localhost:8080/pages/login.html`, đăng nhập bằng mật khẩu cũ. Tài khoản đổi mật khẩu sau đó phải dùng mật khẩu mới 12–128 ký tự; không trim khoảng trắng. Các tài khoản có mật khẩu cũ trống hoặc dài ngoài giới hạn đăng nhập cần quản trị viên đối soát và xử lý riêng, không tự reset ngầm.

**Migration SQL 004 và công cụ hash chưa chạy trên database đang dùng trong phiên làm việc.** Chỉ kiểm tra trên database UUID thử riêng. Seed cho database mới đã thay plaintext bằng hai hash có salt độc lập của mật khẩu demo 123456; không dùng seed cho database cũ.

Hash có tiền tố `{pbkdf2-v58}` và password_scheme `pbkdf2-v58`, dùng `Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8()` qua DelegatingPasswordEncoder, không cấu hình noop/plaintext. Với database đã nâng cấp bằng 004, INSERT tài khoản mới phải ghi rõ password_scheme và hash tương ứng; default legacy giúp không đoán nhầm định dạng dữ liệu cũ.


<a id="migration-h8"></a>

##### Cách chạy và cấu hình

- Frontend mặc định gọi `/api` cùng origin, chạy qua backend WebConfig. Không mở HTML bằng file://. Hướng dẫn Live Server cũ không còn là cách mặc định.
- Cookie phiên tên HOMESTAY_SESSION, HttpOnly, SameSite=Lax, hết hạn sau 30 phút không hoạt động, chỉ truyền bằng cookie, không dùng URL session ID. Khi triển khai HTTPS đặt `COOKIE_SECURE=true`; cần cấu hình HTTPS/reverse proxy thực tế ở RELEASE-01. Phiên nằm trong bộ nhớ một backend, khởi động lại sẽ yêu cầu đăng nhập lại.
- CORS mặc định không mở origin ngoài. Chỉ khi cần dev khác port, đặt `AUTH_ALLOWED_ORIGINS=http://localhost:5500` (có thể nhiều origin cụ thể ngăn bằng dấu phẩy) và định nghĩa `window.HOMESTAY_API_BASE='http://localhost:8080/api'` trước api.js. Dùng cùng hostname localhost cho cả hai bên để cookie SameSite làm việc; không trộn localhost với 127.0.0.1. Không cho wildcard với credentials. Mã không tự tin header X-Forwarded-For của client.
- Login giới hạn mỗi tài khoản 5 lần thử và mỗi IP 30 lần thử trong 15 phút; thành công xóa bộ đếm tài khoản, không xóa bộ đếm IP. Đổi mật khẩu cũng giới hạn số lần thử. Bộ đếm có giới hạn bộ nhớ và nằm trong một tiến trình; nhiều instance cần cơ chế chung khi triển khai mở rộng.
- Vai trò và auth_version được đối chiếu DB trên các request đã có phiên. Đổi mật khẩu làm tất cả phiên cũ bị từ chối ở request tiếp theo; đổi role/xóa tài khoản cũng vô hiệu hóa phiên cũ. Request đã qua kiểm tra trước thời điểm đổi không bị hủy giữa chừng.
- Thư viện mới nằm trong spring-boot-starter-security, theo BOM hiện tại. Trong môi trường này cache Maven mặc định chỉ đọc, nên dependency được tải vào `D:/Thuctap/.maven-cache`. Các script tự chọn cache workspace nếu có, hoặc cache người dùng; có thể truyền `-MavenRepository` để chỉ định chỗ được ghi. Không thay settings Maven toàn máy.


<a id="migration-h9"></a>

##### API xác thực và quyền

| API | Hành vi |
|---|---|
| GET `/api/auth/csrf` | Public, tạo phiên/token ẩn danh; trả `{success,token,headerName:'X-CSRF-TOKEN'}` |
| POST `/api/auth/login` | `{username,password}` + cookie/token CSRF; tạo phiên mới và đổi session ID, trả `{success,message,user}`. Lỗi sai tài khoản/mật khẩu cùng thông báo 401; vượt số lần thử 429 |
| GET `/api/auth/me` | Trả user từ phiên server, không có password/hash; chưa login 401 |
| POST `/api/auth/logout` | Cần CSRF; hủy phiên server và cookie, không chỉ xóa browser storage |
| PUT `/api/auth/change-password` | `{oldPassword,newPassword}` + CSRF; lấy userId từ phiên, bỏ qua userId client; kiểm tra mật khẩu cũ, hash mới, tăng version, buộc login lại |

Login cũng cần CSRF: client lấy `/csrf` trước, gửi token qua header ở mọi POST/PUT/DELETE. Token trước login không dùng lại sau login; frontend xóa cache token sau login/logout/đổi mật khẩu. Không tự retry thao tác ghi khi nhận lỗi token hoặc mất phản hồi.

Admin được nghiệp vụ và CRUD danh mục/thống kê. Staff được đọc phòng/khách/dịch vụ, thêm/sửa khách, đặt/hủy/nhận/trả phòng, thêm/xóa dịch vụ và xem hóa đơn; không sửa/xóa phòng/dịch vụ, không xóa khách, không xem thống kê. API chưa có quy tắc được từ chối mặc định. Đăng nhập/assets login là public; trang nghiệp vụ cần phiên, trang thống kê cần admin.

401/403/429/503 của phần xác thực trả JSON `{success:false,code,message}`. Các lỗi nghiệp vụ booking cũ chưa được đổi đồng loạt; FIX-03 sẽ chuẩn hóa. Frontend đã chờ `/me` trước tải dữ liệu, gửi credentials/CSRF, có đổi mật khẩu và logout server; hoàn thiện ẩn/khóa mọi nút theo quyền còn ở FE-05.


<a id="migration-h10"></a>

##### Kiểm tra SEC-01

```powershell
# Từ backend
.\scripts\test-sec01.ps1
node .\src\test\js\security-client.test.cjs
```

Test Java tạo database UUID riêng, thử SQL 004/công cụ hash rồi khởi động Spring Boot/Tomcat ở port ngẫu nhiên để gọi HTTP thật với cookie jar. Không chạy migration hoặc nghiệp vụ trên database hiện tại. Test frontend dùng fetch/DOM mô phỏng; không thay thế kiểm tra trình duyệt. Báo cáo ở `DOCS.md#report-sec01`.


<a id="migration-h11"></a>

#### CORE-03 — Gọi và xóa dịch vụ

CORE-03 không cần migration riêng; dùng các cột snapshot đã có từ 002. Backend hiện tại còn có SEC-01: database cũ phải hoàn tất 001 → 002 → 003 → 004 và công cụ hash theo phần SEC-01 ở đầu tài liệu trước khi chạy.

- `POST /api/bookings/{bookingId}/services`: JSON `{serviceId,quantity}`. Booking phải CheckedIn, chưa có Payments; service phải tồn tại và đang Hoạt động. quantity nguyên 1–10000; thiếu/null/sai kiểu/phần lẻ/ngoài giới hạn bị từ chối, không cắt số. Tên/giá client gửi bị bỏ qua; giá và tên được lấy ở server và lưu theo CORE-01.
- Mỗi request thêm hợp lệ tạo một dòng riêng. Thành tiền dòng, tổng tiền dịch vụ cộng dồn và tổng hóa đơn có cả tiền phòng phải nằm trong giới hạn lưu trữ. Nếu vượt giới hạn, không ghi dòng mới.
- Kết quả POST giữ `{success,message,bookingServiceId,serviceName,quantity,unitPrice,totalPrice}`.
- `DELETE /api/bookings/services/{bookingServiceId}`: xóa toàn bộ đúng một dòng gọi nhầm; trả `{success,message,bookingServiceId,bookingId}`. Dòng phải tồn tại và booking sở hữu dòng đang CheckedIn, chưa có hóa đơn. Xóa lặp hoặc ID sai trả lỗi, không tác động dòng khác.
- Có thể xóa dòng gọi nhầm khi danh mục dịch vụ đã chuyển Ngừng; chỉ việc gọi thêm mới bị chặn. Khách quá ngày checkout dự kiến nhưng vẫn CheckedIn được gọi/xóa dịch vụ theo trạng thái thực tế; chưa tự tính phụ thu/gia hạn.
- Thêm/xóa/preview/checkout dùng cùng khóa Rooms → Bookings; khi xóa còn khóa và kiểm tra lại dòng Booking_Services trước DELETE. Nếu checkout thắng trước, mọi thay đổi dịch vụ bị từ chối. Nếu thêm/xóa thắng trước, checkout tính đúng dữ liệu đã thay đổi. Không thêm tiền chưa thu sau khi hóa đơn được chốt.
- Sau POST/DELETE, gọi lại `GET /api/checkout/preview/{bookingId}` để lấy bảng kê và tổng mới; preview có ID dòng, tên, số lượng, đơn giá và thành tiền đã lưu. Hóa đơn chính thức vẫn lấy bản lưu từ GET payments.
- HTTP lỗi nghiệp vụ tiếp tục là 200 + `{success:false,message}` cho đến FIX-03; JSON không hợp lệ về cú pháp do Spring xử lý. Giao diện thêm/xóa dịch vụ thuộc FE-02; phân quyền thuộc SEC-01.

Kiểm tra SQL Server riêng:

```powershell
# Chạy từ thư mục backend
.\scripts\test-core03.ps1
```

Chương trình dùng database UUID riêng, kiểm tra cả đồng thời với checkout, rollback và giới hạn tổng tiền. Không chạy nghiệp vụ trên database đang dùng.


<a id="migration-h12"></a>

#### CORE-02 — Lịch phòng và trạng thái theo ngày

Database đã có dữ liệu: giữ quy trình sao lưu/dừng ghi, chạy 001 → 002 → `003_booking_schedule_index.sql`, rồi hoàn thành SEC-01 (004 + công cụ hash) ở đầu tài liệu trước khi chạy backend hiện tại. Script 003 thêm index `(room_id,status,checkin_date,checkout_date)`, có thể chạy lại và chỉ liệt kê các cặp booking đang hiệu lực bị trùng lịch để người phụ trách xử lý. Không tự hủy/sửa booking cũ. Không có migration nào được tự áp dụng trên database đang dùng trong phiên làm việc này.

Index 003 hỗ trợ truy vấn, còn việc chống trùng được thực hiện bằng transaction và khóa phòng xuyên suốt kiểm tra/INSERT. Database mới đã có index trong schema.sql.


<a id="migration-h13"></a>

##### API và quy tắc CORE-02

- `GET /api/bookings/available-rooms?checkin=YYYY-MM-DD&checkout=YYYY-MM-DD`: trả mảng phòng có cùng trường như GET rooms. Sai ngày trả HTTP 400 kèm `{success:false,message}`; ngày phải tồn tại thực tế, đúng định dạng, checkout sau checkin, checkin không trong quá khứ. Query thiếu tham số do Spring xử lý 400. Database lỗi không giả thành danh sách phòng rỗng.
- `POST /api/bookings`: dùng cùng điều kiện tìm phòng, kiểm tra khách/phòng tồn tại, giữ giá CORE-01. Room lock được giữ đến commit/rollback nên hai request trùng khoảng không thể cùng thành công. Giá client gửi không được dùng.
- Khoảng ngày là `[checkin,checkout)`. Chỉ Confirmed/CheckedIn chặn lịch nếu `existing.checkin < input.checkout AND existing.checkout > input.checkin`. Cancelled/CheckedOut không chặn. Đặt nối tiếp đúng ngày checkout được phép.
- Một khách còn CheckedIn chặn thêm việc đặt bắt đầu hôm nay, kể cả đã quá ngày checkout trên đơn. Khoảng tương lai vẫn xét lịch dự kiến. Lúc nhận phòng luôn kiểm tra lại không có khách khác đang ở; việc tìm được phòng tương lai không bảo đảm khách cũ đã rời phòng vào ngày đến.
- `PUT /api/bookings/{id}/cancel`: chỉ Confirmed, không có hóa đơn; trả `{success,message,roomStatus}`. Có thể hủy reservation tương lai dù cùng phòng đang có khách ở hoặc đang bảo trì. Trạng thái thực tế của khách khác/bảo trì được giữ; không luôn gán Trống.
- Check-in chỉ khi `checkinDate <= ngày hiện tại < checkoutDate`, booking Confirmed, không bảo trì/khách khác/hóa đơn. Không nhận sớm hoặc nhận sau khi kỳ ở kết thúc.
- Ngày nghiệp vụ dùng `Asia/Ho_Chi_Minh`, không phụ thuộc múi giờ hệ điều hành/SQL Server. Checkout vẫn tính số đêm đã đặt theo CORE-01; chưa hỗ trợ gia hạn/trả sớm/phụ thu.
- GET rooms, GET rooms/{id}, thống kê trạng thái phòng dùng chung truy vấn: Bảo trì ưu tiên; có bất kỳ CheckedIn thì Đang sử dụng; có Confirmed bao phủ hôm nay thì Đã đặt; còn lại Trống. Đọc dữ liệu tự phản ánh việc qua ngày, không cần cron hoặc một lần ghi để đổi trạng thái. Rooms.status được cập nhật lại khi đặt/hủy/nhận/trả phòng nhưng không được dùng riêng để suy ra lịch trống.
- API PUT sửa phòng cũ cũng khóa phòng trước khi đọc booking. Không bật bảo trì khi còn bất kỳ Confirmed/CheckedIn, kể cả booking tương lai; không tự gán Đã đặt/Đang sử dụng. Để tương thích form hiện tại, gửi lại trạng thái tự tính đang đúng được chấp nhận; gửi Trống không làm khách đang ở biến mất. Bỏ status khi sửa sẽ giữ trạng thái thực tế. Bật/tắt bảo trì qua API status riêng và bộ lọc/UI tiếp tục ở FE-03.
- Booking Confirmed quá hạn chưa tự chuyển Cancelled. Nếu không còn hiệu lực cần người phụ trách hủy theo nghiệp vụ; không tự sửa lịch cũ trong migration.

Chỉ mới bổ sung backend. Giao diện đặt phòng hiện có vẫn cần FE-01 để gọi available-rooms khi đổi ngày và thêm nút hủy. CORE-02 chưa thay JS/HTML của các trang đó. Hợp đồng lỗi các API ghi cũ vẫn giữ HTTP 200 + success:false; chuẩn hóa tổng thể ở FIX-03.

Chạy kiểm tra riêng từ thư mục backend:

```powershell
.\scripts\test-core02.ps1
```

Test tạo database UUID riêng, kiểm tra đồng thời và dùng Clock cố định để thử qua ngày/múi giờ Việt Nam. Tài khoản cần quyền tạo/xóa database thử như các kiểm tra trước.


<a id="migration-h14"></a>

#### Thứ tự nâng cấp lên CORE-01

1. Sao lưu database bằng SSMS và kiểm tra có thể phục hồi. Dừng backend/ghi dữ liệu trong thời gian nâng cấp; giữ bản ứng dụng cũ và backup để khôi phục nếu cần.
2. Chọn đúng database đang dùng trong SSMS. Chạy `001_unique_payment_per_booking.sql`. Nếu báo hóa đơn trùng, dừng và đối soát; không tự xóa hóa đơn.
3. Chạy `002_transaction_price_snapshots.sql`. Script chạy trong transaction; nếu thất bại, toàn bộ DDL/backfill của script được rollback. Sửa dữ liệu vi phạm sau đối soát rồi chạy lại.
4. Xem kết quả liệt kê hóa đơn ước tính và `reconciliation_warning`. Đối chiếu chứng từ gốc cho các hóa đơn cảnh báo. Không coi thông tin dựng lại là bằng chứng lịch sử chính xác.
5. Với backend hiện tại, hoàn thành tiếp 003 và SEC-01 (004 + công cụ hash) theo phần đầu trước khi chạy. Thử preview/checkout trong môi trường thử và GET `/api/payments/{id}` sau đăng nhập. Chỉ bật ghi dữ liệu lại khi ứng dụng và schema tương thích.

**Chưa có script nào được tự chạy trên database đang dùng trong phiên làm việc này.** Các script đã kiểm chứng trên database SQL Server thử riêng. Không chạy lại schema/seed để nâng cấp database có dữ liệu.


<a id="migration-h15"></a>

##### Cách backfill dữ liệu cũ

- Bookings thiếu giá: sao chép Rooms.price hiện tại, đánh dấu `snapshot_estimated=1`. Không thể biết chắc giá lúc đặt từ schema cũ.
- Booking_Services thiếu snapshot: sao chép tên dịch vụ hiện tại, suy ra đơn giá từ `total_price/quantity` với tối đa 6 chữ số thập phân; giữ nguyên thành tiền đã ghi và đánh dấu ước tính. Tên lịch sử không thể khôi phục nếu trước đây đã đổi.
- Payments chưa có bản lưu: dựng JSON từ thông tin đang có, giữ nguyên `room_total`, `service_total`, `grand_total`, `payment_method`, `paid_at` đã ghi trong Payments. Mọi bản dựng lại đều có `snapshotEstimated=true`.
- Nếu tiền phòng không khớp giá ước tính × số đêm, tiền dịch vụ không khớp tổng dòng, tổng hóa đơn không khớp hai phần hoặc đơn giá suy ra × số lượng không khớp tuyệt đối, lưu `reconciliationWarning`. Không tạo dòng dịch vụ giả để bù tiền.
- Chạy lại script không thay giá/tên đã lưu và không ghi đè invoice_snapshot có sẵn. Sai lệch lịch sử cần đối soát thủ công với chứng từ, không tự tính lại hóa đơn đã thu theo bảng giá hiện tại.


<a id="migration-h16"></a>

##### Hợp đồng CORE-01

- `POST /api/bookings`: lấy giá trực tiếp từ Rooms trong cùng transaction; bỏ qua giá do client gửi. Bookings lưu `price_per_night`.
- `POST /api/bookings/{id}/services`: `{serviceId,quantity}`, quantity nguyên 1–10000; chỉ booking CheckedIn, dịch vụ đang Hoạt động. Lưu tên và đơn giá vào dòng Booking_Services. Trả `{success,message,bookingServiceId,serviceName,quantity,unitPrice,totalPrice}`. API này được làm trong CORE-01 để có đường ghi snapshot thực tế; CORE-03 đã bổ sung xóa dòng và kiểm tra đồng thời/tổng cộng dồn. Giao diện ở FE-02.
- Preview giữ các trường cũ, bổ sung `bookingServiceId`, `snapshotEstimated`, `reconciliationWarning`; tiền lấy từ giá đã lưu, không lấy bảng giá hiện tại.
- Checkout giữ `{success,message,invoice}` và thêm `paymentId` ở cấp ngoài. invoice gồm ID, tên/giấy tờ/điện thoại/địa chỉ khách, mã/tên phòng, ngày ở, số đêm, đơn giá, tiền phòng/dịch vụ/tổng, phương thức/trạng thái/thời điểm thanh toán và danh sách services. Toàn bộ được lưu JSON trong Payments ở cùng transaction với thay đổi booking/phòng.
- `GET /api/payments/{id}` trả `{success:true,invoice:{...}}` từ JSON đã lưu, không JOIN/tính lại theo dữ liệu hiện tại. Không có bản lưu thì trả lỗi rõ để chạy migration, không giả tạo hóa đơn từ bảng giá mới.
- VND mới dùng BigDecimal, không nhận phần lẻ đồng, tổng mỗi trường nằm trong 0–999.999.999.999 theo cột tiền hiện có; vượt giới hạn sẽ từ chối và rollback. Phần lẻ đơn giá dựng lại cho dữ liệu cũ chỉ phục vụ đối soát và có cờ ước tính.
- HTTP lỗi nghiệp vụ giữ tương thích FIX-02; phân quyền và chuẩn hóa status thực hiện ở SEC-01/FIX-03. Chức năng nút xem/in lại trên giao diện thuộc FE-02.


<a id="migration-h17"></a>

##### Cài mới và kiểm tra CORE-01

Database mới chạy `schema.sql` rồi `seed_data.sql` như trước. Seed đã có giá chụp lại và một hóa đơn có dịch vụ đầy đủ; chỉ dành cho database mới trống. Các cột snapshot trên Bookings/Booking_Services là bắt buộc: script INSERT tự viết phải truyền giá/tên đã chốt, không bỏ cột rồi dựa vào bảng giá hiện tại.

Từ thư mục backend chạy:

```powershell
.\scripts\test-core01.ps1
.\scripts\test-fix02.ps1
```

CORE-01 kiểm tra migration trên schema cũ, rollback DDL khi dữ liệu sai, dữ liệu ước tính/không khớp, giá được giữ sau đổi bảng giá, hóa đơn được giữ sau đổi thông tin, lỗi ghi JSON và giới hạn tiền. FIX-02 kiểm tra lại đồng thời/rollback trên schema mới. Cả hai dùng database UUID riêng, không sửa dữ liệu nghiệp vụ trong database đang dùng. Yêu cầu quyền tạo/xóa database thử và dependency Maven đã có trong cache, gồm Jackson 2.15.3 do pom hiện tại sử dụng.


<a id="migration-h18"></a>

#### Migration FIX-02

Database đã có dữ liệu: sao lưu trước, chọn đúng database Homestay trong SSMS rồi chạy `001_unique_payment_per_booking.sql`. Không chạy lại `schema.sql` hoặc `seed_data.sql`.

Script khóa bảng Payments trong transaction, kiểm tra hóa đơn trùng và thêm UNIQUE `booking_id`. Có thể chạy lại nếu ràng buộc đã tồn tại. Nếu có trùng, script liệt kê booking vi phạm và dừng; không sửa/xóa dữ liệu. Đối soát hóa đơn với người phụ trách trước khi xử lý rồi chạy lại script. Chọn thời điểm không có nhân viên thanh toán vì script có thể chờ/giữ khóa bảng.

Database cài mới: `schema.sql` đã có cùng ràng buộc, không bắt buộc chạy migration này.

FIX-02 giữ nguyên HTTP 200 với lỗi nghiệp vụ `{success:false,message}` để tương thích frontend hiện tại. Chuẩn hóa HTTP status thuộc FIX-03. Các thao tác create/check-in/checkout khóa phòng trước rồi booking và rollback rõ ràng khi thất bại. Preview chỉ chấp nhận CheckedIn; số tiền vẫn tính theo cơ chế cũ đến CORE-01. Quy tắc ngày và tính lại trạng thái phòng theo lịch thuộc CORE-02.


<a id="migration-h19"></a>

#### Kiểm tra SQL Server tự động

`backend/src/test/java/com/homestay/controller/BookingControllerSqlServerIT.java` là chương trình kiểm tra tích hợp chạy trực tiếp, không cần JUnit. Từ thư mục backend chạy:

```powershell
.\scripts\test-fix02.ps1
```

Script biên dịch offline bằng Maven Wrapper rồi chạy kiểm tra với JDBC driver trong Maven cache. Nếu cache ở nơi khác, truyền `-MavenRepository 'đường-dẫn-cache'`. Lần đầu cần tải đủ dependency trước khi chạy offline. Đây là kiểm tra gọi trực tiếp controller/JDBC, chưa phải kiểm tra HTTP hoặc trình duyệt.

Chương trình dùng cấu hình DBConnection hiện tại để tạo database thử tên `HomestayFix02Test_` kèm UUID, chạy các case trong tiến trình con có DB_NAME riêng rồi xóa đúng database thử trong finally. Cần quyền CREATE/DROP DATABASE trên SQL Server; không chạy các thao tác nghiệp vụ hay migration trên database hiện tại. Nếu không có quyền/môi trường kết nối, kiểm tra dừng với lỗi và phải ghi là chưa kiểm chứng. Nếu tiến trình bị tắt đột ngột, database thử có thể còn lại; chỉ xóa sau khi xác nhận tên UUID được chương trình tạo.

---

<a id="guide-fe01"></a>

## Hướng dẫn đặt, hủy và nhận phòng — FE-01

<a id="guide-fe01-h1"></a>

### Đặt phòng, hủy và nhận phòng — FE-01

Mở dự án qua backend tại `http://localhost:8080/pages/login.html`, đăng nhập admin hoặc staff. Database phải hoàn thành các bước nâng cấp 001–005 và chuyển mật khẩu SEC-01 đã hướng dẫn trước đó; FE-01 không thêm SQL mới.


<a id="guide-fe01-h2"></a>

#### Đặt phòng

1. Vào Booking → Tạo booking mới. Form mặc định hôm nay/mai theo giờ Việt Nam.
2. Chọn ngày nhận/trả. Ngày nhận không trong quá khứ, ngày trả phải sau ngày nhận. Mỗi khoảng ngày hợp lệ gọi API tìm phòng trống theo ngày, không lọc chỉ dựa vào trạng thái phòng hôm nay.
3. Chờ khách hàng/phòng tải xong, chọn khách và phòng. Nếu không có phòng, đổi ngày. Nếu lỗi tải, xem thông báo và bấm Tải lại dữ liệu; lỗi không được coi là không có phòng.
4. Xem số đêm, giá/đêm và tiền phòng tạm tính. Chưa gồm dịch vụ, giá được chốt ở server khi tạo booking. Ghi chú tối đa 500 ký tự.
5. Xác nhận một lần. Trong khi gửi, khóa các trường/nút đóng; thành công sẽ đóng form và tải lại bảng.

Nếu phòng vừa có người khác đặt hoặc dữ liệu đã đổi, form giữ thông báo server, tải lại phòng/khách và bảng booking, xóa lựa chọn không còn hợp lệ. Không tự gửi lại yêu cầu tạo đơn. Mất phản hồi cũng chỉ kiểm tra lại bằng GET để tránh tạo lại booking đã thành công.

Đổi ngày nhanh chỉ hiển thị kết quả của khoảng ngày cuối cùng; phản hồi hoặc lỗi cũ không ghi đè dữ liệu mới. Đóng form làm các phản hồi cũ hết hiệu lực.


<a id="guide-fe01-h3"></a>

#### Hủy booking

Chỉ dòng Confirmed có nút Hủy đặt phòng. Xem nội dung xác nhận rồi đồng ý; nút chuyển trạng thái đang hủy và không gửi lần hai trong lúc chờ. Sau kết quả sẽ tải lại bảng, kể cả khi bị từ chối vì người khác vừa check-in. Phòng/booking khác vẫn được backend xử lý theo lịch CORE-02.

Booking đã CheckedIn/CheckedOut/Cancelled không có nút hủy. Confirmed quá ngày chưa nhận vẫn được xử lý hủy ở trang này, không tự hủy khi mở danh sách.


<a id="guide-fe01-h4"></a>

#### Nhận phòng

1. Vào Check-in. Danh sách có ngày nhận/trả, điện thoại, ghi chú và điều kiện nhận. Chưa đến ngày hoặc đã đến ngày trả thì nút bị khóa và có lý do.
2. Booking hợp lệ trong ngày mở modal xác nhận, hiển thị tên khách/phòng/ngày/ghi chú dưới dạng văn bản an toàn.
3. Xác nhận nhận phòng. Không gửi lặp hoặc đóng modal khi đang xử lý. Server kiểm tra lại trạng thái, khách cũ chưa trả và bảo trì; đủ điều kiện mới nhận thành công.
4. Thành công đóng modal, tải lại danh sách và hiện liên kết Đến trang khách đang ở. Chức năng gọi/xóa dịch vụ trong giao diện checkout sẽ hoàn thiện ở FE-02; FE-01 không gọi dịch vụ trước khi check-in.

Nếu bị từ chối hoặc chưa xác nhận được kết quả, xem thông báo server, đóng modal và chọn lại từ danh sách được tải mới. Không tự gửi lại. Nút Tải lại danh sách giúp cập nhật sau thao tác của nhân viên khác. Client kiểm tra lại ngày ngay trước xác nhận; server vẫn là nơi quyết định cuối cùng.


<a id="guide-fe01-h5"></a>

#### Kiểm tra tự động

Từ thư mục backend:

```powershell
node .\src\test\js\booking-checkin.test.cjs
node .\src\test\js\validation-render.test.cjs
node .\src\test\js\security-client.test.cjs
```

Các test này dùng DOM/API mô phỏng, không chạy lên database đang dùng. Kiểm tra trình duyệt và luồng dữ liệu thật thuộc QA-01; môi trường thử cần khớp backend/migration hiện tại.

---

<a id="guide-fe0203"></a>

## Hướng dẫn dịch vụ, hóa đơn và phòng — FE-02/03

<a id="guide-fe0203-h1"></a>

### Dịch vụ, hóa đơn và bộ lọc phòng — FE-02/FE-03

Chạy backend code mới, mở `http://localhost:8080/pages/login.html`. Database cũ cần đủ migration 001–005 và chuyển mật khẩu SEC-01 theo hướng dẫn trước đó. FE-02/03 không cần SQL migration mới.


<a id="guide-fe0203-h2"></a>

#### Khách đang ở và dịch vụ

1. Vào Check-out, chọn Dịch vụ & Check-out ở booking CheckedIn.
2. Xem Tạm tính BK: ngày ở, số đêm đã đặt, giá phòng đã lưu, bảng kê và tổng.
3. Chọn Thêm dịch vụ, chờ danh mục tải xong, chọn dịch vụ Hoạt động và số lượng nguyên 1–10000. Xem đơn giá/thành tiền rồi thêm. Giá chính thức do server chốt khi thêm.
4. Hủy dòng trong bảng kê nếu gọi nhầm. Mỗi lần gọi là một dòng riêng; không hủy được sau thanh toán. Sau ghi hoặc xung đột, hệ thống tải lại tạm tính.

Khi lỗi tải, nút thu tiền bị khóa; đóng và mở lại để tải mới. Khi mất phản hồi thêm dịch vụ, kiểm tra bảng kê đã tải lại trước khi gọi thêm để tránh ghi trùng.


<a id="guide-fe0203-h3"></a>

#### Thu tiền và in lại

1. Nhân viên tự kiểm tra đã thu đủ tiền rồi chọn phương thức Tiền mặt/Chuyển khoản/Thẻ ngân hàng. Phần mềm chỉ ghi nhận, không tự xác minh tiền về.
2. Xác nhận đã thu tiền & Check-out một lần. Hệ thống khóa các thao tác trong lúc xử lý và lấy Hóa đơn HD từ bản đã lưu.
3. Bấm In hóa đơn. Có thể hủy hộp thoại in hoặc in lại mà không tạo thanh toán mới.
4. Sau đóng/reload trang, tìm trong Hóa đơn đã thu → Xem / In lại. Có thể mở trực tiếp `checkout.html?paymentId=7` với ID hợp lệ và phiên đăng nhập.

Mất phản hồi thanh toán: phần mềm tìm lại hóa đơn bằng GET theo booking. Nếu không đối soát được, nút thu vẫn bị khóa; tải lại danh sách hóa đơn/khách đang ở và kiểm tra kết quả trước khi thao tác tiếp. Không tự gửi lại POST. Nếu hóa đơn đã thu nhưng lỗi tải bản lưu, dùng Xem / In lại sau khi kết nối phục hồi; không thu lần nữa.


<a id="guide-fe0203-h4"></a>

#### Phòng và bảo trì

- Kết hợp tìm mã/tên với loại phòng và trạng thái. Trạng thái theo hôm nay ở Việt Nam; lịch trống theo khoảng ngày vẫn nằm ở trang Booking.
- Staff xem danh sách. Admin có thêm/sửa/xóa và Bảo trì/Kích hoạt. Form danh mục không gán Đã đặt/Đang sử dụng; các trạng thái đó lấy từ booking.
- Bật Bảo trì chỉ khi không còn booking Confirmed/CheckedIn, kể cả tương lai. Xử lý booking trước khi thử lại. Đây là bật/tắt hiện tại, chưa hỗ trợ lịch bảo trì.
- Kích hoạt tính lại trạng thái từ booking. Không dùng sửa trạng thái để giải phóng phòng còn khách; cần checkout đúng nghiệp vụ. Sửa giá danh mục chỉ ảnh hưởng giao dịch mới, không đổi giá booking/hóa đơn đã lưu.


<a id="guide-fe0203-h5"></a>

#### Chạy kiểm tra

Từ thư mục `Homestay`:

```powershell
node backend/src/test/js/checkout-rooms.test.cjs
node backend/src/test/js/validation-render.test.cjs
node backend/src/test/js/booking-checkin.test.cjs
node backend/src/test/js/security-client.test.cjs
.\backend\scripts\test-core02.ps1
.\backend\scripts\test-sec01.ps1
.\backend\scripts\test-core03.ps1
.\backend\scripts\test-core01.ps1
```

Các bộ Java tạo database thử riêng rồi dọn, cần SQL Server chạy và tài khoản có quyền tạo database thử. Không dùng schema/seed để ghi đè database đang dùng. Test DOM mô phỏng không chứng minh bố cục/in; phần kiểm tra trình duyệt và luồng UI tích hợp còn ở QA-01. Kết quả chi tiết: [FE-02](#report-fe02), [FE-03](#report-fe03).

---

<a id="guide-fe0405"></a>

## Hướng dẫn khách hàng và tài khoản — FE-04/05

<a id="guide-fe0405-h1"></a>

### Khách hàng, lịch sử và tài khoản — FE-04/FE-05

Chạy backend code mới và mở `http://localhost:8080/pages/login.html`. Database cũ cần đủ các bước 001–005 và chuyển mật khẩu SEC-01 như hướng dẫn trước. FE-04/05 không thêm migration. Tải lại trang để nạp HTML/JS mới.


<a id="guide-fe0405-h2"></a>

#### Khách hàng và lịch sử

1. Vào Khách hàng, nhập tên, CCCD/hộ chiếu hoặc số điện thoại. Kết quả được tìm trên server; có thể bấm Tìm / Tải lại. Xóa nội dung tìm để xem tất cả.
2. Chọn Lịch sử lưu trú ở dòng khách. Modal hiển thị booking, phòng, ngày nhận/trả, số đêm, trạng thái và tiền đã thu/phương thức.
3. Tổng chi tiêu chỉ gồm hóa đơn đã thanh toán. Booking chưa thu hoặc đã hủy nhưng chưa có khoản thu hiện Chưa thanh toán, không cộng tiền dự kiến vào tổng. Khách mới chưa có booking hiện lịch sử rỗng và tổng 0.
4. Chọn Xem HD để mở đúng bản hóa đơn đã lưu ở Check-out; có thể in lại theo FE-02. Hóa đơn thu 0 đồng vẫn có link riêng.
5. Nếu lỗi, tổng hiện `--`, không coi là 0; bấm Tải lại. Thao tác đóng/chuyển khách khiến phản hồi cũ hết hiệu lực.

Thêm/sửa khách vẫn dùng CCCD 12 chữ số hoặc hộ chiếu 6–20 chữ/số, điện thoại Việt Nam 10 chữ số bắt đầu 0. Dữ liệu cũ có cảnh báo được giữ khi chưa sửa trường đó; khi sửa cần nhập giá trị hợp lệ. Staff được thêm/sửa/xem; chỉ admin có nút xóa. Khách đã có booking không thể xóa dù booking đã hủy/kết thúc.


<a id="guide-fe0405-h3"></a>

#### Tài khoản và phiên

- Header trên các trang hiển thị tên/quyền từ server, nút Đổi mật khẩu và Đăng xuất. Admin quản lý danh mục/thống kê; staff chỉ thấy các thao tác được phép.
- Khi mở trang, chờ kiểm tra phiên rồi nội dung mới hiện. Có lỗi kết nối xác thực thì bấm Thử lại; hết phiên thì đăng nhập lại. Không cần và không nên chỉnh dữ liệu sessionStorage để đăng nhập.
- Đổi mật khẩu: nhập mật khẩu hiện tại, mật khẩu mới 12–128 ký tự khác mật khẩu cũ, không chỉ khoảng trắng, rồi nhập lại khớp. Hệ thống giữ nguyên khoảng trắng và kiểm tra mật khẩu cũ tại server. Thành công phải đăng nhập lại; các phiên cũ bị thu hồi.
- Đăng xuất ở header hoặc menu đều hủy phiên server. Nếu mất phản hồi, ứng dụng kiểm tra lại phiên bằng GET. Không xác nhận được thì Thử lại sau khi kết nối phục hồi.
- Dùng nút Back sau logout không cấp lại quyền API. Trang khôi phục phải kiểm tra lại phiên; nếu tài khoản/quyền đã đổi thì tải lại dữ liệu.


<a id="guide-fe0405-h4"></a>

#### API bổ sung

```text
GET /api/customers?search=...
GET /api/customers/{id}/bookings  -> mảng booking
GET /api/customers/{id}/history   -> {success, customer, bookings, totalPaid}
```

`totalPaid` là chuỗi số nguyên VND cho tổng chi tiêu; từng dòng có `paidTotal`, `paymentId`, `paymentMethod`, `paidAt`. `paymentId: null` nghĩa là chưa có khoản đã thanh toán. Admin/staff đều được đọc; API trả 404 cho khách không tồn tại, 400 cho tham số sai và 503 khi không truy cập được dữ liệu.


<a id="guide-fe0405-h5"></a>

#### Kiểm tra

Từ thư mục `Homestay`:

```powershell
node backend/src/test/js/customers-account.test.cjs
node backend/src/test/js/security-client.test.cjs
node backend/src/test/js/validation-render.test.cjs
node backend/src/test/js/booking-checkin.test.cjs
node backend/src/test/js/checkout-rooms.test.cjs
.\backend\scripts\test-fix03.ps1
```

Bộ Java cần SQL Server hoạt động và tài khoản có quyền tạo database thử. Nó tạo database riêng rồi dọn, không ghi thử lên database nghiệp vụ. Các bộ Node mô phỏng DOM/API, chưa thay thế kiểm tra trực quan và luồng UI thật thuộc QA-01. Xem [báo cáo FE-04](#report-fe04) và [FE-05](#report-fe05).

---

<a id="report-fix02"></a>

## Báo cáo FIX-02

<a id="report-fix02-h1"></a>

### Kết quả FIX-02

Ngày: 26/10/2026. Phạm vi: kiểm soát check-in, preview, checkout và hóa đơn duy nhất.


<a id="report-fix02-h2"></a>

#### Thay đổi

- `BookingController.java`: helper transaction rollback trước khi đóng connection; khóa Rooms bằng XLOCK/HOLDLOCK trước khi khóa Bookings bằng UPDLOCK/HOLDLOCK; kiểm tra lại booking sau khi lấy khóa; UPDATE trạng thái có điều kiện.
- Check-in chỉ nhận Confirmed; preview/checkout chỉ nhận CheckedIn. Check-in/checkout chặn phòng bảo trì, phòng có booking khác CheckedIn và booking đã có Payments.
- createBooking cũng dùng helper transaction và khóa phòng để thống nhất với check-in/checkout. Quy tắc đặt theo ngày vẫn thuộc CORE-02.
- Thêm UNIQUE Payments.booking_id cho cài mới và migration cho database cũ. Migration không tự sửa/xóa hóa đơn trùng.
- Giữ nguyên JSON thành công và HTTP 200 cho lỗi nghiệp vụ như trước; lỗi trả `{success:false,message}`. HTTP status sẽ chuẩn hóa ở FIX-03.


<a id="report-fix02-h3"></a>

#### Kiểm chứng đã chạy

1. `mvnw.cmd -o test-compile`: BUILD SUCCESS, gồm mã chính và chương trình kiểm tra tích hợp.
2. `BookingControllerSqlServerIT`: chạy trực tiếp controller/JDBC trên SQL Server thật, trong database UUID riêng do chương trình tạo. Tiến trình con dùng DB_NAME của database thử. Kết quả cuối: **12 nhóm đạt**, exit code 0; database thử đã được xóa trong finally.
3. Kiểm tra cú pháp PowerShell của `backend/scripts/test-fix02.ps1`: đạt.
4. `git diff --check`: không lỗi whitespace; Git có cảnh báo chuyển LF/CRLF cho các file frontend đã sửa từ FIX-01.

| Nhóm kiểm tra | Kết quả |
|---|---|
| Check-in Cancelled/CheckedOut/CheckedIn | Bị từ chối, trạng thái không đổi |
| Preview/checkout Confirmed/Cancelled/CheckedOut | Bị từ chối, không tạo Payments |
| Booking không tồn tại | Bị từ chối |
| Phòng bảo trì | Chặn check-in/checkout |
| Phòng có khách khác đang ở | Chặn nhận thêm khách |
| Booking CheckedIn nhưng đã có hóa đơn | Chặn thanh toán lại |
| Hai checkout cùng lúc, cùng booking | Một thành công; một hóa đơn; tổng doanh thu 600.000 đúng một lần |
| Hai check-in cùng lúc, cùng booking | Một thành công |
| Hai booking cùng nhận một phòng đồng thời | Một thành công; chỉ một booking CheckedIn |
| Trigger gây lỗi khi cập nhật Rooms sau các bước ghi | Rollback trạng thái booking/phòng và Payments, không còn hóa đơn dở |
| Migration chạy lại và dữ liệu trùng | Chạy lại được; có trùng thì dừng, giữ nguyên cả hai hóa đơn |
| INSERT hóa đơn trùng trực tiếp | UNIQUE chặn, còn đúng một hóa đơn |

Lỗi `Injected FIX-02 failure` trong log là lỗi được test cố ý tạo để kiểm tra rollback. Lượt chạy đầu phát hiện helper test chưa đọc hết các kết quả JDBC sau SELECT chẩn đoán nên chưa nhận được THROW của migration; đã sửa helper và chạy lại toàn bộ, đạt 12 nhóm như trên. Không thay kỳ vọng test để bỏ qua lỗi.


<a id="report-fix02-h4"></a>

#### Cách chạy lại

Từ thư mục `Homestay/backend`:

```powershell
.\scripts\test-fix02.ps1
```

Yêu cầu SQL Server đang chạy, cấu hình DBConnection hợp lệ, dependency có trong Maven cache và tài khoản có quyền tạo/xóa database thử. Test không chạy nghiệp vụ trên database đang cấu hình. Nếu không có quyền tạo database thử, phải chạy ở SQL Server thử có quyền phù hợp; không chuyển test sang dữ liệu thật.


<a id="report-fix02-h5"></a>

#### Việc còn lại khi áp dụng vào database hiện có

Migration `database/migrations/001_unique_payment_per_booking.sql` **chưa được chạy trên database đang dùng**. Sao lưu, chọn đúng database trong SSMS rồi chạy theo `DOCS.md#migration`. Không chạy lại schema/seed. Nếu có hóa đơn trùng, đối soát với người phụ trách trước; script không tự xóa.

Chưa kiểm tra qua HTTP hoặc trình duyệt, chưa restart ứng dụng đang chạy. Luồng trên controller/JDBC đã kiểm chứng; giữ tương thích frontend FIX-01. Lưu giá giao dịch, quy tắc ngày và tính lại trạng thái phòng khi còn booking khác lần lượt thuộc CORE-01/CORE-02.

---

<a id="report-core01"></a>

## Báo cáo CORE-01

<a id="report-core01-h1"></a>

### Kết quả CORE-01 — Lưu giá giao dịch và bản hóa đơn

Ngày: 26/10/2026. Đã hoàn thành code, schema, migration, seed và kiểm tra tích hợp trên SQL Server thử riêng.


<a id="report-core01-h2"></a>

#### Hành vi sau thay đổi

- Tạo booking lấy giá trực tiếp từ Rooms và lưu `Bookings.price_per_night` trong transaction, không nhận giá do client tự gửi.
- POST `/api/bookings/{id}/services` lưu tên và đơn giá dịch vụ ngay khi gọi vào Booking_Services; chỉ nhận CheckedIn, dịch vụ hoạt động và số lượng nguyên 1–10000. Đã có đường ghi dữ liệu thật để bảo đảm snapshot được lưu; phần DELETE và UI thực hiện tiếp ở CORE-03/FE-02.
- Preview và checkout lấy giá đã lưu. Dùng BigDecimal, từ chối phần lẻ đồng cho tiền mới và tổng vượt giới hạn DECIMAL(12,0), không tự làm tròn hoặc cắt số.
- Checkout lưu toàn bộ JSON hóa đơn vào Payments cùng transaction với INSERT hóa đơn, cập nhật booking và phòng. JSON có thông tin khách/phòng, ngày, số đêm, đơn giá, dòng dịch vụ, các khoản tiền, phương thức và thời điểm thanh toán. Trả paymentId ở cấp ngoài và trong invoice.
- GET `/api/payments/{id}` đọc JSON đã lưu, không JOIN bảng khách/phòng/dịch vụ để tính lại. Đổi bảng giá hoặc tên sau thanh toán không làm thay đổi hóa đơn đã lưu. Nút xem/in lại trên giao diện thuộc FE-02.
- Dữ liệu lịch sử chưa lưu thông tin được migration đánh dấu ước tính. Tổng đã thu ưu tiên Payments. Các chênh lệch giữa chi tiết dựng lại và tổng được ghi thành cảnh báo, không sửa tổng cũ hay tạo dòng giả.


<a id="report-core01-h3"></a>

#### File chính

- `backend/src/main/java/com/homestay/controller/BookingController.java`
- `backend/src/main/java/com/homestay/controller/BookingInvoiceSnapshot.java`
- `database/schema.sql`, `database/seed_data.sql`
- `database/migrations/002_transaction_price_snapshots.sql`
- `DOCS.md#migration`: hợp đồng API, thứ tự nâng cấp và giới hạn dữ liệu cũ.
- `backend/src/test/java/com/homestay/controller/BookingPriceSnapshotSqlServerIT.java`
- `backend/scripts/test-core01.ps1`; cập nhật runner/fixture FIX-02 cho schema và dependency Jackson mới sử dụng.


<a id="report-core01-h4"></a>

#### Kiểm chứng

`mvnw.cmd -o test-compile`: BUILD SUCCESS, gồm mã backend và hai chương trình kiểm tra tích hợp.

Chạy `scripts/test-core01.ps1`: **9 nhóm đạt**, exit code 0, dùng database UUID riêng trên SQL Server thật. Database thử đã xóa sau khi chạy.

| Nhóm | Kết quả |
|---|---|
| Migration gặp dữ liệu cũ sai | Dừng, rollback cả thêm cột, giữ các hóa đơn cũ |
| Dựng lại hóa đơn cũ | Giữ tổng Payments, đánh dấu ước tính, báo lệch giá phòng và đơn giá dịch vụ suy ra |
| Chạy migration lần hai sau khi đổi danh mục | Không ghi đè giá hoặc JSON hóa đơn đã lưu |
| Đặt phòng rồi đổi giá | Giữ giá đặt ban đầu; bỏ qua giá client gửi |
| Gọi dịch vụ rồi đổi tên/giá | Giữ tên và giá lúc gọi; chặn quantity thập phân |
| Gây lỗi ở UPDATE lưu JSON Payments | Rollback payment và trạng thái booking, không có hóa đơn dở |
| Thanh toán và xem lại | paymentId đúng, tổng 700.000 khớp; đổi khách/phòng/dịch vụ và xóa dòng Booking_Services thử vẫn giữ bản hóa đơn |
| Tổng vượt giới hạn lưu trữ | Từ chối, không tạo Payments |
| Seed trên schema đã nâng cấp | Tạo đủ giá và bản hóa đơn mẫu 1.100.000 với dòng dịch vụ; migration chạy lại vẫn giữ đúng |

Chạy lại `scripts/test-fix02.ps1` trên schema mới: **12 nhóm đạt**, exit code 0; giữ đúng kiểm soát trạng thái, đồng thời, rollback, UNIQUE và migration 001. Database thử riêng đã xóa. Schema mới cũng đã được tạo thành công trong lần chạy này.

Log `Injected snapshot failure`, `Injected FIX-02 failure` và `Amount outside supported VND range` là các lỗi test cố ý gây ra. Không có kiểm tra nghiệp vụ nào chạy trên dữ liệu của database đang dùng.


<a id="report-core01-h5"></a>

#### Áp dụng và giới hạn

Database đang dùng **chưa chạy migration 001/002**, ứng dụng đang chạy chưa được restart. Cần sao lưu, dừng backend, chạy 001 rồi 002 theo README migration và chạy bản backend mới. Không dùng bản backend mới với schema cũ hoặc chạy lại schema/seed để nâng cấp.

Chưa kiểm tra HTTP/trình duyệt trong đợt này. Giữ hợp đồng preview/check-out tương thích FIX-01, chỉ thêm trường dữ liệu và API. Xác thực/phân quyền API thuộc SEC-01; ngày đặt và trạng thái phòng theo lịch thuộc CORE-02.

Giá/tên/thông tin lịch sử chưa từng được lưu không thể khôi phục chính xác. Snapshot dựng lại chỉ phản ánh dữ liệu còn có tại thời điểm nâng cấp và luôn có cờ ước tính. Tiền đã thu không được thay bằng phép tính theo bảng giá mới. Invoice JSON được giữ độc lập với danh mục, không thay thế chính sách phân quyền hoặc đối soát chứng từ.

---

<a id="report-core02"></a>

## Báo cáo CORE-02

<a id="report-core02-h1"></a>

### Kết quả CORE-02 — Đặt phòng theo ngày và hủy booking

Ngày: 26/10/2026. Đã hoàn thành backend, index/migration và kiểm tra tích hợp trên database SQL Server thử riêng.


<a id="report-core02-h2"></a>

#### Hành vi sau thay đổi

- GET `/api/bookings/available-rooms?checkin=YYYY-MM-DD&checkout=YYYY-MM-DD` tìm phòng theo khoảng ngày. POST tạo booking dùng cùng điều kiện, kiểm tra khách/phòng và lưu giá CORE-01.
- Khoảng lưu trú `[checkin,checkout)` cho phép đặt nối tiếp đúng ngày checkout; chỉ Confirmed/CheckedIn có khoảng giao nhau chặn lịch. Phòng bảo trì không được đặt. Khách còn CheckedIn chặn thêm việc đặt bắt đầu hôm nay, kể cả ngày checkout trên đơn đã qua.
- Khóa Rooms trước khi kiểm tra/ghi booking và giữ đến cuối transaction; cùng thứ tự cho đặt/hủy/nhận/trả phòng và sửa/bảo trì/xóa phòng. Hai yêu cầu đặt cùng khoảng chỉ một được ghi; hai khoảng không giao nhau vẫn được đặt.
- PUT `/api/bookings/{id}/cancel` chỉ cho Confirmed chưa có hóa đơn; tính lại trạng thái sau hủy. Hủy reservation không làm mất trạng thái khách khác đang ở hoặc cờ bảo trì.
- Check-in chỉ trong ngày nhận dự kiến đến trước ngày trả dự kiến, không cho khách khác vào phòng còn khách. Ngày nghiệp vụ dùng Asia/Ho_Chi_Minh, không phụ thuộc múi giờ SQL Server/hệ điều hành.
- `RoomSchedule` là helper chung cho trạng thái: Bảo trì → có CheckedIn thì Đang sử dụng → Confirmed bao phủ hôm nay thì Đã đặt → Trống. GET danh sách/chi tiết phòng và thống kê phòng đọc cùng cách tính; tự phản ánh qua ngày dù không có lần ghi.
- Sau checkout, phòng được tính lại thay vì luôn gán Trống. Nếu booking kế tiếp bao phủ hôm nay thì trở thành Đã đặt.
- PUT sửa phòng cũ dùng transaction/khóa phòng, chặn bật bảo trì khi có Confirmed/CheckedIn và chặn gán trạng thái booking tùy ý. Gửi Trống không xóa khách đang ở; bỏ status giữ trạng thái thực tế. Tạo phòng chỉ nhận Trống hoặc Bảo trì. Xóa phòng vẫn chặn phòng có lịch sử và cũng khóa phòng trước khi kiểm tra.


<a id="report-core02-h3"></a>

#### File chính

- `backend/src/main/java/com/homestay/controller/RoomSchedule.java`: ngày, trạng thái, điều kiện trống, khóa và transaction dùng chung.
- `BookingController.java`: available-rooms/cancel, đặt theo ngày, check-in theo ngày, cập nhật trạng thái sau các thao tác.
- `RoomController.java`: GET trạng thái tự tính và mutation dùng cùng khóa.
- `ServiceStatController.java`: thống kê trạng thái phòng dùng cùng truy vấn; các báo cáo doanh thu/tháng vẫn giữ phạm vi cũ để làm ở REPORT-01.
- `database/schema.sql`, `database/migrations/003_booking_schedule_index.sql`: index và truy vấn liệt kê xung đột lịch cũ, không sửa dữ liệu.
- `backend/src/test/java/com/homestay/controller/BookingScheduleSqlServerIT.java`, `backend/scripts/test-core02.ps1`.
- Cập nhật ngày trong fixture FIX-02/CORE-01 để các kiểm tra hồi quy không phụ thuộc ngày 26/10/2026 khi chạy lại về sau.


<a id="report-core02-h4"></a>

#### Kiểm chứng đã chạy

`mvnw.cmd -o test-compile`: BUILD SUCCESS, gồm backend và 3 chương trình kiểm tra SQL Server.

`scripts/test-core02.ps1`: **22 nhóm đạt**, exit code 0. Kiểm tra trực tiếp controller/JDBC trên SQL Server thật trong database UUID riêng; dùng Clock cố định để kiểm tra múi giờ và qua ngày.

| Nhóm | Kết quả |
|---|---|
| Ngày sai định dạng/không tồn tại/quá khứ/bằng/ngược và ID thập phân | Bị từ chối |
| Khách/phòng không tồn tại | Bị từ chối, không sinh booking |
| Booking tháng sau và booking hôm nay cùng phòng | Được phép nếu không giao nhau; tháng sau không làm phòng bận hôm nay |
| Các hình dạng trùng khoảng và hai khoảng nối tiếp | Trùng bị chặn; nối tiếp được phép; API tìm và tạo thống nhất |
| Cancelled/CheckedOut | Không chặn lịch |
| Hai request đặt cùng khoảng đồng thời | Một thành công, một booking |
| Hai request đặt nối tiếp đồng thời | Cả hai thành công |
| Bảo trì và API sửa phòng cũ | Không đặt được phòng bảo trì, không bật bảo trì khi có booking hiệu lực |
| Đặt phòng đồng thời bật bảo trì | Chỉ một hành động thành công, dữ liệu không mâu thuẫn |
| Hủy booking | Chỉ Confirmed; hủy giải phóng khoảng ngày; hủy lặp/sai trạng thái/ID sai bị chặn |
| Hủy tương lai và gửi Trống khi phòng có khách khác | Giữ Đang sử dụng |
| Hủy khi đã có cờ bảo trì | Giữ Bảo trì |
| Lỗi cập nhật phòng trong transaction hủy | Rollback booking về Confirmed |
| Check-in sớm/đúng ngày/ngày checkout | Chặn sớm và ngày checkout; cho đúng khoảng |
| Khách quá hạn còn CheckedIn | Chặn đặt hôm nay và nhận khách khác; ngày tương lai xét lịch dự kiến |
| Checkout khi có booking Confirmed kế tiếp hôm nay | Phòng chuyển Đã đặt |
| Hủy đồng thời check-in cùng booking | Một chuyển trạng thái hợp lệ được commit |
| Hủy đồng thời tạo thay thế | Không sinh hai reservation hiệu lực trùng khoảng |
| Checkout đồng thời check-in booking kế tiếp | Không có hai khách cùng ở; đúng một hóa đơn |
| Danh sách/chi tiết/thống kê khi qua ngày | Tự đổi trạng thái, thống nhất dù Rooms.status còn giá trị cũ |
| Ngày Việt Nam khi đồng hồ UTC đã qua 17:00 | Dùng ngày tiếp theo ở Việt Nam |
| Migration index | Chạy lại được, giữ nguyên booking cũ xung đột để đối soát |

Hồi quy trên code mới: `scripts/test-fix02.ps1` **12 nhóm đạt**; `scripts/test-core01.ps1` **9 nhóm đạt**, đều exit code 0. Tổng cộng 43 nhóm đạt trong lượt kiểm chứng cuối của ba bộ kiểm tra. Các database thử đã được xóa trong finally. Những log `Injected ... failure` là lỗi chủ động để kiểm tra rollback.

Lượt chạy CORE-02 đầu tiên phát hiện fixture SQL Server mới có thể cấp ID 0 sau reseed; đã sửa fixture dùng IDENTITY_INSERT cho đúng ID thử nghiệm và chạy lại toàn bộ 22 nhóm. Không sửa kỳ vọng nghiệp vụ để bỏ qua lỗi.


<a id="report-core02-h5"></a>

#### Áp dụng và phần tiếp theo

Chưa chạy migration 001/002/003 trên database đang dùng, chưa restart ứng dụng hoặc kiểm tra HTTP/trình duyệt. Sao lưu/dừng backend, chạy migration theo thứ tự và kiểm tra danh sách xung đột lịch cũ theo `DOCS.md#migration`. Migration không tự sửa booking cũ.

API tìm phòng sai ngày trả 400 + success:false; lỗi database mới báo lỗi thay vì danh sách rỗng. Các API ghi cũ vẫn giữ HTTP 200 + success:false để tương thích; chuẩn hóa tất cả lỗi và phân quyền thực hiện tiếp ở FIX-03/SEC-01.

CORE-02 chưa bổ sung nút hủy hay tải lại phòng theo ngày trong HTML/JS. FE-01 sẽ nối giao diện với API mới. FE-03 tiếp tục bộ lọc phòng và API status/nút bảo trì riêng; các ràng buộc trên API PUT cũ đã được làm để bảo đảm đồng thời.

Chưa tự động hủy Confirmed quá hạn, chưa hỗ trợ gia hạn/nhận sớm/phụ thu. Khách còn CheckedIn phải được xử lý checkout trước khi nhận khách mới, kể cả reservation mới đã tồn tại. Doanh thu và hóa đơn tiếp tục theo giá đã lưu ở CORE-01.

---

<a id="report-core03"></a>

## Báo cáo CORE-03

<a id="report-core03-h1"></a>

### Kết quả CORE-03 — Gọi và hủy dịch vụ đang lưu trú

Ngày: 26/10/2026. Đã hoàn thành backend và kiểm tra tích hợp trên SQL Server thử riêng.


<a id="report-core03-h2"></a>

#### Thay đổi

- Hoàn thiện POST `/api/bookings/{bookingId}/services` đã có từ CORE-01: xử lý body thiếu/null và ID booking sai; giữ kiểm tra quantity nguyên 1–10000, catalog hoạt động và giá/tên lấy ở server. Bổ sung kiểm tra tổng dịch vụ cộng dồn và tổng hóa đơn có tiền phòng trước khi INSERT, để không thêm các dòng riêng lẻ hợp lệ nhưng khiến checkout vượt giới hạn cột tiền.
- Thêm DELETE `/api/bookings/services/{bookingServiceId}` xóa toàn bộ một dòng gọi nhầm. Tìm booking sở hữu dòng, khóa Rooms → Bookings, kiểm tra CheckedIn/chưa có hóa đơn, khóa và kiểm tra lại dòng trước DELETE có điều kiện. Dòng mất/thay đổi/ID sai hoặc booking kết thúc đều bị từ chối.
- Xóa lặp không thành công lần hai; không làm thay đổi dòng khác. Danh mục đã Ngừng vẫn cho xóa dòng gọi nhầm đã có, nhưng không cho gọi thêm.
- Thêm/xóa cùng khóa với preview/checkout. Khi checkout thắng trước, thay đổi dịch vụ bị chặn; khi thay đổi dịch vụ thắng trước, khoản tiền được tính theo dữ liệu mới lúc checkout. Bản hóa đơn đã lưu không bị sửa.
- Preview đã có đủ `bookingServiceId`, tên, số lượng, đơn giá, thành tiền theo CORE-01; sau thay đổi gọi lại preview để lấy tổng mới. Mỗi lần gọi tạo một dòng riêng, giữ giá và tên tại thời điểm gọi.


<a id="report-core03-h3"></a>

#### File chính

- `backend/src/main/java/com/homestay/controller/BookingController.java`
- `backend/src/test/java/com/homestay/controller/BookingServicesSqlServerIT.java`
- `backend/scripts/test-core03.ps1`
- `DOCS.md#migration`: hợp đồng POST/DELETE và quy tắc đồng thời.
- README và kế hoạch chi tiết đã cập nhật.

Không thay schema và không cần migration mới cho CORE-03. Các cột lưu tên/giá từ migration 002 đã đáp ứng yêu cầu.


<a id="report-core03-h4"></a>

#### Kiểm chứng đã chạy

`scripts/test-core03.ps1` biên dịch bằng Maven Wrapper offline: **BUILD SUCCESS**, gồm backend và 4 chương trình kiểm tra tích hợp.

Chạy trực tiếp controller/JDBC trên SQL Server thật, dùng database UUID riêng: **21 nhóm đạt**, exit code 0, database thử đã xóa trong finally.

| Nhóm | Kết quả |
|---|---|
| Hai loại dịch vụ × nhiều số lượng | Preview đủ trường; tổng dịch vụ 470.000, tổng hóa đơn 1.070.000 đúng |
| Gọi cùng dịch vụ nhiều lần, xóa một dòng | ID riêng, không gộp dòng, tổng mới đúng, xóa lặp bị chặn |
| Đổi tên/giá catalog | Dòng cũ giữ snapshot; dòng mới dùng bảng giá hiện tại |
| quantity/ID/body sai, thiếu/null/phần lẻ/vượt giới hạn | Bị từ chối, không ghi dòng |
| quantity 10.000 và dịch vụ giá 0 | Được chấp nhận khi hợp lệ |
| Booking/catalog/dòng không tồn tại, ID không dương | Bị từ chối |
| Catalog Ngừng | Chặn gọi mới, cho xóa dòng gọi nhầm đã có |
| Confirmed/Cancelled/CheckedOut | Chặn cả thêm và xóa |
| Booking còn CheckedIn nhưng đã có Payments | Chặn cả thêm và xóa |
| Khách quá ngày dự kiến nhưng còn CheckedIn | Được gọi/xóa dịch vụ theo tình trạng lưu trú thực tế |
| Client gửi giá/tên/booking giả trong body | Dùng path và catalog server, không tin dữ liệu giá từ client |
| Hai yêu cầu gọi dịch vụ đồng thời | Giữ cả hai dòng và tổng tiền đúng |
| Hai yêu cầu xóa cùng dòng đồng thời | Chỉ một thành công |
| Gọi và xóa đồng thời | Mỗi thao tác chỉ tác động dòng của mình |
| Gọi đồng thời checkout | Dòng được thu vào hóa đơn hoặc bị từ chối sau chốt; tổng Payments khớp các dòng thực tế |
| Xóa đồng thời checkout | Dòng được xóa trước khi thu hoặc giữ nguyên khi đã thu; tổng và chi tiết hóa đơn khớp |
| Thao tác sau thanh toán | Bị từ chối, JSON hóa đơn không bị ghi đè |
| Trigger gây lỗi sau INSERT/DELETE | Rollback không để sót/mất dòng; thử lại sau bỏ lỗi được |
| Vượt giới hạn dòng/tổng dịch vụ cộng dồn | Từ chối, không phát sinh dòng dở |
| Tổng dịch vụ vừa cột nhưng cộng phòng bị vượt | Từ chối trước khi ghi |
| Hai booking có dịch vụ riêng | Xóa dòng booking này không tác động booking kia |

Hồi quy `scripts/test-core01.ps1`: **9 nhóm đạt**, exit code 0, database thử riêng đã xóa. Tổng 30 nhóm đạt trong lượt kiểm chứng CORE-03 này. Không chạy lại toàn bộ FIX-02/CORE-02 vì code lịch phòng/thống kê không thay đổi; các tương tác checkout và snapshot liên quan đã được kiểm tra trực tiếp trong hai bộ trên.

Log `Injected service insert/delete failure`, `Injected snapshot failure` và `Amount outside supported VND range` là lỗi cố ý tạo để kiểm tra rollback/giới hạn. Không có thao tác nghiệp vụ nào được chạy trên database đang dùng.


<a id="report-core03-h5"></a>

#### Giới hạn và áp dụng

Chưa chạy HTTP/trình duyệt và chưa restart ứng dụng đang chạy. Hợp đồng lỗi nghiệp vụ vẫn giữ HTTP 200 với `{success:false,message}` đến FIX-03. Phân quyền thực hiện ở SEC-01; giao diện thêm/xóa dịch vụ và bảng kê hoàn chỉnh thuộc FE-02.

CORE-03 không yêu cầu SQL mới. Nếu database hiện tại chưa nâng cấp các đợt trước, vẫn phải sao lưu và chạy 001 → 002 → 003 theo hướng dẫn trước khi dùng backend này. Các migration đó chưa tự áp dụng lên database đang dùng trong phiên làm việc.

---

<a id="report-sec01"></a>

## Báo cáo SEC-01

<a id="report-sec01-h1"></a>

### Kết quả SEC-01 — Phiên server, phân quyền và mật khẩu hash

Ngày: 26/10/2026. Đã hoàn thành code, migration/công cụ chuyển tài khoản, frontend xác thực và kiểm tra tích hợp.


<a id="report-sec01-h2"></a>

#### Hành vi sau thay đổi

- Spring Security bảo vệ API bằng phiên server. Cookie HOMESTAY_SESSION là HttpOnly, SameSite=Lax, thời gian không hoạt động 30 phút; không dùng localStorage/sessionStorage hoặc role/userId client làm quyền truy cập.
- Login đổi session ID và CSRF token, không trim mật khẩu. GET `/auth/me` trả profile không có hash. Logout hủy phiên/cookie server. Header tối thiểu có tên/quyền và form đổi mật khẩu, logout gọi server.
- Mọi POST/PUT/DELETE, kể cả login, cần cookie và header CSRF. Frontend lấy token qua `/api/auth/csrf`, gửi credentials, xóa cache token sau thay đổi đăng nhập và không tự retry request ghi khi lỗi/mất phản hồi.
- Admin được CRUD danh mục và thống kê; staff được nghiệp vụ khách/đặt/nhận/trả phòng/dịch vụ/hóa đơn, không sửa/xóa danh mục phòng/dịch vụ, không xóa khách, không xem thống kê. Rule chưa định nghĩa bị từ chối. Mọi controller đã bỏ wildcard @CrossOrigin, CORS tập trung chỉ cho origin cụ thể.
- Mật khẩu dùng DelegatingPasswordEncoder với Spring Security PBKDF2 v5_8, salt riêng. Không có nhánh login plaintext/noop hoặc tự hash lúc login. Công cụ offline chuyển toàn bộ legacy trong transaction, không reset mật khẩu và không in giá trị mật khẩu/hash. Backend kiểm tra migration khi khởi động.
- Đổi mật khẩu lấy userId từ phiên, xác minh cũ, yêu cầu mới 12–128 ký tự và khác cũ, tăng auth_version và hủy phiên hiện tại. Các phiên cũ khác bị từ chối ở request tiếp theo. Thay role/xóa user cũng làm phiên mất hiệu lực.
- Login giới hạn 5 lần thử/tài khoản và 30 lần thử/IP trong 15 phút, bộ đếm có giới hạn bộ nhớ và đồng bộ khi có nhiều request. Đổi mật khẩu cũng giới hạn thử. Lỗi credential không phân biệt tài khoản có tồn tại.


<a id="report-sec01-h3"></a>

#### File chính

- `backend/pom.xml`: thêm spring-boot-starter-security theo BOM Spring Boot hiện có.
- `backend/src/main/java/com/homestay/security/`: config, principal, tài khoản, hash, migration và giới hạn đăng nhập.
- `AuthController.java`; bỏ wildcard CORS trên các controller nghiệp vụ.
- `backend/src/main/resources/application.properties`, `.env.example`: session cookie, timeout, origin, Secure flag cho HTTPS.
- `database/migrations/004_account_password_metadata.sql`, `database/schema.sql`, `database/seed_data.sql`.
- `frontend/js/api.js`, `auth.js`: phiên/CSRF/logout/đổi mật khẩu; các JS trang chờ kiểm tra đăng nhập trước tải dữ liệu; login.html thêm autocomplete/giới hạn input.
- `backend/scripts/security-classpath.ps1`, `migrate-passwords.ps1`, `test-sec01.ps1`; cập nhật runner cũ để chọn Maven cache có thể ghi và truyền local repo khi compile.
- Test Java `security/SecurityHttpSqlServerIT.java` và test frontend `src/test/js/security-client.test.cjs`.


<a id="report-sec01-h4"></a>

#### Kiểm chứng

Chạy `backend/scripts/test-sec01.ps1`: Maven compile/test-compile và tạo classpath thành công; Java khởi tạo database SQL Server UUID riêng, chuyển tài khoản cũ, chạy Spring Boot/Tomcat ở port ngẫu nhiên và gọi **HTTP thật** bằng cookie jar. **13 nhóm đạt**, exit code 0; server đã đóng và database thử được xóa.

| Nhóm | Kết quả |
|---|---|
| SQL 004 chạy lại, startup guard | Cột thêm đúng, legacy không được coi là tài khoản đã hash |
| Chuyển mật khẩu | Sai scheme rollback cả đợt; giữ mật khẩu có khoảng trắng; salt/hash đúng; chạy lại không đổi hash |
| Giới hạn đăng nhập | Chặn IP/account, không vượt giới hạn khi đồng thời; hết cửa sổ thời gian cho thử lại |
| Tài nguyên public và truy cập ẩn danh | Login/assets 200; API bảo vệ 401; trang nghiệp vụ chuyển về login |
| Login và cookie/CSRF | Login thiếu CSRF bị chặn; session ID/token đổi; cookie HttpOnly/SameSite=Lax; response không chứa hash |
| API ghi admin | Thiếu/sai token 403; có token tạo phòng và đọc thống kê được |
| Credential và throttle HTTP | Sai user/sai password cùng 401/thông báo; tên quá dài bị từ chối; vượt số lần thử 429 |
| Ma trận staff và giả quyền | Đọc danh mục được, thống kê/CRUD danh mục/xóa khách 403; role/userId/header giả không nâng quyền |
| Luồng staff qua HTTP | Tạo khách → đặt → check-in → thêm/xóa dịch vụ → checkout → lấy hóa đơn hoạt động với cookie/CSRF |
| CORS | Origin dev cụ thể có credentials được phép; origin lạ và cấu hình wildcard bị từ chối |
| Kết thúc phiên | Phiên bị expire trên Tomcat, cookie giả, logout và replay cookie cũ đều không truy cập được API |
| Đổi mật khẩu | Cũ sai/mới ngắn bị chặn; bỏ qua userId giả; giữ khoảng trắng; mật khẩu cũ và phiên trước đó không dùng được; mới login được |
| Role đổi ở DB | Phiên đã đăng nhập bị vô hiệu hóa, không giữ quyền cũ |

Test phiên hết hạn dùng thao tác expire phiên trên Tomcat rồi xác nhận HTTP 401, không chờ 30 phút thực. Cookie Secure chưa kiểm tra qua HTTPS thực tế; dev HTTP để Secure=false theo cấu hình.

Chạy Node `src/test/js/security-client.test.cjs`: **8 nhóm đạt** với fetch/DOM mô phỏng. Gồm: browser storage giả không cấp quyền; chờ `/me` trước tải; gửi credentials/CSRF; refresh token ở lần thử lại chủ động, không tự lặp thao tác ghi; giữ message 403; chuyển login khi token request nhận 401; logout gọi server/xóa cache; mật khẩu có khoảng trắng và chặn double submit login.

Kiểm tra cú pháp toàn bộ JS frontend bằng `node --check`: đạt. Không chạy trình duyệt để xác minh bố cục form/header trong đợt này. Các luồng HTTP thực đã kiểm tra; hình thức giao diện và ẩn/khóa đầy đủ nút theo quyền vẫn thuộc FE-05.

Tổng lượt kiểm chứng cuối: **21 nhóm** (13 Java/HTTP/SQL Server và 8 frontend mô phỏng). Không dùng kết quả mock để thay cho kiểm tra session/CSRF/SQL thực.


<a id="report-sec01-h5"></a>

#### Nâng cấp và giới hạn

**SQL 004 và công cụ hash chưa áp dụng lên database đang dùng**, ứng dụng đang chạy chưa restart. Backend mới cần hoàn thành nâng cấp trước: backup/dừng ghi → SQL 001–004 → `scripts/migrate-passwords.ps1 -Apply` → chạy backend mới và mở `/pages/login.html` cùng origin. Database mới dùng schema/seed đã cập nhật, không cần chuyển lại các hash demo. Chi tiết trong `DOCS.md#migration`.

Không reset tài khoản cũ. Phiên cũ trong browser storage không được công nhận; người dùng đăng nhập lại sau nâng cấp. Script migrate dùng cấu hình DBConnection hiện tại; không tự chạy khi build/test hoặc khởi động server. Các test chỉ thay đổi database UUID thử.

Trong môi trường này cache Maven người dùng bị chặn ghi, đã dùng `D:/Thuctap/.maven-cache` thay vì thay settings toàn máy. Script cho phép chỉ định `-MavenRepository` và tự ưu tiên cache workspace nếu có. Dependency theo BOM dự án hiện có; không nâng toàn bộ Spring Boot trong đầu việc này.

Phiên và rate limit hiện ở một tiến trình; triển khai nhiều instance cần cấu hình lưu trữ chung khi làm RELEASE-01. Vô hiệu hóa auth_version/role có hiệu lực ở request kế tiếp, không hủy transaction nghiệp vụ đã qua bước kiểm tra trước thời điểm đổi.

HTTP lỗi xác thực đã có 401/403/429/503 và JSON thống nhất; lỗi nghiệp vụ cũ, validation toàn hệ thống và render dữ liệu an toàn tiếp tục ở FIX-03. Không coi SEC-01 là hoàn thành mọi hạng mục đưa website vào sử dụng thực tế.

Tham khảo thiết kế: [Spring Security — Password Storage](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html) giải thích PasswordEncoder và adaptive hash; [Spring Security — CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html) mô tả token trong phiên và bảo vệ request ghi. Hành vi API theo phiên bản dependency thực tế đã được kiểm chứng bằng HTTP ở trên.

---

<a id="report-fix03"></a>

## Báo cáo FIX-03

<a id="report-fix03-h1"></a>

### Kết quả FIX-03 — Lỗi API, validation và render dữ liệu

Ngày: 26/10/2026. Đã hoàn thành code, migration 005, frontend và kiểm tra trên SQL Server thử riêng.


<a id="report-fix03-h2"></a>

#### Thay đổi

- Thêm `api/ApiProblem`, `ApiErrors`, `ApiExceptionHandler`, `Input` làm hợp đồng lỗi/validation chung. HTTP lỗi có `{success:false,code,message}`; 400/404/409/500/503 phân biệt sai input, thiếu tài nguyên, xung đột, lỗi xử lý và dữ liệu không truy cập được. 401/403/429 của SEC-01 tiếp tục hoạt động. JSON thành công giữ dạng cũ; GET danh sách không bị bọc lại.
- Các controller không trả mảng rỗng/tổng quan dở hoặc false-not-found khi SQL thất bại, không gửi e.getMessage()/stacktrace về client. Lỗi bất ngờ log phía server và trả mã tham chiếu. API ghi vẫn dùng transaction/rollback, các kiểm tra gọi trực tiếp controller vẫn nhận Map lỗi, còn lớp HTTP đặt status đúng.
- Kiểm tra kiểu dữ liệu, ID dương, độ dài, số nguyên/số tiền, enum, ngày, khoảng tháng/năm. Giá không hợp lệ không biến thành 0 hoặc bị cắt phần lẻ. Booking note tối đa 500; tên/mã/mô tả và các trường khách/dịch vụ theo schema.
- Khách hàng có documentType CCCD/PASSPORT/LEGACY, validation riêng và validationWarnings. Migration giữ nguyên giấy tờ/phone/email cũ và đánh dấu cần đối soát, không đoán hộ chiếu. Sửa giá trị khác trên bản ghi cũ được giữ dữ liệu sai nhưng không đổi; thay giá trị phải hợp lệ. Form có chọn loại giấy tờ/cảnh báo, dùng cùng quy tắc với server.
- Các bảng phòng/khách/dịch vụ/booking/check-in/checkout và báo cáo/dropdown không còn ghép dữ liệu vào innerHTML/onclick. Dùng textContent, tạo DOM và click callback giữ dữ liệu gốc. Chỉ còn một template innerHTML tĩnh cho form mật khẩu, không chèn dữ liệu người dùng.
- api.js đọc lỗi JSON bất kể HTTP status, không giả thành công nếu server cũ trả success:false, không nhận JSON hỏng như dữ liệu hợp lệ. Giữ cookie/CSRF và không tự retry request ghi. Các trang hiện message server; bảng lỗi xóa dữ liệu cũ, thống kê dùng `--` thay vì 0; không ghi đè lỗi thành “không có dữ liệu” khi tìm kiếm lại.
- Ngày nhập frontend kiểm tra đúng định dạng/ngày thực/ngày Việt Nam và thứ tự; tạm tính không biến ngày sai thành một đêm. Nối API phòng trống theo ngày và nút hủy vẫn thuộc FE-01.


<a id="report-fix03-h3"></a>

#### File chính

- `backend/src/main/java/com/homestay/api/`: hợp đồng lỗi và validation dùng chung.
- `controller/BookingController.java`, `RoomController.java`, `CustomerController.java`, `ServiceStatController.java`, `RoomSchedule.java`, `BookingInvoiceSnapshot.java`.
- `database/schema.sql`, `database/migrations/005_customer_document_type.sql`.
- Các file `frontend/js/` và `frontend/pages/customers.html`.
- `backend/scripts/test-fix03.ps1`, runner hồi quy dùng classpath dependency đầy đủ.
- `backend/src/test/java/com/homestay/security/ValidationHttpSqlServerIT.java`, `backend/src/test/js/validation-render.test.cjs`.


<a id="report-fix03-h4"></a>

#### Kiểm chứng

Maven compile/test-compile thành công qua helper script. Tất cả thử nghiệm dữ liệu dùng database SQL Server UUID riêng; server HTTP thử dùng port ngẫu nhiên. Không thực hiện nghiệp vụ hay migration trên database đang dùng.

| Bộ kiểm tra | Kết quả |
|---|---|
| FIX-03 HTTP + SQL Server | 24 nhóm đạt, gồm 13 nhóm hồi quy SEC-01, migration 005 và 10 nhóm hợp đồng lỗi/validation/dữ liệu cũ |
| FIX-02 controller/JDBC | 12 nhóm đạt: trạng thái, checkout đồng thời, rollback và UNIQUE |
| CORE-01 controller/JDBC | 9 nhóm đạt: giá đã lưu, hóa đơn, migration và seed |
| CORE-02 controller/JDBC | 22 nhóm đạt: overlap, ngày, hủy và trạng thái/phòng đồng thời |
| CORE-03 controller/JDBC | 21 nhóm đạt: dịch vụ, tổng tiền, xóa và đồng thời checkout |
| Frontend FIX-03 | 9 nhóm đạt với DOM/fetch mô phỏng có chặn innerHTML |
| Frontend SEC-01 | 8 nhóm đạt: phiên, CSRF, cookie, lỗi và password spaces |

Tổng **105 nhóm đạt** trong các bộ kiểm tra trên, tất cả exit code 0. Không cộng lại 13 nhóm SEC-01 riêng ngoài 24 nhóm HTTP. Các database thử đã được xóa khi kết thúc. Log các bộ hồi quy đặt trong `backend/target/fix03-*-regression.log` (thư mục build, không dùng làm dữ liệu nghiệp vụ).

HTTP kiểm tra các trường hợp: tài nguyên không tồn tại 404; ID sai 400; trùng giấy tờ/danh mục và trạng thái sai 409; hộ chiếu hợp lệ, tên O'Neil/HTML-looking text round-trip; dữ liệu cũ giữ nguyên/có cảnh báo và sửa đúng thì hết cảnh báo; số âm/phần lẻ/overflow/enum sai; note/ngày/quantity/phương thức/tháng/năm; JSON hỏng 400, content-type sai 415; JSON hóa đơn bị hỏng có chủ đích trả 500, không lộ nội dung hoặc stack trace. Tạm đổi tên bảng Customers/Rooms/Services trong DB thử để gây lỗi truy vấn: API trả 503, không trả []/{} hay tổng quan dở; bảng được trả về tên cũ trong finally.

Frontend kiểm tra 6 renderer bảng, callback sửa/xóa/check-in với tên chứa dấu nháy và HTML, dropdown phòng/khách, validation CCCD/hộ chiếu và legacy, tải lỗi rõ ràng/KPI chưa biết, tiền/độ dài/ngày/enum, checkout 404 vẫn khóa nút thanh toán và hợp đồng 400/401/403/404/409/500/503/JSON hỏng. Mini DOM được thiết kế ném lỗi nếu renderer gọi innerHTML; không có IMG/SCRIPT do dữ liệu tạo ra.

Kiểm tra cú pháp toàn bộ JavaScript bằng node --check và PowerShell runner; git diff --check không có lỗi whitespace. Chưa dùng trình duyệt thật để đánh giá bố cục/tương tác hiển thị; kiểm tra render là DOM mô phỏng, session/API dùng HTTP thật.


<a id="report-fix03-h5"></a>

#### Áp dụng và giới hạn

**Migration 005 chưa chạy trên database đang dùng**, backend hiện tại chưa restart. Sao lưu/dừng ghi, hoàn thành 001–004 + công cụ hash SEC-01, rồi chạy 005 theo `DOCS.md#migration`. Database mới dùng schema/seed đã cập nhật.

Quy tắc hộ chiếu 6–20 chữ cái ASCII/chữ số là quy tắc nhập liệu của dự án, không đại diện mọi quốc gia. Dữ liệu legacy được đánh dấu để người quản lý xác minh, không tự sửa giấy tờ/điện thoại. Số cccd vẫn unique như trước; chưa tách theo quốc gia/loại giấy tờ.

Các ghi chú ở báo cáo cũ rằng lỗi nghiệp vụ trả HTTP 200 đã được thay thế bởi FIX-03. Client ngoài dự án cần xử lý HTTP không phải 2xx và đọc message/code. Cấu trúc response thành công giữ nguyên, bổ sung documentType/validationWarnings ở khách hàng.

FE-01/FE-02 và các mục giao diện/báo cáo còn lại tiếp tục theo kế hoạch, không coi đợt này đã làm xong nút hủy, chọn phòng theo ngày hoặc modal gọi dịch vụ hoàn chỉnh.

---

<a id="report-fe01"></a>

## Báo cáo FE-01

<a id="report-fe01-h1"></a>

### Kết quả FE-01 — Giao diện đặt/hủy/nhận phòng

Ngày: 26/10/2026. Đã hoàn thành code, hướng dẫn và kiểm tra frontend bằng DOM/API mô phỏng.


<a id="report-fe01-h2"></a>

#### Thay đổi

- `frontend/js/bookings.js`: dùng GET available-rooms với ngày nhận/trả, bỏ GET rooms + lọc status Trống. Có trạng thái khách/phòng đang tải, rỗng và lỗi riêng; khóa xác nhận khi chưa tải/chọn đủ hoặc tiền/ngày không hợp lệ. Date input/change cùng khoảng đang tải dùng chung request; generation bảo vệ khi đổi ngày, đóng/mở form hoặc phản hồi đến sai thứ tự.
- Chọn lại ngày xóa ngay phòng/tạm tính cũ trong lúc tải; chỉ giữ lựa chọn trước nếu phòng vẫn nằm trong kết quả mới. Tính số đêm theo ngày UTC, kiểm tra tiền nguyên và giới hạn, giữ ngày nghiệp vụ Việt Nam. Không biến ngày sai thành một đêm.
- Tạo booking chặn gửi lặp/đóng khi đang gửi; thành công reset/đóng modal và tải bảng. Lỗi/xung đột tải lại khách/phòng/booking, giữ message server và không tự POST lại. Trường hợp mất phản hồi cũng đối soát bằng GET.
- Chỉ Confirmed có nút hủy, có xác nhận với tên khách/phòng, khóa theo booking đang gửi và refresh bảng sau kết quả. GET bảng có generation riêng để dữ liệu cũ không khôi phục nút hủy sau thay đổi trạng thái.
- `frontend/js/checkin.js`: danh sách hiển thị rõ điều kiện ngày, khóa trước ngày nhận/từ ngày trả, mở modal và bổ sung confirmCheckin đang bị thiếu trong HTML cũ. Kiểm tra lại ngày khi xác nhận; khóa gửi/đóng trong lúc xử lý. Lỗi server tải mới danh sách và buộc chọn lại thay vì tự lặp PUT. Chỉ sau thành công mới hiện liên kết trang khách đang ở.
- `frontend/pages/bookings.html`, `checkin.html`: sửa cột bảng cho khớp dữ liệu JS (8 cột); thêm nhãn/loading/error, nút tải lại, đúng ID/handler modal, giới hạn ghi chú và hướng dẫn nghiệp vụ. Không thêm migration hoặc đổi backend.
- Giữ render dữ liệu bằng DOM/textContent/callback từ FIX-03, giữ helper cookie/CSRF/error từ SEC-01.


<a id="report-fe01-h3"></a>

#### Kiểm chứng đã chạy

`node backend/src/test/js/booking-checkin.test.cjs`: **19 nhóm đạt**, exit code 0.

| Nhóm | Kết quả |
|---|---|
| Tải ban đầu và API theo ngày | Chờ đủ dữ liệu; không GET rooms/lọc trạng thái hiện tại; tạm tính đúng |
| Đổi ngày nhanh, phản hồi/lỗi cũ | Chỉ khoảng mới có hiệu lực, dữ liệu cũ không ghi đè |
| Ngày sai/quá khứ/bằng/ngược | Không gửi API tìm phòng hoặc hiện tạm tính hợp lệ |
| Rỗng/lỗi và tải lại | Thông báo khác nhau, khóa submit, tải lại hồi phục |
| Đóng modal khi request chưa xong | Response cũ không điền lại form đã đóng |
| Input/change cùng khoảng pending | Chia sẻ một request |
| Tạo booking thành công và bấm liên tiếp | Chỉ một POST; reset/đóng modal và tải bảng |
| Server trả xung đột | Giữ lỗi, tải lại phòng/bảng, xóa lựa chọn không còn trống |
| Mất phản hồi POST | Kiểm tra lại bằng GET, không POST tự động lần hai |
| Note quá dài/tổng tiền vượt giới hạn | Bị chặn trước khi gửi |
| Nút hủy và bấm liên tiếp | Chỉ Confirmed; một PUT; refresh trạng thái |
| Hủy bị từ chối/bảng trả sai thứ tự | Giữ message, không hiện lại hành động stale |
| Check-in theo ngày và modal | Khóa chưa đến/đã hết kỳ, thông tin an toàn |
| Nhận phòng thành công | Một PUT, khóa lúc gửi, link dịch vụ chỉ hiện sau thành công |
| Nhận phòng bị server từ chối | Refresh và yêu cầu chọn lại, không retry tự động |
| Qua ngày trước khi xác nhận | Kiểm tra lại, chặn khi đã đến ngày trả |
| HTML/JS | ID và handler khớp, cả hai bảng có đúng 8 cột |

Bảng gộp các tình huống gần nhau để dễ đọc; chương trình in 19 nhóm PASS. Hồi quy `validation-render.test.cjs` **9 nhóm đạt**, `security-client.test.cjs` **8 nhóm đạt**. Tổng lượt này **36 nhóm frontend mô phỏng đạt**. Kiểm tra cú pháp hai JS và git diff --check đạt; không chạy lại các bộ Java không thay đổi trong đợt này.


<a id="report-fe01-h4"></a>

#### Phạm vi chưa kiểm chứng

Chưa chạy trình duyệt thật hoặc ghép frontend đang chạy với SQL Server trong đợt này. Mock kiểm tra request/DOM/race/control state, không chứng minh bố cục hoặc hành vi trình duyệt. API/SQL thuộc CORE-02/SEC-01/FIX-03 đã có kiểm tra trước, không cộng các kết quả đó vào số kiểm tra FE-01. Cần QA-01 kiểm tra tích hợp thực tế, gồm đặt nối tiếp, xung đột khi có hai nhân viên và server clock/ngày.

Không có migration mới, không sửa dữ liệu hoặc restart ứng dụng đang dùng. Nếu database chưa nâng cấp các đợt trước, vẫn phải hoàn thành 001–005 và công cụ hash SEC-01. Hướng dẫn vận hành ở `DOCS.md#guide-fe01`.

Modal thêm/xóa dịch vụ, hóa đơn và in lại thuộc FE-02. Liên kết sau check-in chỉ dẫn đến trang khách đang ở, không giả lập việc đã gọi dịch vụ hay thanh toán.

---

<a id="report-fe02"></a>

## Báo cáo FE-02

<a id="report-fe02-h1"></a>

### Kết quả FE-02 — Dịch vụ, thanh toán và hóa đơn

Ngày 26/10/2026. Hoàn thành code và kiểm tra DOM/API mô phỏng, SQL Server và HTTP thật trên database thử riêng. Chưa xác nhận bố cục/in trên trình duyệt thật.


<a id="report-fe02-h2"></a>

#### Thay đổi

- `frontend/js/checkout.js`, `frontend/pages/checkout.html`: khách đang ở mở tạm tính BK; modal thêm dịch vụ chỉ lấy danh mục Hoạt động, hiển thị giá và số lượng nguyên 1–10000. Thêm/hủy đúng bookingServiceId, khóa thao tác trong khi ghi, tải lại preview sau thành công hoặc lỗi; không tự lặp request ghi.
- Bảng kê có tên, số lượng, đơn giá và thành tiền đã lưu, số đêm và giá phòng theo booking. Kiểm tra tổng tiền/chi tiết trước khi bật thu tiền. Cảnh báo giá dựng lại hoặc sai lệch đối soát của dữ liệu cũ được hiển thị. Dữ liệu động dùng textContent/DOM/callback.
- Phân biệt Tạm tính BK và Hóa đơn HD. Sau POST checkout lấy bản chính thức bằng GET payments/{paymentId}; chỉ bản đã thanh toán mới có nút in hoạt động. Mất phản hồi POST thì GET payments?bookingId để đối soát, không gửi lại POST tự động; nếu chưa rõ kết quả thì khóa thu tiền đến khi người dùng kiểm tra lại.
- `BookingController.java`: bổ sung GET `/api/payments` và bộ lọc bookingId để liệt kê hóa đơn đã thu và đối soát. Không tính lại hóa đơn từ bảng giá hiện tại. Admin/staff được đọc; giữ CSRF, phân quyền và transaction hiện có.
- Danh sách Hóa đơn đã thu cho xem/in lại sau reload trang; hỗ trợ liên kết `checkout.html?paymentId=...` để FE-04 dùng tiếp. Các phương thức chỉ ghi nhận nhân viên đã thu, chưa xác minh tiền về hay tích hợp cổng thanh toán.
- `style.css`: print chỉ giữ phần hóa đơn, ẩn sidebar/nút/phần nền; giới hạn chiều cao modal và cuộn nội dung khi cần. Gọi/hủy hộp thoại in không gửi API thanh toán.


<a id="report-fe02-h3"></a>

#### Kiểm chứng

- `node backend/src/test/js/checkout-rooms.test.cjs`: 12 nhóm FE-02/03 đạt. Gồm 2 nước + 1 thuê xe đúng tổng, hủy đúng dòng, dữ liệu độc hại thành văn bản, preview lỗi/thiếu chi tiết, response cũ, chống gửi lặp, bản chính thức, in lặp không ghi và mất phản hồi thanh toán.
- Hồi quy frontend: FIX-03 9, FE-01 19, SEC-01 8 nhóm đạt. Tổng frontend đợt này 48 lượt nhóm đạt.
- `backend/scripts/test-core02.ps1`: 26 nhóm đạt, gồm kiểm tra mới danh sách payments/đối soát và FE-03.
- `backend/scripts/test-sec01.ps1`: 13 nhóm HTTP/SQL đạt; có kiểm tra mới staff đọc payments/lọc rooms và bị 403 ở PUT rooms/{id}/status.
- `backend/scripts/test-core03.ps1`: 21 nhóm SQL đạt, gồm đồng thời gọi/hủy dịch vụ với checkout.
- `backend/scripts/test-core01.ps1`: 9 nhóm SQL đạt, gồm giá/thông tin hóa đơn không đổi sau cập nhật danh mục.
- Kiểm tra cú pháp JS và git diff --check đạt. Log lỗi được chủ động tạo trong các test rollback là tình huống kiểm tra, các bộ kết thúc exit code 0.


<a id="report-fe02-h4"></a>

#### Giới hạn và vận hành

Không có migration mới. Không sửa database nghiệp vụ đang dùng hoặc restart backend đang chạy. Cần restart backend bằng code mới để có API danh sách payments. Database cũ vẫn cần đủ migration 001–005 và chuyển mật khẩu SEC-01.

Đã thử chạy Chrome headless để xem giao diện/in, nhưng môi trường báo Access denied khi tạo khóa profile nên không tạo được ảnh/bản in kiểm chứng. Các test DOM chỉ kiểm tra logic, không chứng minh bố cục, tiếng Việt/font hoặc phân trang bản in. QA-01 cần kiểm tra trình duyệt và luồng giao diện nối backend/SQL thật; SQL/HTTP đã được kiểm tra riêng trên database dùng một lần và đã dọn sau test. Danh sách hóa đơn hiện tải toàn bộ, chưa có phân trang.

Hướng dẫn thao tác: [DOCS.md#guide-fe0203](#guide-fe0203).

---

<a id="report-fe03"></a>

## Báo cáo FE-03

<a id="report-fe03-h1"></a>

### Kết quả FE-03 — Bộ lọc phòng và bảo trì

Ngày 26/10/2026. Hoàn thành code, kiểm tra DOM mô phỏng và SQL/HTTP thật trong database thử riêng. Chưa kiểm tra trực quan trên trình duyệt.


<a id="report-fe03-h2"></a>

#### Thay đổi

- GET `/api/rooms?type=Single&status=Trống&search=P`: kết hợp loại, trạng thái và tìm mã/tên. Truy vấn dùng parameter, search được coi là chuỗi literal (kể cả `%`, nháy đơn), giới hạn 100 ký tự; loại/trạng thái sai trả lỗi. Không có bộ lọc vẫn trả mảng như trước.
- Trạng thái được tính bằng helper CORE-02 theo ngày Việt Nam trước khi lọc, không dùng giá trị đã lưu có thể lỗi thời. Không cần ghi vào database chỉ để qua ngày mới.
- PUT `/api/rooms/{id}/status` chỉ nhận Trống (kích hoạt) hoặc Bảo trì, khóa phòng trong transaction và dùng quy tắc dùng chung. Chặn bảo trì khi còn Confirmed/CheckedIn, kể cả booking tương lai. Kích hoạt tính lại trạng thái thực tế, không ép phòng còn khách thành Trống.
- PUT danh mục cũ không nhận Đã đặt/Đang sử dụng từ client; nếu có Trống/Bảo trì vẫn phải đi qua cùng kiểm tra và khóa. Bỏ trường trạng thái khỏi form sửa, sửa tên/giá không tắt bảo trì hoặc ghi đè trạng thái theo booking. Giá booking đã lưu được giữ nguyên.
- Frontend có ba bộ lọc, debounce tìm kiếm và generation chặn dữ liệu cũ ghi đè. Tải lại khi quay về trang và khi phát hiện đổi ngày; tách tải/rỗng/lỗi. Sau ghi/lỗi tải lại theo bộ lọc đang chọn.
- Chỉ admin thấy và gọi được các nút thêm/sửa/xóa/bảo trì. Gửi bảo trì/xóa được khóa theo phòng; lưu form chặn bấm lặp và đóng khi đang gửi. Server vẫn bảo vệ quyền qua SEC-01, không dựa vào việc ẩn nút.


<a id="report-fe03-h3"></a>

#### Kiểm chứng

- `test-core02.ps1`: 26 nhóm SQL đạt (22 hồi quy + 4 nhóm FE-02/03 mới): kết hợp ba bộ lọc, search literal, lọc theo ngày mới, booking tương lai chặn cả API status/API cũ, bật/tắt bảo trì, từ chối gán trạng thái tự động và giữ phòng đang có khách.
- `test-sec01.ps1`: 13 nhóm HTTP/SQL đạt, bổ sung kiểm tra staff GET bộ lọc được phép và PUT status bị 403.
- `checkout-rooms.test.cjs`: 12 nhóm frontend FE-02/03 đạt, gồm query được encode, response cũ, staff không có thao tác, admin chỉ gửi một request bảo trì và form sửa không gửi status.
- Hồi quy frontend FIX-03 9, FE-01 19, SEC-01 8 nhóm; hồi quy SQL CORE-03 21 và CORE-01 9 nhóm đạt. Cú pháp JS và git diff --check đạt.


<a id="report-fe03-h4"></a>

#### Giới hạn và vận hành

Không thêm migration hoặc thay dữ liệu nghiệp vụ đang dùng. Cần restart backend bằng code mới. Cần QA-01 kiểm tra trực quan và luồng UI thật với backend/SQL; Chrome headless chưa chạy được do môi trường chặn tạo profile. Không có lịch bảo trì theo ngày, vẫn là cơ chế bật/tắt theo kế hoạch.

Hướng dẫn và lệnh kiểm tra: [DOCS.md#guide-fe0203](#guide-fe0203).

---

<a id="report-fe04"></a>

## Báo cáo FE-04

<a id="report-fe04-h1"></a>

### Kết quả FE-04 — Tìm khách và lịch sử lưu trú

Ngày 26/10/2026. Hoàn thành code và kiểm tra DOM/API mô phỏng cùng HTTP/SQL Server thật trên database thử riêng.


<a id="report-fe04-h2"></a>

#### Thay đổi

- `CustomerController.java`: GET `/api/customers?search=` tìm tên, CCCD/hộ chiếu hoặc điện thoại bằng parameter; ký tự `%` và nháy đơn là văn bản literal, giới hạn 100 ký tự. Không truyền search vẫn trả mảng như trước.
- GET `/api/customers/{id}/bookings` trả mảng booking, phòng, ngày, số đêm, trạng thái, khoản đã thu và paymentId. GET `/api/customers/{id}/history` trả `{success, customer, bookings, totalPaid}` cho modal có tổng chi tiêu. Khách không tồn tại trả 404, ID sai trả 400, query lỗi trả 503.
- Lịch sử chỉ nối Payments đã thanh toán, không nối Booking_Services; mỗi booking có tối đa một payment theo constraint hiện có. Tổng chỉ cộng khoản đã thu, không cộng giá dự kiến hoặc khoản chưa thanh toán. Tổng được tính từ chính các dòng trả về. `totalPaid` là chuỗi số nguyên đồng để tránh mất chính xác khi tổng cả đời vượt giới hạn số nguyên JavaScript; từng dòng `paidTotal` vẫn là số trong giới hạn hóa đơn.
- `customers.js/html`: tìm phía server có debounce và generation chống phản hồi cũ. Modal có trạng thái tải/rỗng/lỗi, tải lại, tổng đã thu và liên kết `checkout.html?paymentId=...` tới hóa đơn FE-02. Khách/phòng và nội dung động dùng textContent; kiểm tra tổng khớp các dòng trước khi hiển thị.
- Giữ validation CCCD/hộ chiếu và cảnh báo dữ liệu cũ từ FIX-03. Lưu khách khóa gửi lặp/đóng khi đang gửi; xóa khóa theo khách và refresh sau kết quả. Staff thêm/sửa/xem lịch sử, không có nút xóa; server tiếp tục chặn xóa staff và chặn xóa khách có booking.


<a id="report-fe04-h3"></a>

#### Kiểm chứng

- `backend/scripts/test-fix03.ps1`: **29 nhóm HTTP/SQL đạt**, gồm 24 nhóm hồi quy và 5 nhóm mới FE-04. Kiểm tra search theo ba trường; input SQL-looking; history/bookings; khách mới; 404/400/401; nhiều dịch vụ không nhân dòng; loại khoản chưa thanh toán; không đổi tiền khi đổi danh mục; liên kết hóa đơn; scope theo khách; xóa 409/403; mất bảng trả 503. Database thử được tạo riêng và dọn sau test.
- `customers-account.test.cjs`: **14 nhóm FE-04/05 đạt**, gồm tìm kiếm, race, dữ liệu độc hại, lịch sử rỗng/lỗi, đóng modal, tổng không khớp, hóa đơn 0 đồng và quyền staff.
- Hồi quy frontend: `security-client.test.cjs` 11, `booking-checkin.test.cjs` 19, `checkout-rooms.test.cjs` 12, `validation-render.test.cjs` 9 nhóm đạt. Tổng frontend đợt FE-04/05: **65 lượt nhóm đạt**.
- Cú pháp JS và git diff --check đạt. Các log lỗi SQL/JSON được cố ý tạo để kiểm tra rollback/lỗi an toàn; bộ test kết thúc exit code 0.


<a id="report-fe04-h4"></a>

#### Giới hạn

Không thêm migration, không sửa dữ liệu nghiệp vụ hoặc restart ứng dụng đang dùng. Cần chạy backend code mới để có API bổ sung; database cũ vẫn cần migration 001–005 và chuyển mật khẩu SEC-01. Danh sách/lịch sử chưa phân trang. Chưa kiểm tra trực quan hoặc luồng UI nối SQL thật trong đợt này; phần đó tiếp tục ở QA-01.

Hướng dẫn: [DOCS.md#guide-fe0405](#guide-fe0405).

---

<a id="report-fe05"></a>

## Báo cáo FE-05

<a id="report-fe05-h1"></a>

### Kết quả FE-05 — Tài khoản và phiên đăng nhập

Ngày 26/10/2026. Hoàn thành code và kiểm tra DOM/fetch mô phỏng; hồi quy xác thực, CSRF, quyền server và đổi mật khẩu qua HTTP/SQL thật.


<a id="report-fe05-h2"></a>

#### Thay đổi

- Tách UI tài khoản dùng chung sang `frontend/js/account.js`, nạp sau `api.js` trên bảy trang được bảo vệ. Trang login chỉ nạp api/auth. Tên và vai trò lấy từ `/api/auth/me`, hiển thị bằng DOM an toàn; header có đổi mật khẩu và đăng xuất, không tạo lặp khi kiểm tra lại phiên.
- Các trang bắt đầu với trạng thái chờ phiên, chỉ mở nội dung sau khi `/me` thành công. Không xác nhận được thì giữ nội dung/modal ẩn và có nút Thử lại. `api.js` tiếp tục là cơ chế auth duy nhất cho dữ liệu được bảo vệ; cookie/CSRF từ SEC-01, không tin sessionStorage. Storage bị vô hiệu hóa cũng không làm hỏng auth.
- Khi rời trang, che nội dung và xóa các ô mật khẩu trước khi browser lưu trang. Khi khôi phục bằng Back/Forward cache, gọi lại `/me`; hết phiên chuyển login, thay user/quyền thì reload dữ liệu. Response identity/401 cũ không ghi đè phiên mới; request ghi đang chờ CSRF cũ bị chặn trước khi gửi, người dùng có thể thao tác mới sau kiểm tra phiên.
- Đăng xuất ở header/sidebar dùng cùng khóa gửi, gọi POST server rồi ẩn nội dung/chuyển login. Mất phản hồi chỉ đối soát GET `/me`, không tự lặp POST. Server tiếp tục vô hiệu hóa cookie/phiên và chặn API sau logout.
- Đổi mật khẩu kiểm tra độ dài, không toàn khoảng trắng, khác mật khẩu cũ và nhập lại khớp; giữ nguyên khoảng trắng của mật khẩu gửi server. Khóa trường/nút khi đang gửi; lỗi hiện message server. Thành công xóa form và yêu cầu đăng nhập lại.
- `auth.js` không lỗi khi thiếu login-form; có phiên tại login thì chuyển rooms, giữ chống gửi lặp và không lưu thông tin xác thực trong storage.
- Hoàn thiện quyền UI: staff không thấy thống kê, nút danh mục phòng/dịch vụ hoặc xóa khách; vẫn thêm/sửa khách, đặt/hủy/nhận/trả phòng, gọi dịch vụ và xem hóa đơn. Bổ sung guard trong JS dịch vụ; quyền server vẫn quyết định. Header và menu dùng được ở chiều rộng mobile bằng CSS chung.


<a id="report-fe05-h3"></a>

#### Kiểm chứng

- `customers-account.test.cjs`: **14 nhóm FE-04/05 đạt**: header an toàn/cập nhật quyền/không lặp; form mật khẩu lỗi/thành công/gửi lặp; logout lặp/mất phản hồi; pagehide/pageshow; auth unavailable và retry; response /me cũ; auth.js thiếu form; thứ tự script và gate trên toàn bộ trang.
- `security-client.test.cjs`: **11 nhóm đạt**, gồm 8 nhóm SEC-01 cũ và 3 nhóm FE-05 mới: login còn phiên, response 401 cũ và request ghi chờ CSRF cũ; kiểm tra thao tác mới phục hồi sau revalidation.
- Hồi quy FE-01 19, FE-02/03 12, FIX-03 frontend 9 nhóm đạt. Tổng **65 lượt nhóm frontend**.
- `test-fix03.ps1`: **29 nhóm HTTP/SQL đạt**, gồm xác thực/CSRF, staff bị chặn API quản trị, logout và replay cookie, phiên hết hạn, đổi mật khẩu thu hồi phiên và đăng nhập bằng mật khẩu mới; kèm validation và FE-04.
- Kiểm tra cú pháp và git diff --check đạt.


<a id="report-fe05-h4"></a>

#### Giới hạn và vận hành

Không thêm migration, thay dữ liệu nghiệp vụ hoặc restart backend đang dùng. Sau chạy backend code mới nên tải lại trang để nạp `account.js` và HTML mới. Chưa kiểm tra trực quan, mobile hoặc Back/Forward cache trên browser thật trong đợt này; các event/DOM được mô phỏng và server được kiểm tra riêng. QA-01 cần kiểm chứng tích hợp UI trên browser thật. Việc hết phiên được phát hiện khi có request API hoặc khi khôi phục trang, chưa có polling kiểm tra đăng nhập liên tục.

Hướng dẫn: [DOCS.md#guide-fe0405](#guide-fe0405).

---

<a id="legacy-readme"></a>

## Giới thiệu và hướng dẫn cài đặt gốc

<a id="legacy-readme-h1"></a>

### 🏠 Homestay Management System

Dự án thực tập – Hệ thống quản lý Homestay
**Stack:** HTML/CSS/JS → Java Spring Boot → Microsoft SQL Server (JDBC)

**Cập nhật FE-04/FE-05:** khách hàng có tìm kiếm server, lịch sử lưu trú, tổng chi tiêu đã thu và link hóa đơn; các trang dùng chung header tài khoản, đổi mật khẩu/logout, kiểm tra phiên khi quay lại và nút theo quyền admin/staff. Không thêm migration; cần chạy backend code mới và tải lại frontend. Kiểm tra DOM và HTTP/SQL đạt, QA trực quan còn ở QA-01. Xem [hướng dẫn](#guide-fe0405), [báo cáo FE-04](#report-fe04), [FE-05](#report-fe05).

**Cập nhật FE-02/FE-03:** đã có thêm/hủy dịch vụ cho khách đang ở, bảng kê giá đã lưu, hóa đơn chính thức và xem/in lại; phòng có bộ lọc kết hợp mã/tên–loại–trạng thái và bảo trì/kích hoạt dành cho admin. Không cần migration mới; cần chạy backend code mới để có API bổ sung. Kiểm tra DOM và SQL/HTTP đạt; kiểm tra trực quan/in còn ở QA-01. Xem [hướng dẫn thao tác](#guide-fe0203), [báo cáo FE-02](#report-fe02) và [báo cáo FE-03](#report-fe03).

**Cập nhật FE-01:** form đặt phòng đã tìm theo khoảng ngày, có hủy booking Confirmed và xử lý xung đột/tải chậm; trang nhận phòng dùng modal xác nhận, hiển thị điều kiện ngày và chặn gửi lặp. Không cần migration mới. Xem [hướng dẫn FE-01](#guide-fe01) và [báo cáo](#report-fe01).

**Cập nhật FIX-03:** database cũ cần chạy tiếp migration 005 để thêm loại giấy tờ sau các bước SEC-01. API lỗi đã dùng HTTP 400/401/403/404/409/500/503 với JSON rõ ràng; frontend hiển thị dữ liệu động bằng DOM an toàn và hỗ trợ CCCD/hộ chiếu, cảnh báo dữ liệu cũ. Xem [hướng dẫn nâng cấp hiện tại](#migration) và [báo cáo FIX-03](#report-fix03).

**Cập nhật SEC-01:** backend đã dùng phiên server, CSRF, phân quyền admin/staff và mật khẩu hash. Database cũ cần sao lưu/dừng ghi, chạy SQL migration 001–004 rồi `backend/scripts/migrate-passwords.ps1 -Apply` trước khi chạy backend mới. Mở frontend qua `http://localhost:8080/pages/login.html`. Xem [hướng dẫn nâng cấp tài khoản](#migration) và [báo cáo SEC-01](#report-sec01). Các đoạn hướng dẫn cũ về sessionStorage hoặc mở file/Live Server không còn là luồng mặc định.

**Cập nhật CORE-01:** database đã có dữ liệu cần sao lưu, dừng backend và chạy migration 001 rồi 002 theo [hướng dẫn nâng cấp](#migration) trước khi chạy backend mới. Không chạy lại schema/seed trên database cũ. Database mới dùng schema/seed đã cập nhật. Giá giao dịch và bản hóa đơn được lưu riêng; xem lại bằng `GET /api/payments/{id}`. Kết quả kiểm tra ở [báo cáo CORE-01](#report-core01).

**Cập nhật CORE-02:** chạy tiếp migration 003 cho index và danh sách booking cũ cần đối soát. Backend đã có tìm phòng theo ngày, hủy booking và trạng thái phòng theo ngày Việt Nam; giao diện sẽ tích hợp ở FE-01. Xem [hướng dẫn API/quy tắc](#migration) và [báo cáo CORE-02](#report-core02).

**Cập nhật CORE-03:** hoàn thiện API thêm/xóa dịch vụ đang lưu trú, đồng bộ với checkout và chặn tổng hóa đơn vượt giới hạn. Không cần migration mới. Xem [báo cáo CORE-03](#report-core03); giao diện dịch vụ sẽ tích hợp ở FE-02.

---


<a id="legacy-readme-h2"></a>

#### 👥 Nhóm phát triển

| Thành viên | Vai trò |
|---|---|
| Đức Đại (Leader) | Backend Java Spring Boot (tất cả API) + JavaScript toàn bộ Frontend |
| Sỹ Đại | Frontend HTML + CSS (tất cả các trang) |
| Long | Database SQL Server (Rooms, Bookings, Services) + query thống kê |
| Hưng | Database SQL Server (Users, Customers, Payments) + query thống kê |

---


<a id="legacy-readme-h3"></a>

#### 📁 Cấu trúc dự án

```
homestay-app/
├── .env.example          ← Mẫu cấu hình (copy thành .env)
├── frontend/
│   ├── css/style.css
│   ├── pages/            ← 8 trang HTML
│   └── js/               ← JS xử lý từng trang
├── backend/              ← Java Spring Boot
│   ├── pom.xml
│   └── src/main/java/com/homestay/
│       ├── controller/   ← API endpoints
│       ├── db/           ← Kết nối JDBC
│       └── HomestayApplication.java
└── database/
    ├── schema.sql        ← Tạo bảng
    └── seed_data.sql     ← Dữ liệu mẫu
```

---


<a id="legacy-readme-h4"></a>

#### ⚡ Hướng dẫn cài đặt và chạy


<a id="legacy-readme-h5"></a>

##### Bước 1 – Clone dự án về máy

```bash
git clone https://github.com/DoDucDai/Homestay.git
cd Homestay
```


<a id="legacy-readme-h6"></a>

##### Bước 2 – Tạo file `.env`

```bash
# Windows
copy .env.example .env
```

Mở file `.env` lên và **đổi mật khẩu SQL Server của máy bạn**:

```
DB_HOST=localhost
DB_PORT=1433
DB_NAME=HomestayDB
DB_USER=sa
DB_PASSWORD=mat_khau_cua_ban   <-- doi cai nay
```


<a id="legacy-readme-h7"></a>

##### Bước 3 – Tạo Database (Long + Hưng làm)

1. Mở **SQL Server Management Studio (SSMS)**
2. Chạy file `database/schema.sql` → tạo database và 7 bảng
3. Chạy file `database/seed_data.sql` → thêm dữ liệu mẫu


<a id="legacy-readme-h8"></a>

##### Bước 4 – Chạy Backend (Đức Đại làm)

Mở terminal trong thư mục `backend/`:

```bash
mvn spring-boot:run
```

> Backend chạy tại: `http://localhost:8080`
> Nếu lỗi, kiểm tra lại file `.env` và SQL Server đang chạy chưa.


<a id="legacy-readme-h9"></a>

##### Bước 5 – Mở Frontend

Mở trực tiếp file `frontend/pages/login.html` bằng trình duyệt.

> ⚠️ Phải chạy Backend trước rồi mới mở Frontend.

---


<a id="legacy-readme-h10"></a>

#### 🔧 Yêu cầu cài đặt

| Công cụ | Version |
|---|---|
| Java JDK | 17+ |
| Maven | 3.6+ |
| SQL Server | 2019+ (hoặc SQL Server Express) |
| SSMS | Bất kỳ |
| Trình duyệt | Chrome / Edge / Firefox |

---


<a id="legacy-readme-h11"></a>

#### 🌐 API Endpoints

| Method | URL | Mô tả | Người làm BE |
|---|---|---|---|
| POST | `/api/auth/login` | Đăng nhập | Đức Đại |
| GET/POST/PUT/DELETE | `/api/rooms` | CRUD phòng | Đức Đại |
| GET/POST/PUT/DELETE | `/api/customers` | CRUD khách hàng | Đức Đại |
| GET/POST | `/api/bookings` | Booking + kiểm tra phòng trống | Đức Đại |
| PUT | `/api/checkin/{id}` | Check-in | Đức Đại |
| POST | `/api/checkout/{id}` | Check-out + tính tiền | Đức Đại |
| GET/POST/PUT/DELETE | `/api/services` | CRUD dịch vụ | Đức Đại |
| GET | `/api/statistics/overview` | Thống kê tổng hợp | Đức Đại (SQL: Long+Hưng) |
| GET | `/api/statistics/revenue` | Doanh thu theo tháng | Đức Đại (SQL: Hưng) |

---


<a id="legacy-readme-h12"></a>

#### 🔄 Quy trình làm việc với Git

```bash
# Truoc khi bat dau code moi ngay, pull code moi nhat ve
git pull

# Sau khi code xong, push len
git add .
git commit -m "Mo ta nhung gi ban vua lam"
git push
```

> ⚠️ File `.env` đã được gitignore – **KHÔNG** bị push lên GitHub. Mỗi người tự tạo file `.env` trên máy mình.

---

<a id="legacy-docs"></a>

## Tài liệu kỹ thuật gốc

<a id="legacy-docs-h1"></a>

### 📖 TÀI LIỆU DỰ ÁN – HOMESTAY MANAGEMENT SYSTEM

> Đọc file này trước khi bắt đầu code. Nó giải thích toàn bộ cấu trúc, file nào làm gì, hàm nào dùng như thế nào.

---


<a id="legacy-docs-h2"></a>

#### 1. CẤU TRÚC THƯ MỤC

```
homestay-app/
│
├── .env                  ← Config local CỦA BẠN (không lên GitHub)
├── .env.example          ← Mẫu .env, copy ra làm .env
├── DOCS.md#legacy-readme             ← Hướng dẫn cài đặt và chạy
│
├── frontend/
│   ├── css/
│   │   └── style.css     ← CSS DÙNG CHUNG cho tất cả trang (Sỹ Đại làm)
│   │
│   ├── pages/            ← CÁC TRANG HTML (Sỹ Đại làm HTML/CSS)
│   │   ├── login.html
│   │   ├── rooms.html
│   │   ├── customers.html
│   │   ├── bookings.html
│   │   ├── checkin.html
│   │   ├── checkout.html
│   │   ├── services.html
│   │   └── statistics.html
│   │
│   └── js/               ← XỬ LÝ LOGIC (Đức Đại)
│       ├── api.js         ← HÀM FETCH DÙNG CHUNG – ĐỌC KỸ PHẦN 3
│       ├── auth.js        ← Xử lý đăng nhập (Đức Đại)
│       ├── rooms.js       ← Xử lý trang phòng (Đức Đại)
│       ├── customers.js   ← Xử lý trang khách hàng (Đức Đại)
│       ├── bookings.js    ← Xử lý trang booking (Đức Đại)
│       ├── checkin.js     ← Xử lý trang check-in (Đức Đại)
│       ├── checkout.js    ← Xử lý trang check-out (Đức Đại)
│       ├── services.js    ← Xử lý trang dịch vụ (Đức Đại)
│       └── statistics.js  ← Xử lý trang thống kê (Đức Đại)
│
├── backend/
│   ├── pom.xml            ← Khai báo thư viện Java (không cần sửa)
│   └── src/main/
│       ├── java/com/homestay/
│       │   ├── HomestayApplication.java   ← Điểm chạy của backend (không sửa)
│       │   ├── db/
│       │   │   └── DBConnection.java      ← Kết nối SQL Server (không sửa)
│       │   └── controller/               ← API ENDPOINTS (Đức Đại)
│       │       ├── AuthController.java
│       │       ├── RoomController.java
│       │       ├── CustomerController.java
│       │       ├── BookingController.java
│       │       └── ServiceStatController.java
│       └── resources/
│           └── application.properties    ← Config Spring Boot (không sửa)
│
└── database/
    ├── schema.sql         ← Tạo 7 bảng trong SQL Server (Long + Hưng)
    └── seed_data.sql      ← Dữ liệu mẫu để test (Long + Hưng)
```

---


<a id="legacy-docs-h3"></a>

#### 2. DATABASE – 7 BẢNG

```
Users          → Tài khoản đăng nhập
Rooms          → Danh sách phòng
Customers      → Khách hàng
Bookings       → Đặt phòng (nối Customers + Rooms)
Services       → Các dịch vụ (giặt ủi, thuê xe...)
Booking_Services → Dịch vụ của từng booking
Payments       → Hóa đơn thanh toán
```


<a id="legacy-docs-h4"></a>

##### Quan hệ giữa các bảng:
```
Customers ──┐
            ├──► Bookings ──► Booking_Services ──► Services
Rooms ──────┘       │
                    └──► Payments
```


<a id="legacy-docs-h5"></a>

##### Trạng thái phòng (Rooms.status):
| Giá trị | Ý nghĩa |
|---|---|
| `Trống` | Phòng có thể đặt |
| `Đã đặt` | Đã có booking, chờ check-in |
| `Đang sử dụng` | Đã check-in |
| `Bảo trì` | Không cho đặt |


<a id="legacy-docs-h6"></a>

##### Trạng thái booking (Bookings.status):
| Giá trị | Ý nghĩa |
|---|---|
| `Confirmed` | Đã đặt, chờ check-in |
| `CheckedIn` | Đang ở |
| `CheckedOut` | Đã trả phòng |
| `Cancelled` | Hủy |

---


<a id="legacy-docs-h7"></a>

#### 3. CÁC HÀM DÙNG CHUNG TRONG `api.js`

> ⚠️ Mọi file JS đều phải dùng các hàm này thay vì tự viết fetch. File `api.js` được load trước nên tất cả hàm đều dùng được.


<a id="legacy-docs-h8"></a>

##### 3.1 Gọi API

```javascript
// Lấy dữ liệu (GET)
const data = await apiGet('/rooms');
// Tương đương: GET http://localhost:8080/api/rooms

// Tạo mới (POST)
const result = await apiPost('/rooms', { roomCode: 'P101', roomName: 'Phòng 101', price: 300000 });

// Cập nhật (PUT)
const result = await apiPut('/rooms/5', { roomName: 'Phòng mới', price: 400000 });

// Xóa (DELETE)
const result = await apiDelete('/rooms/5');
```


<a id="legacy-docs-h9"></a>

##### 3.2 Hiển thị thông báo

```javascript
// Thông báo thành công (màu xanh, tự mất sau 3 giây)
showAlert('Thêm phòng thành công!', 'success');

// Thông báo lỗi (màu đỏ)
showAlert('Vui lòng nhập đủ thông tin!', 'error');

// Thông báo thông tin (màu xanh dương)
showAlert('Đang tải dữ liệu...', 'info');
```
> ⚠️ Trang HTML phải có thẻ: `<div id="alert-message" class="alert" style="display:none;"></div>`


<a id="legacy-docs-h10"></a>

##### 3.3 Mở / Đóng Modal

```javascript
// Mở modal (truyền vào id của modal-overlay)
openModal('room-modal');

// Đóng tất cả modal
closeModal();
```


<a id="legacy-docs-h11"></a>

##### 3.4 Định dạng hiển thị

```javascript
// Định dạng tiền VNĐ
formatMoney(500000)        // → "500,000 VNĐ"
formatMoney(1200000)       // → "1,200,000 VNĐ"

// Định dạng ngày tháng
formatDate('2026-09-15')   // → "15/09/2026"
formatDate(null)           // → "--"
```


<a id="legacy-docs-h12"></a>

##### 3.5 Kiểm tra đăng nhập

```javascript
// Gọi đầu mỗi trang (nếu chưa đăng nhập → tự chuyển về login.html)
const user = checkLogin();
// user = { userId: 1, username: "admin", fullName: "Nguyễn Văn Admin", role: "admin" }
```

---


<a id="legacy-docs-h13"></a>

#### 4. API ENDPOINTS – DANH SÁCH ĐẦY ĐỦ

> Backend chạy tại: `http://localhost:8080`
> Tất cả API đều có prefix `/api`


<a id="legacy-docs-h14"></a>

##### Auth
| Method | URL | Mô tả | Người làm |
|---|---|---|---|
| POST | `/api/auth/login` | Đăng nhập | Đức Đại |


<a id="legacy-docs-h15"></a>

##### Rooms
| Method | URL | Mô tả | Người làm |
|---|---|---|---|
| GET | `/api/rooms` | Lấy danh sách tất cả phòng | Đức Đại |
| POST | `/api/rooms` | Thêm phòng mới | Đức Đại |
| PUT | `/api/rooms/{id}` | Sửa phòng | Đức Đại |
| DELETE | `/api/rooms/{id}` | Xóa phòng | Đức Đại |


<a id="legacy-docs-h16"></a>

##### Customers
| Method | URL | Mô tả | Người làm |
|---|---|---|---|
| GET | `/api/customers` | Lấy danh sách khách hàng | Đức Đại |
| POST | `/api/customers` | Thêm khách hàng | Đức Đại |
| PUT | `/api/customers/{id}` | Sửa khách hàng | Đức Đại |
| DELETE | `/api/customers/{id}` | Xóa khách hàng | Đức Đại |


<a id="legacy-docs-h17"></a>

##### Booking / Checkin / Checkout
| Method | URL | Mô tả | Người làm |
|---|---|---|---|
| GET | `/api/bookings?status=X` | Lấy booking theo trạng thái | Đức Đại |
| POST | `/api/bookings` | Tạo booking mới | Đức Đại |
| PUT | `/api/checkin/{id}` | Thực hiện check-in | Đức Đại |
| POST | `/api/checkout/{id}` | Thực hiện check-out + tạo hóa đơn | Đức Đại |


<a id="legacy-docs-h18"></a>

##### Services
| Method | URL | Mô tả | Người làm |
|---|---|---|---|
| GET | `/api/services` | Lấy danh sách dịch vụ | Đức Đại |
| POST | `/api/services` | Thêm dịch vụ | Đức Đại |
| PUT | `/api/services/{id}` | Sửa dịch vụ | Đức Đại |
| DELETE | `/api/services/{id}` | Xóa dịch vụ | Đức Đại |


<a id="legacy-docs-h19"></a>

##### Statistics
| Method | URL | Mô tả | Người làm |
|---|---|---|---|
| GET | `/api/statistics/overview` | Tổng hợp: phòng, khách, doanh thu | Đức Đại (SQL: Long+Hưng) |
| GET | `/api/statistics/revenue?month=9&year=2026` | Doanh thu theo tháng | Đức Đại (SQL: Hưng) |

---


<a id="legacy-docs-h20"></a>

#### 5. CSS CLASSES SẴN CÓ TRONG `style.css`

> Sỹ Đại dùng các class này khi làm HTML, không cần viết CSS thêm.


<a id="legacy-docs-h21"></a>

##### Layout
```html
<div class="wrapper">          <!-- Bọc toàn bộ trang -->
<nav class="sidebar">          <!-- Thanh menu bên trái -->
<div class="main-content">     <!-- Nội dung chính bên phải -->
<div class="card">             <!-- Khung trắng có shadow -->
<h1 class="page-title">        <!-- Tiêu đề trang -->
```


<a id="legacy-docs-h22"></a>

##### Bảng dữ liệu
```html
<table>
  <thead><tr><th>Cột 1</th></tr></thead>
  <tbody id="data-tbody"></tbody>   <!-- JS sẽ điền dữ liệu vào đây -->
</table>
```


<a id="legacy-docs-h23"></a>

##### Nút bấm
```html
<button class="btn btn-primary">Xanh dương</button>
<button class="btn btn-success">Xanh lá – Thêm mới</button>
<button class="btn btn-warning">Vàng – Sửa</button>
<button class="btn btn-danger">Đỏ – Xóa</button>
<button class="btn btn-secondary">Xám – Hủy</button>
```


<a id="legacy-docs-h24"></a>

##### Form nhập liệu
```html
<div class="form-group">
  <label for="input-id">Nhãn *</label>
  <input type="text" id="input-id" placeholder="Nhập...">
</div>
```


<a id="legacy-docs-h25"></a>

##### Modal (hộp thoại popup)
```html
<div class="modal-overlay" id="ten-modal">
  <div class="modal-box">
    <h3>Tiêu đề</h3>
    <!-- nội dung form -->
    <div class="modal-footer">
      <button class="btn btn-secondary" onclick="closeModal()">Hủy</button>
      <button class="btn btn-primary" onclick="luuDuLieu()">Lưu</button>
    </div>
  </div>
</div>
```


<a id="legacy-docs-h26"></a>

##### Badge trạng thái
```html
<span class="badge badge-available">Trống</span>
<span class="badge badge-booked">Đã đặt</span>
<span class="badge badge-occupied">Đang sử dụng</span>
<span class="badge badge-maintenance">Bảo trì</span>
<span class="badge badge-paid">Đã thanh toán</span>
<span class="badge badge-unpaid">Chưa thanh toán</span>
```


<a id="legacy-docs-h27"></a>

##### Thông báo
```html
<!-- Thêm vào HTML, JS sẽ tự điều khiển hiển thị -->
<div id="alert-message" class="alert" style="display:none;"></div>
```

---


<a id="legacy-docs-h28"></a>

#### 6. VÍ DỤ CODE MẪU


<a id="legacy-docs-h29"></a>

##### Ví dụ: Lấy danh sách phòng và render vào bảng (JS)

```javascript
// Trong rooms.js
async function loadRooms() {
    try {
        const rooms = await apiGet('/rooms');
        const tbody = document.getElementById('rooms-tbody');
        tbody.innerHTML = '';

        rooms.forEach(room => {
            const row = `
                <tr>
                    <td>${room.roomCode}</td>
                    <td>${room.roomName}</td>
                    <td>${room.roomType}</td>
                    <td>${formatMoney(room.price)}</td>
                    <td><span class="badge badge-available">${room.status}</span></td>
                    <td>
                        <button class="btn btn-warning" onclick="openEdit(${room.roomId})">Sửa</button>
                        <button class="btn btn-danger"  onclick="deleteRoom(${room.roomId})">Xóa</button>
                    </td>
                </tr>`;
            tbody.innerHTML += row;
        });
    } catch (err) {
        showAlert('Không tải được dữ liệu!', 'error');
    }
}

// Gọi khi trang load xong
document.addEventListener('DOMContentLoaded', () => {
    checkLogin();   // Kiểm tra đăng nhập
    loadRooms();    // Tải danh sách phòng
});
```


<a id="legacy-docs-h30"></a>

##### Ví dụ: Thêm phòng mới (JS)

```javascript
async function saveRoom() {
    const roomCode = document.getElementById('room-code').value.trim();
    const roomName = document.getElementById('room-name').value.trim();
    const price    = document.getElementById('room-price').value;

    if (!roomCode || !roomName || !price) {
        showAlert('Vui lòng nhập đủ thông tin!', 'error');
        return;
    }

    try {
        const result = await apiPost('/rooms', { roomCode, roomName, price: Number(price) });
        if (result.success) {
            showAlert('Thêm phòng thành công!', 'success');
            closeModal();
            loadRooms(); // Tải lại bảng
        } else {
            showAlert(result.message, 'error');
        }
    } catch (err) {
        showAlert('Lỗi kết nối server!', 'error');
    }
}
```


<a id="legacy-docs-h31"></a>

##### Ví dụ: GET request với tham số (JS)

```javascript
// Lấy booking có status = Confirmed
const bookings = await apiGet('/bookings?status=Confirmed');

// Lấy doanh thu tháng 9 năm 2026
const revenue = await apiGet('/statistics/revenue?month=9&year=2026');
```


<a id="legacy-docs-h32"></a>

##### Ví dụ: Controller Java lấy dữ liệu từ DB (Java)

```java
@GetMapping
public List<Map<String, Object>> getAllRooms() {
    List<Map<String, Object>> result = new ArrayList<>();

    String sql = "SELECT * FROM Rooms ORDER BY room_code";

    try (Connection conn = DBConnection.getConnection();
         PreparedStatement stmt = conn.prepareStatement(sql);
         ResultSet rs = stmt.executeQuery()) {

        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("roomId",      rs.getInt("room_id"));
            row.put("roomCode",    rs.getString("room_code"));
            row.put("roomName",    rs.getString("room_name"));
            row.put("roomType",    rs.getString("room_type"));
            row.put("price",       rs.getLong("price"));
            row.put("status",      rs.getString("status"));
            row.put("description", rs.getString("description"));
            result.add(row);
        }
    } catch (SQLException e) {
        e.printStackTrace();
    }

    return result;
}
```

---


<a id="legacy-docs-h33"></a>

#### 7. QUY TẮC CODE CHUNG


<a id="legacy-docs-h34"></a>

##### Đặt tên
| Loại | Quy tắc | Ví dụ |
|---|---|---|
| Java class | PascalCase | `RoomController`, `DBConnection` |
| Java method | camelCase | `getAllRooms()`, `createBooking()` |
| JS function | camelCase | `loadRooms()`, `saveCustomer()` |
| JS variable | camelCase | `roomId`, `customerName` |
| HTML id | kebab-case | `room-code`, `booking-modal` |
| CSS class | kebab-case | `btn-primary`, `modal-overlay` |


<a id="legacy-docs-h35"></a>

##### JSON response từ API (quy tắc chung)
```json
// Khi thành công
{ "success": true, "message": "..." }

// Khi thất bại
{ "success": false, "message": "Mô tả lỗi" }

// Khi trả về danh sách
[ { ... }, { ... } ]
```


<a id="legacy-docs-h36"></a>

##### Thứ tự load file trong HTML
```html
<!-- Cuối thẻ </body>, luôn load api.js TRƯỚC file JS của trang -->
<script src="../js/api.js"></script>
<script src="../js/rooms.js"></script>   ← File JS của trang này
```

---


<a id="legacy-docs-h37"></a>

#### 8. LỖI THƯỜNG GẶP & CÁCH XỬ LÝ

| Lỗi | Nguyên nhân | Cách sửa |
|---|---|---|
| `Failed to fetch` | Backend chưa chạy | Chạy `mvn spring-boot:run` trong thư mục `backend/` |
| `CORS error` | Thiếu `@CrossOrigin` | Đảm bảo mọi Controller đều có `@CrossOrigin(origins = "*")` |
| `Connection refused` | SQL Server chưa chạy | Mở SQL Server Configuration Manager, start service |
| `Login failed for user 'sa'` | Sai password trong `.env` | Mở file `.env`, sửa lại `DB_PASSWORD` |
| Tiếng Việt bị lỗi `???` | Query thiếu `N''` | Dùng `N'Trống'` thay vì `'Trống'` trong SQL Server |

---

<a id="legacy-dai"></a>

## Hướng dẫn Đức Đại gốc

<a id="legacy-dai-h1"></a>

### 📘 HƯỚNG DẪN CHI TIẾT DÀNH CHO THỰC TẬP SINH

<a id="legacy-dai-h2"></a>

#### HỆ THỐNG QUẢN LÝ HOMESTAY (BACKEND JAVA SPRING BOOT + JDBC + SQL SERVER)

> ⚠️ **LƯU Ý:** File này là tài liệu cá nhân hướng dẫn chi tiết dành cho bạn (Thực tập sinh / Leader), đã được cấu hình trong `.gitignore` để **KHÔNG bị commit lên GitHub**. Bạn có thể yên tâm ghi chú, đọc hiểu và sử dụng trong suốt quá trình thực tập.

---


<a id="legacy-dai-h3"></a>

#### MỤC LỤC
1. [TỔNG QUAN KIẾN TRÚC VÀ LUỒNG ĐI CỦA DỮ LIỆU (FLOW)](#legacy-dai-h4)
2. [CẤU TRÚC CODE BACKEND VÀ TRÁCH NHIỆM TỪNG FILE](#legacy-dai-h7)
3. [GIẢI THÍCH CHI TIẾT TỪNG CONTROLLER & CÁC API ĐÃ CODE](#legacy-dai-h9)
   - [3.1 AuthController – Xác thực đăng nhập](#legacy-dai-h10)
   - [3.2 RoomController – Quản lý danh mục phòng](#legacy-dai-h12)
   - [3.3 CustomerController – Quản lý khách hàng](#legacy-dai-h18)
   - [3.4 BookingController – Quản lý đặt phòng, Check-in & Check-out](#legacy-dai-h23)
   - [3.5 ServiceStatController – Quản lý dịch vụ & Thống kê doanh thu](#legacy-dai-h29)
4. [KỸ THUẬT LẬP TRÌNH QUAN TRỌNG ĐÃ ÁP DỤNG TRONG CODE](#legacy-dai-h32)
5. [HƯỚNG DẪN CÀI ĐẶT, CẤU HÌNH VÀ CHẠY DỰ ÁN TỪ A ĐẾN Z](#legacy-dai-h38)
6. [HƯỚNG DẪN TEST TỪNG API (POSTMAN / CURL / TRÌNH DUYỆT)](#legacy-dai-h46)
7. [CẨM NANG BẮT BỆNH VÀ XỬ LÝ LỖI (TROUBLESHOOTING)](#legacy-dai-h49)
8. [BÍ QUYẾT BẢO VỆ VÀ BÁO CÁO THỰC TẬP THÀNH CÔNG](#legacy-dai-h55)

---


<a id="legacy-dai-h4"></a>

#### 1. TỔNG QUAN KIẾN TRÚC VÀ LUỒNG ĐI CỦA DỮ LIỆU (FLOW)

Đối với một bạn mới bắt đầu thực tập backend, điều quan trọng nhất không phải là học thuộc từng dòng code, mà là **hiểu rõ luồng đi của một request (Request-Response Lifecycle)**.


<a id="legacy-dai-h5"></a>

##### Sơ đồ luồng toàn hệ thống:

```
[ Trình duyệt / Client ]
       │
       │ (1) Gửi HTTP Request (JSON) qua Fetch API (api.js)
       ▼
[ Spring Boot Controller ] (@RestController, @RequestMapping)
       │
       │ (2) Đọc Body / Param, Validate dữ liệu
       │ (3) Gọi DBConnection.getConnection()
       ▼
[ JDBC PreparedStatement ] (Tham số hóa ?, an toàn chống SQL Injection)
       │
       │ (4) Gửi lệnh SQL qua cổng 1433 (TCP/IP)
       ▼
[ Microsoft SQL Server ] (Database: HomestayDB)
       │
       │ (5) Thực thi truy vấn, trả về ResultSet
       ▼
[ Controller xử lý ]
       │
       │ (6) Đọc ResultSet ánh xạ sang Map<String, Object> hoặc List
       │ (7) Tự động đóng Connection (try-with-resources)
       ▼
[ Jackson JSON Converter ]
       │
       │ (8) Biến Java Map thành chuỗi JSON { success: true, ... }
       ▼
[ Trình duyệt / Client ] (Nhận kết quả hiển thị lên giao diện HTML)
```


<a id="legacy-dai-h6"></a>

##### Chi tiết từng chặng của luồng:
1. **Frontend gọi API:** Người dùng bấm nút "Thêm phòng" trên giao diện HTML -> Hàm `saveRoom()` trong file `rooms.js` chạy -> Gọi `apiPost('/rooms', data)` trong `api.js` -> Trình duyệt gửi gói tin HTTP `POST http://localhost:8080/api/rooms` kèm body dạng chuỗi JSON.
2. **Spring Boot tiếp nhận:** Web Server nhúng (Apache Tomcat chạy cổng 8080) nhận gói tin. Spring Boot quét xem Controller nào có `@RequestMapping("/api/rooms")` và `@PostMapping` -> Khớp với hàm `createRoom(@RequestBody Map<String, Object> body)`.
3. **Chuyển đổi dữ liệu tự động (Deserialization):** Spring Boot sử dụng thư viện **Jackson** để tự động giải mã chuỗi JSON từ client thành một `Map<String, Object>` trong Java.
4. **Kết nối Database:** Hàm lấy kết nối từ `DBConnection.getConnection()`, tạo câu lệnh `PreparedStatement` với các dấu hỏi chấm `?`.
5. **Thực thi trên SQL Server:** Lệnh SQL được gửi đến SQL Server. SQL Server kiểm tra bảng, khóa chính, khóa ngoại và thực hiện thêm/sửa/xóa hoặc truy vấn.
6. **Thu thập kết quả (ResultSet):** Kết quả trả về qua đối tượng `ResultSet rs`. Java duyệt qua từng dòng `while (rs.next())`, bốc từng cột cho vào `LinkedHashMap`.
7. **Đóng kết nối:** Khối lệnh `try (...)` kết thúc, connection được tự động đóng lại (tránh cạn kiệt tài nguyên kết nối).
8. **Phản hồi (Response):** Hàm trả về `Map<String, Object>`. Jackson tự động chuyển Map này thành chuỗi JSON và trả lại trình duyệt với mã HTTP 200 OK.

---


<a id="legacy-dai-h7"></a>

#### 2. CẤU TRÚC CODE BACKEND VÀ TRÁCH NHIỆM TỪNG FILE

Toàn bộ backend nằm trong thư mục `backend/`:

```
backend/
├── pom.xml                                    ← Quản lý thư viện Maven
└── src/main/
    ├── java/com/homestay/
    │   ├── HomestayApplication.java           ← Điểm khởi chạy hệ thống (Main Class)
    │   │
    │   ├── db/
    │   │   └── DBConnection.java              ← Quản lý kết nối SQL Server (JDBC)
    │   │
    │   └── controller/                        ← Các bộ điều hướng API (REST API)
    │       ├── AuthController.java            ← Đăng nhập, phân quyền người dùng
    │       ├── RoomController.java            ← Danh sách phòng, thêm, sửa, xóa phòng
    │       ├── CustomerController.java        ← Khách hàng, chống trùng CCCD
    │       ├── BookingController.java         ← Đặt phòng, Check-in, Check-out & Lập hóa đơn
    │       └── ServiceStatController.java     ← Danh mục dịch vụ + Thống kê doanh thu
    │
    └── resources/
        └── application.properties             ← Cấu hình cổng, CORS, DataSource
```


<a id="legacy-dai-h8"></a>

##### Trách nhiệm chi tiết của từng thành phần:

1. **`HomestayApplication.java`**:
   - Được gắn annotation `@SpringBootApplication`.
   - Chứa hàm `public static void main(String[] args)`.
   - Khi chạy file này, Spring Boot tự động khởi tạo Tomcat Server ở cổng 8080 và nạp toàn bộ các Controller vào bộ nhớ.

2. **`DBConnection.java`**:
   - Đảm bảo kết nối tới cơ sở dữ liệu Microsoft SQL Server thông qua driver `com.microsoft.sqlserver.jdbc.SQLServerDriver`.
   - Đọc các thông số cấu hình từ file `.env` (thông qua thư viện `spring-dotenv`) hoặc biến môi trường:
     - `DB_HOST`: Mặc định `localhost`
     - `DB_PORT`: Mặc định `1433`
     - `DB_NAME`: Tên database (`HomestayDB`)
     - `DB_USER`: Tài khoản SQL Server (`sa`)
     - `DB_PASSWORD`: Mật khẩu tài khoản `sa` của bạn
   - Chuỗi kết nối JDBC:
     `jdbc:sqlserver://localhost:1433;databaseName=HomestayDB;encrypt=true;trustServerCertificate=true`

3. **Gói `controller/`**:
   - Là nơi trực tiếp nhận các request từ phía trình duyệt / Postman.
   - Mỗi file controller chịu trách nhiệm cho một phân hệ nghiệp vụ cụ thể.

---


<a id="legacy-dai-h9"></a>

#### 3. GIẢI THÍCH CHI TIẾT TỪNG CONTROLLER & CÁC API ĐÃ CODE

Dưới đây là phân tích chi tiết từng controller để bạn hiểu cặn kẽ cách từng dòng code hoạt động:


<a id="legacy-dai-h10"></a>

##### 3.1 AuthController – Xác thực đăng nhập
- **Đường dẫn:** `/api/auth`
- **Annotation:** `@RestController`, `@RequestMapping("/api/auth")`, `@CrossOrigin(origins = "*")`


<a id="legacy-dai-h11"></a>

###### API: Đăng nhập (`POST /api/auth/login`)
- **Mục đích:** Kiểm tra thông tin tài khoản và mật khẩu của người dùng (Admin hoặc Nhân viên) để cấp quyền truy cập.
- **Dữ liệu đầu vào (Request Body JSON):**
  ```json
  {
    "username": "admin",
    "password": "123"
  }
  ```
- **Code hoạt động như thế nào:**
  1. Lấy `username` và `password` từ map `body`. Dùng `.trim()` để loại bỏ khoảng trắng dư thừa.
  2. Kiểm tra nếu một trong hai trường bị bỏ trống thì lập tức trả về lỗi:
     `{ "success": false, "message": "Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu!" }`
  3. Sử dụng `PreparedStatement` với câu lệnh SQL:
     ```sql
     SELECT user_id, username, full_name, role FROM Users WHERE username = ? AND password = ?
     ```
  4. Nếu có bản ghi trả về (`rs.next()`), trích xuất thông tin: `userId`, `username`, `fullName`, `role` (không trả về trường password để đảm bảo an toàn).
  5. Đóng gói vào JSON:
     ```json
     {
       "success": true,
       "message": "Đăng nhập thành công!",
       "user": {
         "userId": 1,
         "username": "admin",
         "fullName": "Nguyễn Văn Admin",
         "role": "admin"
       }
     }
     ```
  6. Nếu không tìm thấy, trả về `{ "success": false, "message": "Sai tên đăng nhập hoặc mật khẩu!" }`.

---


<a id="legacy-dai-h12"></a>

##### 3.2 RoomController – Quản lý danh mục phòng
- **Đường dẫn:** `/api/rooms`
- **Các chức năng:** Lấy danh sách, xem chi tiết, thêm mới, cập nhật, xóa phòng.


<a id="legacy-dai-h13"></a>

###### 1. Lấy danh sách phòng (`GET /api/rooms`)
- **SQL:**
  ```sql
  SELECT room_id, room_code, room_name, room_type, price, status, description
  FROM Rooms
  ORDER BY room_code ASC
  ```
- **Dữ liệu trả về:** Mảng các phòng sắp xếp theo mã phòng từ nhỏ đến lớn:
  ```json
  [
    {
      "roomId": 1,
      "roomCode": "P101",
      "roomName": "Phòng Đơn Tầng 1",
      "roomType": "Single",
      "price": 300000,
      "status": "Trống",
      "description": "Phòng có ban công thoáng mát"
    }
  ]
  ```


<a id="legacy-dai-h14"></a>

###### 2. Xem chi tiết một phòng (`GET /api/rooms/{id}`)
- **Path Variable:** `id` (mã số định danh `room_id`).
- **SQL:** `SELECT ... FROM Rooms WHERE room_id = ?`


<a id="legacy-dai-h15"></a>

###### 3. Thêm phòng mới (`POST /api/rooms`)
- **Body JSON gửi lên:**
  ```json
  {
    "roomCode": "P103",
    "roomName": "Phòng Đôi Tầng 1",
    "roomType": "Double",
    "price": 500000,
    "status": "Trống",
    "description": "Phòng 2 giường đơn"
  }
  ```
- **Xử lý logic nghiệp vụ đặc biệt:**
  - **Kiểm tra trùng mã phòng:** Trước khi insert, controller chạy câu lệnh `SELECT COUNT(*) FROM Rooms WHERE room_code = ?`. Nếu mã này đã có trong database, controller sẽ trả về cảnh báo ngay thay vì để SQL Server bắn lỗi ngoại lệ làm crash hệ thống.
  - **Giá trị mặc định:** Nếu client không truyền trạng thái `status`, code tự động gán là `"Trống"`.


<a id="legacy-dai-h16"></a>

###### 4. Cập nhật thông tin phòng (`PUT /api/rooms/{id}`)
- **Xử lý:** Kiểm tra xem mã phòng mới (nếu sửa) có bị trùng với một phòng KHÁC không (`WHERE room_code = ? AND room_id <> ?`). Nếu hợp lệ thì thực hiện lệnh UPDATE.


<a id="legacy-dai-h17"></a>

###### 5. Xóa phòng (`DELETE /api/rooms/{id}`)
- **Bảo vệ toàn vẹn dữ liệu (Foreign Key Protection):**
  - Trong thực tế, bạn không thể xóa một phòng nếu phòng đó đã từng được đặt bởi khách hàng (do ràng buộc bảng `Bookings` tham chiếu tới `Rooms`).
  - Code đã kiểm tra trước:
    ```sql
    SELECT COUNT(*) FROM Bookings WHERE room_id = ?
    ```
  - Nếu `count > 0`, code trả về:
    `{ "success": false, "message": "Không thể xóa phòng đã có lịch sử đặt phòng!" }`
  - Nếu phòng mới tạo chưa có booking nào, code mới thực hiện lệnh `DELETE`.

---


<a id="legacy-dai-h18"></a>

##### 3.3 CustomerController – Quản lý khách hàng
- **Đường dẫn:** `/api/customers`


<a id="legacy-dai-h19"></a>

###### 1. Lấy danh sách khách hàng (`GET /api/customers`)
- Trả về danh sách toàn bộ khách hàng, sắp xếp theo thứ tự khách hàng mới nhất lên đầu (`ORDER BY customer_id DESC`).


<a id="legacy-dai-h20"></a>

###### 2. Thêm khách hàng mới (`POST /api/customers`)
- **Body JSON gửi lên:**
  ```json
  {
    "fullName": "Trần Văn An",
    "cccd": "001099009999",
    "phone": "0987654321",
    "email": "an.tran@example.com",
    "address": "Hà Nội"
  }
  ```
- **Xử lý logic:**
  - Kiểm tra các trường bắt buộc: Họ tên, CCCD/Passport, Số điện thoại.
  - **Chống trùng CCCD:** Kiểm tra xem CCCD đã tồn tại trong CSDL chưa (`SELECT COUNT(*) FROM Customers WHERE cccd = ?`). Nếu đã có, trả về thông báo lỗi thân thiện.


<a id="legacy-dai-h21"></a>

###### 3. Cập nhật khách hàng (`PUT /api/customers/{id}`)
- Cập nhật thông tin địa chỉ, email, số điện thoại hoặc họ tên. Có kiểm tra chống trùng CCCD đối với các khách hàng khác.


<a id="legacy-dai-h22"></a>

###### 4. Xóa khách hàng (`DELETE /api/customers/{id}`)
- Kiểm tra xem khách hàng có đơn đặt phòng nào trong bảng `Bookings` không. Nếu có thì từ chối xóa để giữ dữ liệu kế toán và lịch sử.

---


<a id="legacy-dai-h23"></a>

##### 3.4 BookingController – Quản lý đặt phòng, Check-in & Check-out
- **Đường dẫn:** `/api`
- Đây là controller chứa nghiệp vụ phức tạp và quan trọng nhất của toàn bộ hệ thống!


<a id="legacy-dai-h24"></a>

###### 1. Lấy danh sách Booking (`GET /api/bookings`)
- Hỗ trợ bộ lọc theo trạng thái:
  - `GET /api/bookings` -> Lấy tất cả
  - `GET /api/bookings?status=Confirmed` -> Lấy các đơn vừa đặt (chờ check-in)
  - `GET /api/bookings?status=CheckedIn` -> Lấy các khách đang lưu trú
  - `GET /api/bookings?status=CheckedOut` -> Lấy các đơn đã hoàn thành
- **Kỹ thuật SQL:** Dùng `JOIN` giữa 3 bảng `Bookings`, `Customers` và `Rooms` để lấy đầy đủ tên khách, số điện thoại, mã phòng, giá phòng trong một câu query duy nhất.


<a id="legacy-dai-h25"></a>

###### 2. Tạo đơn đặt phòng mới (`POST /api/bookings`)
- **Body JSON gửi lên:**
  ```json
  {
    "customerId": 1,
    "roomId": 2,
    "checkinDate": "2026-10-05",
    "checkoutDate": "2026-10-08",
    "note": "Khách yêu cầu tầng yên tĩnh"
  }
  ```
- **Quy trình xử lý 3 bước trong Transaction (`conn.setAutoCommit(false)`):**
  1. **Bước 1 - Kiểm tra phòng:** Truy vấn trạng thái phòng. Nếu phòng không ở trạng thái `"Trống"`, lập tức dừng và báo lỗi `Phòng này hiện không còn trống!`.
  2. **Bước 2 - Tạo đơn:** `INSERT INTO Bookings` với trạng thái ban đầu là `'Confirmed'`. Dùng `Statement.RETURN_GENERATED_KEYS` để lấy về mã số `bookingId` vừa tạo.
  3. **Bước 3 - Khóa phòng:** `UPDATE Rooms SET status = N'Đã đặt' WHERE room_id = ?` để ngăn không cho người khác đặt trùng phòng này.
  4. Gọi `conn.commit()` để hoàn tất giao dịch.


<a id="legacy-dai-h26"></a>

###### 3. Thực hiện Check-in (`PUT /api/checkin/{bookingId}`)
- Khi khách đến nhận phòng:
  1. `UPDATE Bookings SET status = 'CheckedIn' WHERE booking_id = ?`
  2. `UPDATE Rooms SET status = N'Đang sử dụng' WHERE room_id = ?`
  3. Trả về thông báo thành công.


<a id="legacy-dai-h27"></a>

###### 4. Xem trước hóa đơn (`GET /api/checkout/preview/{bookingId}`)
- Chức năng cực kỳ hữu ích cho giao diện người dùng: Khi bấm nút "Check-out", giao diện cần mở popup modal hiển thị hóa đơn tạm tính cho khách xem trước khi xác nhận thu tiền.
- API này tính toán:
  - Số đêm ở = `ChronoUnit.DAYS.between(checkinDate, checkoutDate)` (tối thiểu 1 đêm).
  - Tiền phòng = `price * nights`.
  - Tiền dịch vụ phát sinh = `SUM(total_price) FROM Booking_Services WHERE booking_id = ?`.
  - Tổng tiền phải trả = `Tiền phòng + Tiền dịch vụ`.
  - Trả về danh sách chi tiết các dịch vụ khách đã sử dụng.


<a id="legacy-dai-h28"></a>

###### 5. Xác nhận Check-out & Lập hóa đơn (`POST /api/checkout/{bookingId}`)
- **Body JSON gửi lên:**
  ```json
  {
    "paymentMethod": "Tiền mặt"
  }
  ```
- **Quy trình xử lý chuẩn kế toán:**
  1. Tính toán lại chính xác số đêm, tiền phòng, tiền dịch vụ, tổng cộng.
  2. Tạo bản ghi hóa đơn trong bảng `Payments`:
     ```sql
     INSERT INTO Payments (booking_id, room_total, service_total, grand_total, payment_method, payment_status, paid_at)
     VALUES (?, ?, ?, ?, ?, N'Đã thanh toán', GETDATE())
     ```
  3. Cập nhật `Bookings SET status = 'CheckedOut'`.
  4. Giải phóng phòng: `UPDATE Rooms SET status = N'Trống'` (để phòng sẵn sàng cho khách tiếp theo).
  5. Commit transaction và trả về đối tượng `invoice` đầy đủ thông tin.

---


<a id="legacy-dai-h29"></a>

##### 3.5 ServiceStatController – Quản lý dịch vụ & Thống kê doanh thu
- **Đường dẫn:** `/api`


<a id="legacy-dai-h30"></a>

###### Phân hệ Dịch vụ:
- `GET /api/services`: Lấy bảng giá các dịch vụ của homestay (giặt ủi, thuê xe máy, ăn sáng, đưa đón sân bay,...).
- `POST /api/services`: Thêm dịch vụ mới. Có kiểm tra chống trùng tên dịch vụ.
- `PUT /api/services/{id}`: Chỉnh sửa tên hoặc đơn giá dịch vụ.
- `DELETE /api/services/{id}`: Xóa dịch vụ (có kiểm tra nếu dịch vụ đã có trong bảng `Booking_Services` thì từ chối xóa).


<a id="legacy-dai-h31"></a>

###### Phân hệ Thống kê:
- `GET /api/statistics/overview`:
  - Trả về toàn bộ số liệu tổng quan cho trang Dashboard / Báo cáo:
    - Tổng số phòng hiện có, số phòng trống, số phòng đã đặt, số phòng đang có khách, số phòng đang bảo trì.
    - Tổng số lượng khách hàng đã lưu trong hệ thống.
    - Số lượt đặt phòng trong tháng hiện tại (`MONTH(created_at) = MONTH(GETDATE())`).
    - Tổng doanh thu thực tế thu được trong tháng này từ các hóa đơn đã thanh toán.
- `GET /api/statistics/revenue?month=10&year=2026`:
  - Cho phép người quản lý tra cứu chi tiết danh sách tất cả các hóa đơn thanh toán trong một tháng và năm bất kỳ.
  - Hiển thị rõ: Ngày thanh toán, tên khách, số phòng, tiền phòng, tiền dịch vụ, tổng tiền và phương thức thanh toán.

---


<a id="legacy-dai-h32"></a>

#### 4. KỸ THUẬT LẬP TRÌNH QUAN TRỌNG ĐÃ ÁP DỤNG TRONG CODE

Là một thực tập sinh, khi bạn trình bày code với Leader hoặc Giảng viên hướng dẫn, hãy tự tin nêu bật các kỹ thuật chuẩn sau đây đã được áp dụng trong bài:


<a id="legacy-dai-h33"></a>

##### 1. Kỹ thuật `PreparedStatement` chống SQL Injection
- **Tuyệt đối không cộng chuỗi SQL:** Thay vì viết `"SELECT * FROM Users WHERE username = '" + username + "'"`, code luôn sử dụng dấu hỏi chấm `?`:
  ```java
  String sql = "SELECT * FROM Users WHERE username = ? AND password = ?";
  PreparedStatement stmt = conn.prepareStatement(sql);
  stmt.setString(1, username);
  stmt.setString(2, password);
  ```
- **Lợi ích:** Trình điều khiển JDBC sẽ tự động escape các ký tự nguy hiểm (như `' OR '1'='1`), vô hiệu hóa hoàn toàn các cuộc tấn công SQL Injection.


<a id="legacy-dai-h34"></a>

##### 2. Kỹ thuật Quản lý Tài nguyên với `try-with-resources`
- Mọi `Connection`, `PreparedStatement`, `ResultSet` đều là tài nguyên hệ điều hành có giới hạn. Nếu mở ra mà không đóng, sau vài chục request hệ thống sẽ bị lỗi `Too many connections` hoặc treo ứng dụng.
- Cú pháp `try (Connection conn = ...; PreparedStatement stmt = ...) { ... }` đảm bảo rằng dù code chạy thành công hay xảy ra ngoại lệ (exception), Java sẽ tự động gọi phương thức `.close()` giải phóng tài nguyên ngay lập tức.


<a id="legacy-dai-h35"></a>

##### 3. Kỹ thuật Quản lý Giao dịch (Database Transactions)
- Trong các thao tác nhiều bước như Check-out (tính tiền -> tạo hóa đơn -> đổi trạng thái booking -> đổi trạng thái phòng), nếu bước 3 thành công nhưng bước 4 lỗi mà không có Transaction thì phòng sẽ bị kẹt vĩnh viễn ở trạng thái "Đang sử dụng"!
- Code đã sử dụng:
  ```java
  conn.setAutoCommit(false); // Bắt đầu Transaction
  // ... Thực hiện chuỗi lệnh ...
  conn.commit();             // Xác nhận toàn bộ thành công
  // Nếu có lỗi -> conn.rollback(); // Hoàn tác về ban đầu
  ```


<a id="legacy-dai-h36"></a>

##### 4. Kỹ thuật Ép kiểu an toàn (Safe Number Parsing)
- Trong Spring Boot, khi client gửi JSON như `{"price": 300000}`, thư viện Jackson có thể deserialize thành kiểu `Integer`, `Long` hoặc `Double`. Nếu bạn ép kiểu cứng `(int) body.get("price")`, chương trình có thể văng lỗi `ClassCastException`.
- Hàm `parseLong` và `parseInt` được viết riêng để xử lý an toàn:
  ```java
  private long parseLong(Object val, long defaultVal) {
      if (val == null) return defaultVal;
      if (val instanceof Number n) return n.longValue();
      try {
          return Long.parseLong(val.toString().trim());
      } catch (Exception e) {
          return defaultVal;
      }
  }
  ```


<a id="legacy-dai-h37"></a>

##### 5. Hỗ trợ CORS (Cross-Origin Resource Sharing)
- Mỗi Controller đều có `@CrossOrigin(origins = "*")`. Điều này cho phép giao diện Frontend (chạy trên Live Server port 5500 hoặc mở trực tiếp từ file) có thể gửi request AJAX/Fetch đến Backend (chạy trên port 8080) mà không bị trình duyệt chặn vì chính sách bảo mật Same-Origin.

---


<a id="legacy-dai-h38"></a>

#### 5. HƯỚNG DẪN CÀI ĐẶT, CẤU HÌNH VÀ CHẠY DỰ ÁN TỪ A ĐẾN Z

Dưới đây là các bước thao tác thực tế từng bước một trên máy tính của bạn:


<a id="legacy-dai-h39"></a>

##### Bước 1: Khởi tạo Cơ sở dữ liệu trong SQL Server
1. Mở phần mềm **SQL Server Management Studio (SSMS)**.
2. Đăng nhập vào SQL Server (chọn chế độ `SQL Server Authentication`, user `sa`).
3. Mở file `d:/Thuctap/Homestay/database/schema.sql` trong SSMS -> Nhấn nút **Execute (F5)** để tạo Database `HomestayDB` và 7 bảng.
4. Mở tiếp file `d:/Thuctap/Homestay/database/seed_data.sql` -> Nhấn nút **Execute (F5)** để nạp dữ liệu mẫu.
5. Kiểm tra bằng cách mở tab mới và gõ:
   ```sql
   USE HomestayDB;
   SELECT * FROM Users;
   SELECT * FROM Rooms;
   ```
   Nếu thấy bảng hiện dữ liệu là thành công!


<a id="legacy-dai-h40"></a>

##### Bước 2: Cấu hình file `.env`
1. Mở file `.env` tại thư mục gốc dự án: `d:/Thuctap/Homestay/.env`.
2. Kiểm tra các dòng cấu hình:
   ```properties
   DB_HOST=localhost
   DB_PORT=1433
   DB_NAME=HomestayDB
   DB_USER=sa
   DB_PASSWORD=your_sql_server_password
   SERVER_PORT=8080
   ```
   > ⚠️ **Quan trọng:** Hãy thay thế `123456` bằng chính xác mật khẩu tài khoản `sa` trên SQL Server của máy bạn!


<a id="legacy-dai-h41"></a>

##### Bước 3: Chạy Backend Java


<a id="legacy-dai-h42"></a>

###### Cách 1: Sử dụng IntelliJ IDEA (Khuyên dùng)
1. Mở IntelliJ IDEA -> Chọn **File** -> **Open** -> Chọn thư mục `d:/Thuctap/Homestay/backend`.
2. Chờ IntelliJ tải các thư viện Maven trong file `pom.xml` (thường mất 1 - 2 phút ở lần mở đầu tiên).
3. Tìm file: `src/main/java/com/homestay/HomestayApplication.java`.
4. Nhấp chuột phải vào file -> Chọn **Run 'HomestayApplication'** (hoặc bấm biểu tượng tam giác màu xanh lá cây).
5. Nhìn vào tab Console bên dưới, nếu thấy dòng chữ:
   `Tomcat started on port 8080 (http) with context path ''`
   `Started HomestayApplication in ... seconds`
   -> Chúc mừng! Backend của bạn đã hoạt động hoàn hảo!


<a id="legacy-dai-h43"></a>

###### Cách 2: Sử dụng VS Code
1. Mở VS Code, cài đặt Extension: **Extension Pack for Java** và **Spring Boot Extension Pack**.
2. Mở thư mục `backend/` trong VS Code.
3. Mở file `HomestayApplication.java` -> Bấm nút **Run** ở góc trên cùng bên phải.


<a id="legacy-dai-h44"></a>

###### Cách 3: Sử dụng Terminal với lệnh Maven (Nếu máy đã cài Maven)
1. Mở terminal PowerShell tại thư mục `backend`:
   ```powershell
   cd d:\Thuctap\Homestay\backend
   mvn spring-boot:run
   ```


<a id="legacy-dai-h45"></a>

##### Bước 4: Chạy Frontend giao diện
1. Mở VS Code, cài extension **Live Server** (của tác giả Ritwick Dey).
2. Chuột phải vào file `frontend/pages/login.html` -> Chọn **Open with Live Server**.
3. Trình duyệt tự động mở tại địa chỉ: `http://127.0.0.1:5500/frontend/pages/login.html`.
4. Đăng nhập bằng tài khoản mẫu:
   - **Tài khoản:** `admin`
   - **Mật khẩu:** `123456`

---


<a id="legacy-dai-h46"></a>

#### 6. HƯỚNG DẪN TEST TỪNG API (POSTMAN / CURL / TRÌNH DUYỆT)

Khi backend đang chạy, bạn có thể kiểm tra từng API bằng các cách dưới đây:


<a id="legacy-dai-h47"></a>

##### 1. Test trên trình duyệt (Chỉ áp dụng cho các API `GET`):
Dán trực tiếp các link sau vào thanh địa chỉ của trình duyệt:
- `http://localhost:8080/api/rooms` -> Xem danh sách tất cả các phòng.
- `http://localhost:8080/api/customers` -> Xem danh sách khách hàng.
- `http://localhost:8080/api/bookings` -> Xem danh sách đơn đặt phòng.
- `http://localhost:8080/api/services` -> Xem danh sách dịch vụ.
- `http://localhost:8080/api/statistics/overview` -> Xem số liệu tổng quan.
- `http://localhost:8080/api/statistics/revenue?month=9&year=2026` -> Xem doanh thu tháng 9/2026.

---


<a id="legacy-dai-h48"></a>

##### 2. Bảng tổng hợp mẫu test Postman / Thunder Client:

| Chức năng | Phương thức HTTP | Đường dẫn (URL) | Request Body (JSON) |
|---|---|---|---|
| **Đăng nhập** | `POST` | `http://localhost:8080/api/auth/login` | `{"username":"admin", "password":"123456"}` |
| **Thêm phòng** | `POST` | `http://localhost:8080/api/rooms` | `{"roomCode":"P401", "roomName":"Phòng VIP Tầng 4", "roomType":"VIP", "price":1500000, "status":"Trống", "description":"View biển"}` |
| **Sửa phòng** | `PUT` | `http://localhost:8080/api/rooms/1` | `{"roomName":"Phòng Đơn Tầng 1 (Mới sửa)", "roomType":"Single", "price":320000, "status":"Trống", "description":"Có máy lạnh mới"}` |
| **Xóa phòng** | `DELETE` | `http://localhost:8080/api/rooms/6` | Không cần body |
| **Thêm khách hàng**| `POST` | `http://localhost:8080/api/customers` | `{"fullName":"Lê Văn Cường", "cccd":"001202008888", "phone":"0988776655", "email":"cuong@gmail.com", "address":"Đà Nẵng"}` |
| **Tạo đặt phòng** | `POST` | `http://localhost:8080/api/bookings` | `{"customerId":1, "roomId":1, "checkinDate":"2026-10-10", "checkoutDate":"2026-10-12", "note":"Đến nhận phòng lúc 14h"}` |
| **Check-in** | `PUT` | `http://localhost:8080/api/checkin/4` | Không cần body |
| **Xem trước hóa đơn**| `GET` | `http://localhost:8080/api/checkout/preview/2` | Không cần body |
| **Check-out** | `POST` | `http://localhost:8080/api/checkout/2` | `{"paymentMethod":"Chuyển khoản"}` |
| **Thêm dịch vụ** | `POST` | `http://localhost:8080/api/services` | `{"serviceName":"Bữa tối BBQ", "unitPrice":250000, "status":"Hoạt động"}` |

---


<a id="legacy-dai-h49"></a>

#### 7. CẨM NANG BẮT BỆNH VÀ XỬ LÝ LỖI (TROUBLESHOOTING)

Trong quá trình thực tập, chắc chắn bạn sẽ gặp một số lỗi. Hãy bình tĩnh tra cứu bảng dưới đây:


<a id="legacy-dai-h50"></a>

##### Lỗi 1: `Login failed for user 'sa'`
- **Nguyên nhân:** Sai mật khẩu của tài khoản `sa` trong file `.env`.
- **Cách xử lý:**
  1. Mở SSMS, đăng nhập thử bằng user `sa` và mật khẩu để chắc chắn mật khẩu đúng.
  2. Mở file `.env`, kiểm tra dòng `DB_PASSWORD=...` và đổi lại cho đúng.
  3. Khởi động lại ứng dụng Spring Boot.


<a id="legacy-dai-h51"></a>

##### Lỗi 2: `Connection refused: connect` hoặc `The TCP/IP connection to the host localhost, port 1433 has failed`
- **Nguyên nhân:** Cổng 1433 của SQL Server chưa được bật, hoặc dịch vụ SQL Server chưa khởi chạy.
- **Cách xử lý:**
  1. Bấm phím `Windows + R`, gõ `services.msc` -> Tìm `SQL Server (MSSQLSERVER)` -> Bấm **Start**.
  2. Bấm phím Windows, tìm phần mềm **SQL Server Configuration Manager**.
  3. Chọn mục **SQL Server Network Configuration** -> **Protocols for MSSQLSERVER**.
  4. Đảm bảo trạng thái của **TCP/IP** là **Enabled** (chuột phải chọn Enable).
  5. Khởi động lại dịch vụ SQL Server.


<a id="legacy-dai-h52"></a>

##### Lỗi 3: `Port 8080 was already in use`
- **Nguyên nhân:** Có một tiến trình Java hoặc ứng dụng khác đang chiếm cổng 8080.
- **Cách xử lý:**
  1. Mở PowerShell gõ lệnh tìm tiến trình:
     ```powershell
     netstat -ano | findstr :8080
     ```
  2. Xem số PID ở cột cuối cùng (ví dụ `12345`).
  3. Gõ lệnh để tắt tiến trình đó:
     ```powershell
     taskkill /PID 12345 /F
     ```
  4. Sau đó chạy lại Spring Boot.


<a id="legacy-dai-h53"></a>

##### Lỗi 4: Tiếng Việt bị hiển thị dấu hỏi chấm `???`
- **Nguyên nhân:** Truy vấn SQL thiếu tiền tố `N` (viết tắt của National Unicode) đối với các chuỗi tiếng Việt trong SQL Server.
- **Cách xử lý:** Luôn sử dụng `PreparedStatement.setString(index, value)` (driver JDBC sẽ tự động mã hóa Unicode), hoặc nếu viết SQL cứng thì phải có tiền tố `N`, ví dụ: `N'Trống'`, `N'Đang sử dụng'`.


<a id="legacy-dai-h54"></a>

##### Lỗi 5: `Cannot delete or update a parent row: a foreign key constraint fails`
- **Nguyên nhân:** Cố gắng xóa một phòng hoặc khách hàng đang có dữ liệu liên kết ở bảng `Bookings`.
- **Cách xử lý:** Trong code đã có logic kiểm tra trước. Nếu cần xóa để test, hãy xóa booking liên kết trước hoặc tạo một dữ liệu mới tinh để test xóa.

---


<a id="legacy-dai-h55"></a>

#### 8. BÍ QUYẾT BẢO VỆ VÀ BÁO CÁO THỰC TẬP THÀNH CÔNG

Khi báo cáo đồ án / thực tập trước hội đồng hoặc Mentor doanh nghiệp, bạn nên tự tin trình bày theo cấu trúc sau:

1. **Giới thiệu tổng quan hệ thống:** Trình bày mục tiêu của phần mềm quản lý Homestay, giải quyết bài toán đặt phòng, lưu trú, phát sinh dịch vụ và thanh toán tự động.
2. **Kiến trúc phân tầng (Architecture):**
   - Tách biệt rõ ràng giữa Frontend (HTML/CSS thuần + Fetch API không phụ thuộc framework nặng nề) và Backend (Spring Boot RESTful API).
   - Mô hình dữ liệu quan hệ (RDBMS) chuẩn hóa trên Microsoft SQL Server với 7 thực thể.
3. **Nghiệp vụ trọng tâm (Core Business Logic):**
   - Trình bày trực tiếp quy trình State Machine của phòng: `Trống` -> `Đã đặt` (Confirmed) -> `Đang sử dụng` (CheckedIn) -> `Trống` (CheckedOut & Lập hóa đơn thanh toán).
   - Giải thích cách quản lý Transaction để đảm bảo tính toàn vẹn dữ liệu kế toán khi khách trả phòng.
4. **Bảo mật và Tối ưu:**
   - Phòng chống SQL Injection bằng `PreparedStatement`.
   - Quản lý tài nguyên bộ nhớ bằng `try-with-resources`.
   - Xử lý lỗi tập trung, trả về định dạng JSON thống nhất `{ "success": boolean, "message": string }`.

---
*Tài liệu được biên soạn dành riêng cho quá trình thực tập dự án Homestay Management System.*

---


<a id="legacy-dai-h56"></a>

#### 9. NHẬT KÝ TIẾN ĐỘ THỰC TẬP (CẬP NHẬT GẦN NHẤT)

**Ngày hoàn thành: 03/10/2026**
- **Sửa file:** `frontend/pages/rooms.html`
  - Sửa lỗi thiếu thẻ `<script>` import `api.js` (do sơ suất quên include trước đó).
- **Hoàn thiện file:** `frontend/js/rooms.js`
  - Tích hợp thành công API lấy danh sách phòng (`GET /api/rooms`).
  - Tích hợp API Thêm phòng mới (`POST /api/rooms`) và Cập nhật phòng (`PUT /api/rooms/{id}`).
  - Tích hợp API Xóa phòng (`DELETE /api/rooms/{id}`).
  - Làm tính năng filter/tìm kiếm phòng theo mã và tên ở phía Client.
  - Xử lý các logic hiển thị lỗi từ Backend thông qua `showAlert` (ví dụ: bắt lỗi trùng mã phòng hoặc phòng đang dính khóa ngoại không cho xóa).

**Ngày hoàn thành: 04/10/2026**
- **Sửa file:** `frontend/pages/customers.html`
  - Bổ sung thẻ `<script>` import `api.js` bị thiếu.
- **Hoàn thiện file:** `frontend/js/customers.js`
  - Tích hợp API lấy danh sách khách hàng (`GET /api/customers`).
  - Tích hợp API Thêm mới và Sửa khách hàng (`POST`, `PUT`), có xử lý báo lỗi khi bị trùng số CCCD/Passport từ Backend.
  - Tích hợp API Xóa khách hàng, hiển thị lỗi nếu khách hàng đã có lịch sử đặt phòng.
  - Làm tính năng tìm kiếm khách hàng nhanh theo Tên, CCCD hoặc Số điện thoại.

**Ngày hoàn thành: 10/10/2026**
- **Sửa file:** `frontend/pages/*.html`
  - Bổ sung thẻ `<script>` import `api.js` bị thiếu trên toàn bộ các trang (bookings, checkin, checkout, services, statistics).
- **Hoàn thiện các file Frontend còn lại:**
  - `frontend/js/services.js`: Đã code xong CRUD Dịch vụ (thêm, sửa, xóa, lấy danh sách).
  - `frontend/js/bookings.js`: Code chức năng lấy ds khách, ds phòng trống, tự động tính tiền dự kiến khi chọn phòng/ngày, và API đặt phòng.
  - `frontend/js/checkin.js`: Lấy các booking `Confirmed` và thực hiện Check-in cập nhật trạng thái phòng.
  - `frontend/js/checkout.js`: Lấy các khách đang lưu trú `CheckedIn`, tải hóa đơn tạm tính và xuất hóa đơn giải phóng phòng.
  - `frontend/js/statistics.js`: Hiển thị số lượng phòng/khách, thống kê doanh thu tức thời (Dashboard) và báo cáo doanh thu theo tháng/năm.

👉 **KẾT LUẬN: Đã hoàn thành 100% tất cả các tính năng của dự án!**

**Ngày hoàn thành: Cập nhật sửa lỗi (10/10/2026)**
- **Sửa file:** `frontend/pages/login.html`
  - Bổ sung thẻ `<script src="../js/api.js"></script>` do Sỹ Đại quên include, gây lỗi hàm `apiPost` không tồn tại khi bấm Đăng nhập.
- **Troubleshooting Database:**
  - Hỗ trợ sửa cấu hình `.env` cho khớp với mật khẩu `sa` thực tế của Database.
  - Xử lý vấn đề TCP/IP bị disable trên bản SQLEXPRESS mặc định, hướng dẫn chi tiết cách mở port 1433 để Java có thể kết nối được.

---

<a id="legacy-team"></a>

## Hướng dẫn phân công từng người gốc

<a id="legacy-team-h1"></a>

### 📚 HƯỚNG DẪN LÀM BÀI THEO TỪNG NGƯỜI

> Mỗi người đọc **đúng phần của mình**. Làm theo thứ tự từng bước, không bỏ qua.

---


<a id="legacy-team-h2"></a>

### 👤 LONG – Database (Rooms, Bookings, Services, Booking_Services)


<a id="legacy-team-h3"></a>

#### Công cụ cần cài
- SQL Server (đã có)
- SQL Server Management Studio (SSMS)


<a id="legacy-team-h4"></a>

#### Nhiệm vụ của Long
1. Hoàn thiện `schema.sql` – các bảng **Rooms, Bookings, Services, Booking_Services**
2. Hoàn thiện `seed_data.sql` – dữ liệu mẫu cho các bảng đó
3. Viết SQL query cho **Thống kê phòng + Booking** (cung cấp cho Đức Đại dùng)

---


<a id="legacy-team-h5"></a>

#### BƯỚC 1 – Chạy Database

1. Mở **SSMS**, kết nối vào SQL Server
2. Mở file `database/schema.sql`, bôi đen tất cả → **F5** để chạy
3. Mở file `database/seed_data.sql`, bôi đen tất cả → **F5** để chạy
4. Kiểm tra bằng cách chạy:
```sql
USE HomestayDB;
SELECT * FROM Rooms;
SELECT * FROM Customers;
```
Nếu thấy dữ liệu là thành công ✅

---


<a id="legacy-team-h6"></a>

#### BƯỚC 2 – Viết SQL Query Thống Kê (cung cấp cho Đức Đại)

Mở SSMS, test từng query rồi **copy kết quả gửi cho Đức Đại** để Đức Đại dùng trong Java.


<a id="legacy-team-h7"></a>

##### Query 1 – Tổng số phòng theo trạng thái
```sql
USE HomestayDB;

SELECT
    COUNT(*) AS total_rooms,
    SUM(CASE WHEN status = N'Trống'          THEN 1 ELSE 0 END) AS available,
    SUM(CASE WHEN status = N'Đã đặt'         THEN 1 ELSE 0 END) AS booked,
    SUM(CASE WHEN status = N'Đang sử dụng'   THEN 1 ELSE 0 END) AS occupied,
    SUM(CASE WHEN status = N'Bảo trì'        THEN 1 ELSE 0 END) AS maintenance
FROM Rooms;
```


<a id="legacy-team-h8"></a>

##### Query 2 – Tổng booking tháng này
```sql
SELECT COUNT(*) AS total_bookings_this_month
FROM Bookings
WHERE MONTH(created_at) = MONTH(GETDATE())
  AND YEAR(created_at)  = YEAR(GETDATE());
```


<a id="legacy-team-h9"></a>

##### Query 3 – Thống kê phòng được đặt nhiều nhất
```sql
SELECT
    r.room_code,
    r.room_name,
    COUNT(b.booking_id) AS total_bookings
FROM Rooms r
LEFT JOIN Bookings b ON r.room_id = b.room_id
GROUP BY r.room_id, r.room_code, r.room_name
ORDER BY total_bookings DESC;
```

---


<a id="legacy-team-h10"></a>

#### BƯỚC 3 – Push Code Lên GitHub

```bash
git add .
git commit -m "Long: Hoan thien schema va seed data"
git push
```

---
---


<a id="legacy-team-h11"></a>

### 👤 HƯNG – Database (Users, Customers, Payments) + Thống kê doanh thu


<a id="legacy-team-h12"></a>

#### Công cụ cần cài
- SQL Server + SSMS


<a id="legacy-team-h13"></a>

#### Nhiệm vụ của Hưng
1. Kiểm tra bảng **Users, Customers, Payments** trong `schema.sql`
2. Thêm/chỉnh dữ liệu mẫu trong `seed_data.sql`
3. Viết SQL query **Thống kê doanh thu + Khách hàng** (cung cấp cho Đức Đại)
4. Hỗ trợ viết báo cáo thực tập

---


<a id="legacy-team-h14"></a>

#### BƯỚC 1 – Kiểm Tra Database (Giống Long, chạy schema.sql + seed_data.sql)

---


<a id="legacy-team-h15"></a>

#### BƯỚC 2 – Viết SQL Query Thống Kê Doanh Thu


<a id="legacy-team-h16"></a>

##### Query 1 – Tổng doanh thu tháng này
```sql
SELECT
    COUNT(payment_id)    AS total_invoices,
    SUM(grand_total)     AS total_revenue
FROM Payments
WHERE MONTH(paid_at) = MONTH(GETDATE())
  AND YEAR(paid_at)  = YEAR(GETDATE())
  AND payment_status = N'Đã thanh toán';
```


<a id="legacy-team-h17"></a>

##### Query 2 – Doanh thu theo tháng/năm (Đức Đại sẽ truyền month, year vào)
```sql
-- Thay ? bằng số tháng và năm cụ thể khi test
SELECT
    p.payment_id,
    c.full_name     AS customer_name,
    r.room_code,
    p.room_total,
    p.service_total,
    p.grand_total,
    p.payment_method,
    p.paid_at
FROM Payments p
JOIN Bookings  b ON p.booking_id  = b.booking_id
JOIN Customers c ON b.customer_id = c.customer_id
JOIN Rooms     r ON b.room_id     = r.room_id
WHERE MONTH(p.paid_at) = 9    -- doi so nay
  AND YEAR(p.paid_at)  = 2026  -- doi so nay
ORDER BY p.paid_at DESC;
```


<a id="legacy-team-h18"></a>

##### Query 3 – Tổng số khách hàng
```sql
SELECT COUNT(*) AS total_customers FROM Customers;
```


<a id="legacy-team-h19"></a>

##### Query 4 – Top khách hàng đặt nhiều nhất
```sql
SELECT TOP 5
    c.full_name,
    c.phone,
    COUNT(b.booking_id) AS total_bookings
FROM Customers c
JOIN Bookings b ON c.customer_id = b.customer_id
GROUP BY c.customer_id, c.full_name, c.phone
ORDER BY total_bookings DESC;
```

---


<a id="legacy-team-h20"></a>

#### BƯỚC 3 – Push Code
```bash
git add .
git commit -m "Hung: Them query thong ke doanh thu"
git push
```

---
---


<a id="legacy-team-h21"></a>

### 👤 SỸ ĐẠI – Frontend HTML + CSS


<a id="legacy-team-h22"></a>

#### Công cụ cần cài
- VS Code
- Extension: **Live Server** (để xem trang trực tiếp)


<a id="legacy-team-h23"></a>

#### Nhiệm vụ của Sỹ Đại
Làm HTML + CSS cho **8 trang** trong `frontend/pages/`. Không cần đụng vào JS.

---


<a id="legacy-team-h24"></a>

#### BƯỚC 1 – Clone và Mở Dự Án

```bash
git clone https://github.com/DoDucDai/Homestay.git
```

Mở VS Code → File → Open Folder → chọn thư mục `Homestay`

---


<a id="legacy-team-h25"></a>

#### BƯỚC 2 – Hiểu Cấu Trúc HTML Có Sẵn

Mỗi trang HTML đã có **khung sườn sẵn** gồm:
- Sidebar (menu trái)
- `main-content` (nội dung chính)
- Bảng dữ liệu
- Modal (hộp thoại)

**Sỹ Đại chỉ cần:** Làm cho nó trông đẹp hơn, thêm CSS nếu cần, KHÔNG xóa các `id` có sẵn vì JS dùng đến.

---


<a id="legacy-team-h26"></a>

#### BƯỚC 3 – Các Class CSS Đã Có Sẵn Trong `style.css`

```html
<!-- Layout -->
<div class="wrapper">
<nav class="sidebar">
<div class="main-content">
<div class="card">
<h1 class="page-title">

<!-- Nút bấm -->
<button class="btn btn-success">Xanh lá</button>
<button class="btn btn-primary">Xanh dương</button>
<button class="btn btn-warning">Vàng - Sửa</button>
<button class="btn btn-danger">Đỏ - Xóa</button>
<button class="btn btn-secondary">Xám - Hủy</button>

<!-- Badge trạng thái phòng -->
<span class="badge badge-available">Trống</span>
<span class="badge badge-booked">Đã đặt</span>
<span class="badge badge-occupied">Đang sử dụng</span>
<span class="badge badge-maintenance">Bảo trì</span>

<!-- Thông báo (JS tự điều khiển) -->
<div id="alert-message" class="alert" style="display:none;"></div>

<!-- Form -->
<div class="form-group">
  <label for="ten-input">Nhãn *</label>
  <input type="text" id="ten-input" placeholder="Nhập...">
</div>
```

---


<a id="legacy-team-h27"></a>

#### BƯỚC 4 – Làm Từng Trang Theo Thứ Tự


<a id="legacy-team-h28"></a>

##### Trang 1: `login.html` ✅ Đã có khung
- Không cần sửa nhiều, chỉ làm đẹp hơn nếu muốn


<a id="legacy-team-h29"></a>

##### Trang 2: `rooms.html` ← LÀM TRƯỚC TIÊN (module mẫu)
Kiểm tra đủ các phần:
```html
<!-- 1. Nút thêm phòng -->
<button class="btn btn-success" onclick="openAddModal()">+ Thêm phòng</button>

<!-- 2. Ô tìm kiếm -->
<input type="text" id="search-input" placeholder="Tìm kiếm...">

<!-- 3. Thông báo -->
<div id="alert-message" class="alert" style="display:none;"></div>

<!-- 4. Bảng - tbody có id để JS điền dữ liệu vào -->
<table>
  <thead>...</thead>
  <tbody id="rooms-tbody"></tbody>   ← QUAN TRỌNG: phải có id này
</table>

<!-- 5. Modal thêm/sửa - có id để JS mở/đóng -->
<div class="modal-overlay" id="room-modal">
  ...
  <input type="hidden" id="room-id">   ← Lưu id khi sửa
  <input type="text" id="room-code">
  ...
</div>
```


<a id="legacy-team-h30"></a>

##### Trang 3-8: `customers.html`, `bookings.html`, `checkin.html`, `checkout.html`, `services.html`, `statistics.html`
Tương tự `rooms.html`, chỉ thay nội dung phù hợp với module.

---


<a id="legacy-team-h31"></a>

#### BƯỚC 5 – Xem Trực Tiếp Trên Trình Duyệt

1. Chuột phải vào file `login.html`
2. Chọn **"Open with Live Server"**
3. Trình duyệt sẽ tự mở và cập nhật khi bạn lưu file

---


<a id="legacy-team-h32"></a>

#### BƯỚC 6 – Push Code
```bash
git pull                              # Lấy code mới nhất trước
git add frontend/
git commit -m "Sy Dai: Hoan thien giao dien trang rooms va customers"
git push
```

> ⚠️ **Lưu ý:** Không xóa bất kỳ `id` nào trong HTML. JS dùng các `id` đó để tìm và cập nhật nội dung.

---
---


<a id="legacy-team-h33"></a>

### 👤 ĐỨC ĐẠI (LEADER) – Backend Java & Toàn bộ Frontend JS


<a id="legacy-team-h34"></a>

#### Công cụ cần cài
- IntelliJ IDEA (hoặc VS Code + Extension Pack for Java)
- JDK 17
- Maven


<a id="legacy-team-h35"></a>

#### Nhiệm vụ của Đức Đại (Leader)
- **Backend:** Code tất cả API trong thư mục `controller/` (Login, Room, Customer, Checkin, Checkout, Services, Statistics).
- **Frontend:** Code toàn bộ file `.js` trong `frontend/js/`.
- **Tích hợp:** Hỗ trợ các bạn test, ghép nối các phần.

---


<a id="legacy-team-h36"></a>

#### BƯỚC 1 – Chuẩn Bị

```bash
git clone https://github.com/DoDucDai/Homestay.git
cd Homestay

# Tao file .env
copy .env.example .env
# Mo file .env, doi DB_PASSWORD thanh mat khau SQL Server cua may ban
```

---


<a id="legacy-team-h37"></a>

#### BƯỚC 2 – Mở Project Trong IntelliJ

1. Mở IntelliJ IDEA
2. File → Open → chọn thư mục `backend/`
3. Chờ IntelliJ tải Maven dependencies (lần đầu mất 2-3 phút)
4. Chạy `HomestayApplication.java` → chuột phải → **Run**
5. Nếu thấy `Started HomestayApplication` là thành công ✅

---


<a id="legacy-team-h38"></a>

#### BƯỚC 3 – Cách Viết Code Trong Controller


<a id="legacy-team-h39"></a>

##### Template chuẩn cho mọi hàm:

```java
@GetMapping
public List<Map<String, Object>> getAllRooms() {
    List<Map<String, Object>> result = new ArrayList<>();

    String sql = "SELECT * FROM Rooms ORDER BY room_code";

    try (Connection conn = DBConnection.getConnection();
         PreparedStatement stmt = conn.prepareStatement(sql);
         ResultSet rs = stmt.executeQuery()) {

        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("roomId",   rs.getInt("room_id"));
            row.put("roomCode", rs.getString("room_code"));
            row.put("roomName", rs.getString("room_name"));
            row.put("price",    rs.getLong("price"));
            row.put("status",   rs.getString("status"));
            result.add(row);
        }

    } catch (SQLException e) {
        e.printStackTrace();
    }

    return result;
}
```


<a id="legacy-team-h40"></a>

##### Template INSERT (thêm mới):

```java
@PostMapping
public Map<String, Object> createRoom(@RequestBody Map<String, Object> body) {
    String roomCode = (String) body.get("roomCode");
    String roomName = (String) body.get("roomName");
    int    price    = (int) body.get("price");

    String sql = "INSERT INTO Rooms (room_code, room_name, price) VALUES (?, ?, ?)";

    try (Connection conn = DBConnection.getConnection();
         PreparedStatement stmt = conn.prepareStatement(sql)) {

        stmt.setString(1, roomCode);
        stmt.setString(2, roomName);
        stmt.setInt(3, price);
        stmt.executeUpdate();

        return Map.of("success", true, "message", "Them phong thanh cong");

    } catch (SQLException e) {
        e.printStackTrace();
        return Map.of("success", false, "message", e.getMessage());
    }
}
```


<a id="legacy-team-h41"></a>

##### Template UPDATE (sửa):

```java
@PutMapping("/{id}")
public Map<String, Object> updateRoom(@PathVariable int id, @RequestBody Map<String, Object> body) {
    String roomName = (String) body.get("roomName");
    int    price    = (int) body.get("price");

    String sql = "UPDATE Rooms SET room_name=?, price=? WHERE room_id=?";

    try (Connection conn = DBConnection.getConnection();
         PreparedStatement stmt = conn.prepareStatement(sql)) {

        stmt.setString(1, roomName);
        stmt.setInt(2, price);
        stmt.setInt(3, id);
        stmt.executeUpdate();

        return Map.of("success", true);

    } catch (SQLException e) {
        e.printStackTrace();
        return Map.of("success", false, "message", e.getMessage());
    }
}
```


<a id="legacy-team-h42"></a>

##### Template DELETE (xóa):

```java
@DeleteMapping("/{id}")
public Map<String, Object> deleteRoom(@PathVariable int id) {
    String sql = "DELETE FROM Rooms WHERE room_id=?";

    try (Connection conn = DBConnection.getConnection();
         PreparedStatement stmt = conn.prepareStatement(sql)) {

        stmt.setInt(1, id);
        stmt.executeUpdate();
        return Map.of("success", true);

    } catch (SQLException e) {
        e.printStackTrace();
        return Map.of("success", false, "message", e.getMessage());
    }
}
```

---


<a id="legacy-team-h43"></a>

#### BƯỚC 4 – Test API Bằng Postman (hoặc trình duyệt)

Sau khi chạy backend, test từng API:

```
# Test GET rooms (dán thẳng vào trình duyệt)
http://localhost:8080/api/rooms

# Test POST (dùng Postman)
POST http://localhost:8080/api/rooms
Body (JSON): { "roomCode": "P999", "roomName": "Test", "price": 100000 }
```

---


<a id="legacy-team-h44"></a>

#### BƯỚC 5 – Thứ Tự Làm Các Controller

```
1. AuthController.java     → login (LÀM TRƯỚC TIÊN để test đăng nhập)
2. RoomController.java     → CRUD phòng (module mẫu)
3. CustomerController.java → CRUD khách hàng
4. BookingController.java  → Đặt phòng, checkin, checkout
5. ServiceStatController.java → CRUD services + gọi query của Long/Hưng
```

---


<a id="legacy-team-h45"></a>

#### BƯỚC 6 – Code Backend Phần Booking

File: `BookingController.java`


<a id="legacy-team-h46"></a>

##### Hàm `getBookings` – Lấy danh sách booking
```java
@GetMapping("/bookings")
public List<Map<String, Object>> getBookings(@RequestParam(required = false) String status) {
    List<Map<String, Object>> result = new ArrayList<>();

    String sql = """
        SELECT b.booking_id, b.checkin_date, b.checkout_date, b.status, b.note,
               c.full_name AS customer_name, c.phone,
               r.room_code, r.room_name, r.price
        FROM Bookings b
        JOIN Customers c ON b.customer_id = c.customer_id
        JOIN Rooms r ON b.room_id = r.room_id
        """;

    if (status != null && !status.isEmpty()) {
        sql += " WHERE b.status = ?";
    }
    sql += " ORDER BY b.created_at DESC";

    try (Connection conn = DBConnection.getConnection();
         PreparedStatement stmt = conn.prepareStatement(sql)) {

        if (status != null && !status.isEmpty()) {
            stmt.setString(1, status);
        }

        ResultSet rs = stmt.executeQuery();
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("bookingId",    rs.getInt("booking_id"));
            row.put("customerName", rs.getString("customer_name"));
            row.put("phone",        rs.getString("phone"));
            row.put("roomCode",     rs.getString("room_code"));
            row.put("roomName",     rs.getString("room_name"));
            row.put("price",        rs.getLong("price"));
            row.put("checkinDate",  rs.getString("checkin_date"));
            row.put("checkoutDate", rs.getString("checkout_date"));
            row.put("status",       rs.getString("status"));
            row.put("note",         rs.getString("note"));
            result.add(row);
        }
    } catch (SQLException e) { e.printStackTrace(); }

    return result;
}
```


<a id="legacy-team-h47"></a>

##### Hàm `createBooking` – Tạo booking mới
```java
@PostMapping("/bookings")
public Map<String, Object> createBooking(@RequestBody Map<String, Object> body) {
    int    customerId    = (int) body.get("customerId");
    int    roomId        = (int) body.get("roomId");
    String checkinDate   = (String) body.get("checkinDate");
    String checkoutDate  = (String) body.get("checkoutDate");
    String note          = (String) body.getOrDefault("note", "");

    try (Connection conn = DBConnection.getConnection()) {

        // 1. Kiem tra phong co trong khong
        String checkSql = "SELECT status FROM Rooms WHERE room_id=?";
        PreparedStatement checkStmt = conn.prepareStatement(checkSql);
        checkStmt.setInt(1, roomId);
        ResultSet rs = checkStmt.executeQuery();
        if (rs.next() && !rs.getString("status").equals("Trống")) {
            return Map.of("success", false, "message", "Phong nay khong con trong!");
        }

        // 2. Tao booking
        String insertSql = "INSERT INTO Bookings (customer_id, room_id, checkin_date, checkout_date, note) VALUES (?,?,?,?,?)";
        PreparedStatement insertStmt = conn.prepareStatement(insertSql);
        insertStmt.setInt(1, customerId);
        insertStmt.setInt(2, roomId);
        insertStmt.setString(3, checkinDate);
        insertStmt.setString(4, checkoutDate);
        insertStmt.setString(5, note);
        insertStmt.executeUpdate();

        // 3. Cap nhat trang thai phong
        String updateSql = "UPDATE Rooms SET status=N'Đã đặt' WHERE room_id=?";
        PreparedStatement updateStmt = conn.prepareStatement(updateSql);
        updateStmt.setInt(1, roomId);
        updateStmt.executeUpdate();

        return Map.of("success", true, "message", "Tao booking thanh cong");

    } catch (SQLException e) {
        e.printStackTrace();
        return Map.of("success", false, "message", e.getMessage());
    }
}
```

---


<a id="legacy-team-h48"></a>

#### BƯỚC 7 – Code Backend Phần Checkout

```java
@PostMapping("/checkout/{bookingId}")
public Map<String, Object> checkOut(@PathVariable int bookingId, @RequestBody Map<String, Object> body) {
    String paymentMethod = (String) body.getOrDefault("paymentMethod", "Tiền mặt");

    try (Connection conn = DBConnection.getConnection()) {

        // 1. Lay thong tin booking va phong
        String infoSql = """
            SELECT b.checkin_date, b.checkout_date, b.room_id, r.price
            FROM Bookings b JOIN Rooms r ON b.room_id = r.room_id
            WHERE b.booking_id = ?
            """;
        PreparedStatement infoStmt = conn.prepareStatement(infoSql);
        infoStmt.setInt(1, bookingId);
        ResultSet rs = infoStmt.executeQuery();

        if (!rs.next()) return Map.of("success", false, "message", "Khong tim thay booking");

        long   price        = rs.getLong("price");
        int    roomId       = rs.getInt("room_id");
        String checkinDate  = rs.getString("checkin_date");
        String checkoutDate = rs.getString("checkout_date");

        // 2. Tinh so dem va tien phong
        long nights    = java.time.temporal.ChronoUnit.DAYS.between(
                            java.time.LocalDate.parse(checkinDate),
                            java.time.LocalDate.parse(checkoutDate));
        long roomTotal = price * nights;

        // 3. Tinh tien dich vu
        String svcSql  = "SELECT ISNULL(SUM(total_price),0) AS svc FROM Booking_Services WHERE booking_id=?";
        PreparedStatement svcStmt = conn.prepareStatement(svcSql);
        svcStmt.setInt(1, bookingId);
        ResultSet svcRs   = svcStmt.executeQuery();
        long serviceTotal = svcRs.next() ? svcRs.getLong("svc") : 0;
        long grandTotal   = roomTotal + serviceTotal;

        // 4. Tao hoa don
        String paymentSql = "INSERT INTO Payments (booking_id, room_total, service_total, grand_total, payment_method) VALUES (?,?,?,?,?)";
        PreparedStatement payStmt = conn.prepareStatement(paymentSql);
        payStmt.setInt(1, bookingId);
        payStmt.setLong(2, roomTotal);
        payStmt.setLong(3, serviceTotal);
        payStmt.setLong(4, grandTotal);
        payStmt.setString(5, paymentMethod);
        payStmt.executeUpdate();

        // 5. Cap nhat trang thai
        conn.prepareStatement("UPDATE Bookings SET status='CheckedOut' WHERE booking_id=" + bookingId).executeUpdate();
        conn.prepareStatement("UPDATE Rooms SET status=N'Trống' WHERE room_id=" + roomId).executeUpdate();

        return Map.of("success", true,
                      "roomTotal",     roomTotal,
                      "serviceTotal",  serviceTotal,
                      "grandTotal",    grandTotal,
                      "nights",        nights);

    } catch (Exception e) {
        e.printStackTrace();
        return Map.of("success", false, "message", e.getMessage());
    }
}
```

---


<a id="legacy-team-h49"></a>

#### BƯỚC 8 – Làm JavaScript


<a id="legacy-team-h50"></a>

##### Cấu trúc chuẩn cho mỗi file JS:

```javascript
// ==============================
// rooms.js
// ==============================

// Chạy khi trang load xong
document.addEventListener('DOMContentLoaded', () => {
    checkLogin();    // Kiểm tra đăng nhập
    loadRooms();     // Tải dữ liệu
});

// Load danh sách
async function loadRooms() {
    try {
        const rooms = await apiGet('/rooms');
        const tbody = document.getElementById('rooms-tbody');
        tbody.innerHTML = '';
        rooms.forEach(room => {
            tbody.innerHTML += `
                <tr>
                    <td>${room.roomCode}</td>
                    <td>${room.roomName}</td>
                    <td>${room.roomType}</td>
                    <td>${formatMoney(room.price)}</td>
                    <td><span class="badge badge-available">${room.status}</span></td>
                    <td>
                        <button class="btn btn-warning" onclick="openEditModal(${room.roomId})">Sửa</button>
                        <button class="btn btn-danger"  onclick="confirmDelete(${room.roomId})">Xóa</button>
                    </td>
                </tr>`;
        });
    } catch {
        showAlert('Lỗi tải dữ liệu!', 'error');
    }
}

// Mở modal thêm mới
function openAddModal() {
    document.getElementById('modal-title').textContent = 'Thêm Phòng Mới';
    document.getElementById('room-id').value   = '';
    document.getElementById('room-code').value = '';
    document.getElementById('room-name').value = '';
    document.getElementById('room-price').value = '';
    openModal('room-modal');
}

// Mở modal sửa
async function openEditModal(id) {
    const rooms = await apiGet('/rooms');
    const room  = rooms.find(r => r.roomId === id);
    document.getElementById('modal-title').textContent  = 'Sửa Phòng';
    document.getElementById('room-id').value    = room.roomId;
    document.getElementById('room-code').value  = room.roomCode;
    document.getElementById('room-name').value  = room.roomName;
    document.getElementById('room-price').value = room.price;
    openModal('room-modal');
}

// Lưu (thêm hoặc sửa)
async function saveRoom() {
    const id    = document.getElementById('room-id').value;
    const code  = document.getElementById('room-code').value.trim();
    const name  = document.getElementById('room-name').value.trim();
    const price = document.getElementById('room-price').value;

    if (!code || !name || !price) {
        showAlert('Vui lòng nhập đủ thông tin!', 'error');
        return;
    }

    const data   = { roomCode: code, roomName: name, price: Number(price) };
    const result = id
        ? await apiPut(`/rooms/${id}`, data)
        : await apiPost('/rooms', data);

    if (result.success) {
        showAlert(id ? 'Cập nhật thành công!' : 'Thêm thành công!', 'success');
        closeModal();
        loadRooms();
    } else {
        showAlert(result.message, 'error');
    }
}

// Xóa
async function confirmDelete(id) {
    if (!confirm('Bạn có chắc muốn xóa phòng này?')) return;
    const result = await apiDelete(`/rooms/${id}`);
    if (result.success) {
        showAlert('Đã xóa phòng!', 'success');
        loadRooms();
    }
}
```

---


<a id="legacy-team-h51"></a>

#### BƯỚC 9 – Thứ Tự Làm JS

```
1. api.js      ← Đã xong, không cần sửa
2. auth.js     ← Đã xong
3. rooms.js    ← Làm đầu tiên
4. customers.js← Tương tự rooms.js
5. bookings.js ← Đặt phòng
6. checkin.js  ← Check-in
7. checkout.js ← Trả phòng
8. services.js ← Dịch vụ
9. statistics.js ← Thống kê
```

---


<a id="legacy-team-h52"></a>

#### BƯỚC 10 – Push Code

```bash
git pull
git add .
git commit -m "Duc Dai (Leader): Hoan thien Backend va JS"
git push
```

---


<a id="legacy-team-h53"></a>

### ⚠️ QUY TẮC CHUNG CHO CẢ NHÓM

> Đọc và nhớ 5 điều này để tránh conflict code

1. **`git pull` trước khi bắt đầu code mỗi ngày** – tránh bị conflict
2. **Chỉ sửa file trong phần của mình** – không đụng vào file của người khác
3. **Commit thường xuyên** – sau mỗi hàm/trang làm xong, commit luôn
4. **Không xóa `id` trong HTML** – JS dùng các id đó để tìm element
5. **Test trước khi push** – chạy thử, kiểm tra không có lỗi rõ ràng

```bash
# Quy trình làm việc mỗi ngày
git pull                          # 1. Lấy code mới
# ... code ...
git add .                         # 2. Stage file
git commit -m "Mô tả việc làm"   # 3. Commit
git push                          # 4. Push lên
```

---

<a id="legacy-schedule"></a>

## Lịch trình thực tập gốc

<a id="legacy-schedule-h1"></a>

### 📅 LỊCH TRÌNH THỰC TẬP & PHÂN CÔNG CÔNG VIỆC THEO TUẦN

> **Mục tiêu dự án:** Hoàn thành hệ thống Quản lý Homestay đủ chức năng demo để báo cáo thực tập (Target: 8 điểm).
> **Thời gian dự kiến:** 4 tuần (có thể điều chỉnh tùy theo deadline của trường).

---


<a id="legacy-schedule-h2"></a>

#### TUẦN 1: KHỞI TẠO DỰ ÁN & CƠ SỞ DỮ LIỆU
**Mục tiêu:** Chạy được dự án trên máy tất cả thành viên, hoàn thiện database và giao diện (UI) cơ bản.

| Thành viên | Nhiệm vụ chi tiết trong Tuần 1 |
|---|---|
| **Cả nhóm** | - Clone code từ GitHub về máy.<br>- Cài đặt đủ tool: SQL Server, Java 17, IntelliJ, VS Code.<br>- Mở dự án lên, cấu hình file `.env` chạy thử để đảm bảo mọi máy đều start được backend. |
| **Long & Hưng** | - Chạy file `schema.sql` và `seed_data.sql` trên SQL Server.<br>- Thống nhất kiểu dữ liệu, xác nhận các bảng đã liên kết đúng (Foreign Key).<br>- Test thử các câu query thống kê cơ bản trong SQL Server. |
| **Sỹ Đại (FE)** | - Hoàn thiện UI/UX trang `login.html`.<br>- Hoàn thiện UI khung chuẩn (Sidebar, Navbar) cho trang `rooms.html` để làm mẫu. |
| **Đức Đại (Leader)**| - Code xong API Login (`AuthController.java`).<br>- Code xong các API cơ bản (GET, POST) cho phòng ở `RoomController.java`.<br>- Hỗ trợ các bạn setup ban đầu.<br>- Review code của Sỹ Đại.<br>- Viết Javascript (`auth.js`) để kết nối API Login với giao diện `login.html`. |

---


<a id="legacy-schedule-h3"></a>

#### TUẦN 2: HOÀN THIỆN CÁC MODULE CƠ BẢN (KHÁCH HÀNG & PHÒNG)
**Mục tiêu:** Làm xong hoàn toàn các chức năng Thêm, Sửa, Xóa, Xem (CRUD) cho Phòng và Khách hàng.

| Thành viên | Nhiệm vụ chi tiết trong Tuần 2 |
|---|---|
| **Long & Hưng** | - Viết xong toàn bộ các câu lệnh SQL query cho phần Thống kê theo hướng dẫn.<br>- Gửi các câu lệnh SQL cho Đức Đại.<br>- Bắt đầu viết dàn ý cho báo cáo thực tập (Phần khảo sát hiện trạng, biểu đồ Use Case). |
| **Sỹ Đại (FE)** | - Dựa vào khung chuẩn, làm tiếp giao diện cho các trang: `customers.html`, `services.html`.<br>- Đảm bảo các form nhập liệu, bảng danh sách hiển thị đẹp mắt. |
| **Đức Đại (Leader)**| - Hoàn thiện các API (PUT, DELETE) cho `RoomController.java`.<br>- Code toàn bộ các API (GET, POST, PUT, DELETE) cho `CustomerController.java` và `ServiceStatController.java` (phần Services).<br>- Viết Javascript (`rooms.js`) để gọi API, hiển thị danh sách phòng, xử lý thêm/sửa/xóa phòng.<br>- Viết Javascript (`customers.js` và `services.js`) để hoàn thiện chức năng cho trang Khách hàng và Dịch vụ. |

---


<a id="legacy-schedule-h4"></a>

#### TUẦN 3: MODULE CỐT LÕI (BOOKING, CHECK-IN, CHECK-OUT)
**Mục tiêu:** Hoàn thành quy trình nghiệp vụ chính của Homestay (Đặt phòng -> Nhận phòng -> Trả phòng & Thanh toán).

| Thành viên | Nhiệm vụ chi tiết trong Tuần 3 |
|---|---|
| **Long & Hưng** | - Hỗ trợ test lỗi (nhập dữ liệu vào form xem có lưu đúng vào DB không).<br>- Viết tiếp báo cáo thực tập (Phần Thiết kế cơ sở dữ liệu, vẽ sơ đồ ERD). |
| **Sỹ Đại (FE)** | - Hoàn thiện giao diện các trang: `bookings.html`, `checkin.html`, `checkout.html`.<br>- Chú ý thiết kế hóa đơn thanh toán ở trang checkout sao cho chuyên nghiệp. |
| **Đức Đại (Leader)**| - Code API Check-in (`PUT /api/checkin/{id}`) trong `BookingController.java`.<br>- Code API Tạo Booking và API Check-out (tính tiền) trong `BookingController.java` (phần logic phức tạp).<br>- Viết Javascript (`bookings.js`, `checkin.js`, `checkout.js`) để gọi API, xử lý nghiệp vụ đặt phòng và trả phòng.<br>- Hỗ trợ test các phần liên kết dữ liệu giữa các bảng. |

---


<a id="legacy-schedule-h5"></a>

#### TUẦN 4: THỐNG KÊ, TỔNG DUYỆT & VIẾT BÁO CÁO
**Mục tiêu:** Hoàn thiện nốt phần Thống kê, test lỗi toàn hệ thống, đóng gói source code và hoàn thành Báo cáo quyển.

| Thành viên | Nhiệm vụ chi tiết trong Tuần 4 |
|---|---|
| **Sỹ Đại (FE)** | - Hoàn thiện giao diện trang `statistics.html` (có thể dùng thư viện Chart.js để vẽ biểu đồ nếu muốn điểm cao hơn).<br>- Rà soát lại CSS toàn bộ hệ thống, chỉnh sửa các phần bị lệch. |
| **Đức Đại (Leader)**| - Dùng các câu query của Long và Hưng để viết API Thống kê (`ServiceStatController.java`).<br>- Viết Javascript (`statistics.js`) gọi API để hiển thị số liệu thống kê lên màn hình.<br>- Ghép nối toàn bộ hệ thống, test end-to-end (từ lúc tạo khách hàng -> đặt phòng -> check out -> xem thống kê).<br>- Sửa các lỗi lặt vặt (bugs) phát sinh khi test và fix các bug khó. |
| **Long & Hưng** | - Chụp ảnh màn hình các chức năng đã hoàn thiện.<br>- Tổng hợp báo cáo quyển, chuẩn bị slide thuyết trình (PowerPoint). |
| **Cả nhóm** | - Họp nhóm tổng duyệt: chạy thử dự án từ đầu đến cuối.<br>- Phân công người thuyết trình và người thao tác demo dự án khi báo cáo với thầy cô. |

---


<a id="legacy-schedule-h6"></a>

#### 📌 LƯU Ý KHI LÀM VIỆC:
- **Deadline hàng tuần:** Cuối mỗi tuần (Vd: Tối Chủ Nhật) cả nhóm sẽ họp 15-30 phút để review xem tiến độ đến đâu.
- **Quy tắc đẩy code:** Làm xong phần nào (dù nhỏ), commit và push lên GitHub ngay, đừng để dồn đến cuối tuần.
- **Nếu gặp khó khăn (Stuck):** Không ôm việc, sau 2-3 tiếng không sửa được lỗi thì nhắn lên nhóm nhờ Leader hoặc các bạn khác hỗ trợ ngay.


## Hưng — Kiểm tra khách hàng/dịch vụ (29/10/2026)

Đại/Codex đã bổ sung 15 ca Chrome thật theo yêu cầu sửa phần việc, đạt 15/15 trên database QA riêng. Xem [bảng bước/kỳ vọng/thực tế](contributions/hung/UI_TESTS.md). Hai CSV cuối nằm đúng trong contributions/hung, đủ 50 khách/12 dịch vụ; không giữ bản trùng ở gốc.

## Long — Dữ liệu và truy vấn kiểm tra (29/10/2026)

Đã hoàn thiện 20 phòng, 60 booking, 52 dòng dịch vụ, 40 payment theo ngày chốt 29/10/2026; 40 CheckedOut, 10 Cancelled, 6 Confirmed, 4 CheckedIn. Doanh thu 11/2025–10/2026 là 70.680.000 VNĐ. [Hướng dẫn, đối soát tháng, cách nhập và kết quả thật](contributions/README.md). Truy vấn chỉ đọc dùng giá snapshot và lọc đã thanh toán; 7 nhóm SQL/import đạt, gồm rollback và chạy lại không trùng.

HomestayDB trên máy Đại đã được sao lưu/kiểm tra phục hồi, nâng bằng migrations hiện có rồi nhập append-only. Dữ liệu cũ được giữ, hóa đơn legacy thiếu chi tiết có cảnh báo và không tự sửa tổng. Đây là hoàn thiện bởi Đại/Codex dựa trên các commit nguồn của Hưng và Long; không ghi nhận thay kết quả chưa chạy của hai bạn.
