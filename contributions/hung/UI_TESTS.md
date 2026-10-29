# Hưng — Kiểm tra khách hàng/dịch vụ

Thực hiện bổ sung bởi Đại/Codex ngày 29/10/2026 theo yêu cầu của Đại. Không ghi là Hưng đã tự chạy các ca này. Chrome thật, backend/JAR và database UUID riêng, có seed giả lập, trên giao diện local đang có.

Tài khoản demo admin và nhanvien; không đổi mật khẩu. Không thao tác ghi thử trên HomestayDB.

| Mã | Vai trò | Các bước | Mong đợi | Thực tế | Kết quả |
| --- | --- | --- | --- | --- | --- |
| H01 | admin | Mở Thêm khách hàng và lưu form rỗng | Không lưu; lỗi trường bắt buộc | Họ tên trống hoặc vượt 100 ký tự. | Đạt |
| H02 | admin | Nhập CCCD 123 và lưu | Không lưu; lỗi định dạng CCCD | CCCD cần 12 chữ số; hộ chiếu cần 6–20 chữ cái hoặc chữ số. | Đạt |
| H03 | admin | Nhập điện thoại 123 và lưu | Không lưu; lỗi 10 chữ số bắt đầu bằng 0 | Số điện thoại cần 10 chữ số, bắt đầu bằng 0. | Đạt |
| H04 | admin | Lưu khách CCCD hợp lệ, email/địa chỉ trống | Khách xuất hiện, thông tin tùy chọn báo chưa có | KH6	; Khách Kiểm Thử Ánh; Chưa có địa chỉ; 	CCCD: 099000000015	0900000015	Chưa có email	SửaLịch sử lưu trúXóa | Đạt |
| H05 | admin | Sửa email và địa chỉ KH6 | Danh sách hiển thị thông tin vừa sửa | KH6	; Khách Kiểm Thử Ánh; Địa chỉ giả lập kiểm thử; 	CCCD: 099000000015	0900000015	anh.qa@example.com	SửaLịch sử lưu trúXóa | Đạt |
| H06 | admin | Tìm khách bằng từ khóa Ánh | Hiển thị khách có tên có dấu | KH6	; Khách Kiểm Thử Ánh; Địa chỉ giả lập kiểm thử; 	CCCD: 099000000015	0900000015	anh.qa@example.com	SửaLịch sử lưu trúXóa | Đạt |
| H07 | admin | Tìm số giấy tờ 099000000015 | Hiển thị đúng KH6 | KH6	; Khách Kiểm Thử Ánh; Địa chỉ giả lập kiểm thử; 	CCCD: 099000000015	0900000015	anh.qa@example.com	SửaLịch sử lưu trúXóa | Đạt |
| H08 | admin | Tìm từ khóa không tồn tại | Danh sách rỗng có thông báo rõ | Không tìm thấy khách hàng phù hợp. Hãy thử tên, số giấy tờ hoặc số điện thoại khác. | Đạt |
| H09 | admin | Thêm khách dùng giấy tờ đã tồn tại | Bị từ chối, form giữ nguyên để sửa | Dữ liệu bị trùng với bản ghi đã có! | Đạt |
| H10 | admin | Thêm khách Hộ chiếu QA26102915, sau đó tìm giấy tờ | Hộ chiếu được lưu và tìm thấy | KH8	; Khách Hộ Chiếu QA; Chưa có địa chỉ; 	Hộ chiếu: QA26102915	0900000016	Chưa có email	SửaLịch sử lưu trúXóa | Đạt |
| H11 | admin | Lưu dịch vụ rỗng | Bị chặn và có lỗi tên/giá | Tên dịch vụ trống hoặc vượt 100 ký tự. | Đạt |
| H12 | admin | Lưu dịch vụ giá -1 | Bị chặn; đơn giá không âm | Đơn giá phải là số nguyên trong giới hạn cho phép. | Đạt |
| H13 | admin | Lưu dịch vụ QA15 giá 12000 | Thêm thành công và xuất hiện trong danh sách | DV6	Dịch vụ QA15	12.000 VNĐ	Hoạt động	SửaXóa | Đạt |
| H14 | admin | Sửa dịch vụ QA15 thành giá 15000 và trạng thái Ngừng | Giá mới và trạng thái Ngừng xuất hiện | DV6	Dịch vụ QA15	15.000 VNĐ	Ngừng	SửaXóa | Đạt |
| H15 | staff | Xem Dịch vụ và Khách hàng bằng nhanvien | Xem được dữ liệu; không có thêm/sửa/xóa dịch vụ hoặc xóa khách | {"services":{"add":0,"edit":0,"delete":0,"row":"DV6\tDịch vụ QA15\t15.000 VNĐ\tNgừng\t"},"customerDeleteButtons":0} | Đạt |

Kết quả: 15/15 đạt. Không phát hiện lỗi ứng dụng trong phạm vi này. Chờ selector của công cụ đôi lúc hết hạn trước khi response về; các trường hợp đó đã được đọc lại DOM/danh sách để xác nhận, không đánh dấu dựa trên việc chỉ bấm nút.
