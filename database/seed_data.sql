-- ============================================
-- seed_data.sql - DU LIEU MAU DE DEMO
-- NGUOI LAM: LONG + HUNG
-- Chay sau khi da chay schema.sql
-- Tool: SQL Server Management Studio (SSMS)
-- ============================================

USE HomestayDB;
GO

-- ============================================
-- DU LIEU: USERS (Tai khoan)
-- NGUOI LAM: HUNG
-- ============================================
INSERT INTO Users (username, password, full_name, role) VALUES
('admin',    '123456', N'Nguyễn Văn Admin',  'admin'),
('nhanvien', '123456', N'Trần Thị Nhân Viên', 'staff');

-- ============================================
-- DU LIEU: ROOMS (Phong)
-- NGUOI LAM: LONG
-- ============================================
INSERT INTO Rooms (room_code, room_name, room_type, price, status, description) VALUES
('P101', N'Phòng Đơn Tầng 1',       'Single', 300000,  N'Trống',          N'Phòng đơn thoáng mát, có ban công'),
('P102', N'Phòng Đôi Tầng 1',       'Double', 500000,  N'Trống',          N'Phòng đôi với 2 giường đơn'),
('P201', N'Phòng VIP Tầng 2',       'VIP',    900000,  N'Trống',          N'Phòng VIP cao cấp, view đẹp'),
('P202', N'Phòng Gia Đình Tầng 2',  'Family', 1200000, N'Trống',          N'Phòng rộng cho cả gia đình'),
('P301', N'Phòng Đơn Tầng 3',       'Single', 350000,  N'Đang sử dụng',   N'Phòng đơn tầng 3, view núi'),
('P302', N'Phòng Đôi Tầng 3',       'Double', 550000,  N'Bảo trì',        N'Đang sửa chữa');

-- ============================================
-- DU LIEU: CUSTOMERS (Khach hang)
-- NGUOI LAM: HUNG
-- ============================================
INSERT INTO Customers (full_name, cccd, phone, email, address) VALUES
(N'Nguyễn Văn An',     '001099001234', '0901234567', 'an@email.com',    N'Hà Nội'),
(N'Trần Thị Bình',     '001099005678', '0912345678', 'binh@email.com',  N'TP. Hồ Chí Minh'),
(N'Lê Hoàng Cường',    '001099009012', '0923456789', 'cuong@email.com', N'Đà Nẵng'),
(N'Phạm Thị Dung',     '001099003456', '0934567890', 'dung@email.com',  N'Huế'),
(N'Hoàng Minh Đức',    '001099007890', '0945678901', 'duc@email.com',   N'Nha Trang');

-- ============================================
-- DU LIEU: SERVICES (Dich vu)
-- NGUOI LAM: LONG
-- ============================================
INSERT INTO Services (service_name, unit_price, status) VALUES
(N'Thuê xe máy',    150000, N'Hoạt động'),
(N'Giặt ủi',        50000,  N'Hoạt động'),
(N'Đưa đón sân bay',200000, N'Hoạt động'),
(N'Ăn sáng',        80000,  N'Hoạt động'),
(N'Spa & Massage',  300000, N'Hoạt động');

-- ============================================
-- DU LIEU: BOOKINGS (Dat phong mau)
-- NGUOI LAM: LONG
-- ============================================
INSERT INTO Bookings (customer_id, room_id, checkin_date, checkout_date, status, note) VALUES
(1, 1, '2026-09-10', '2026-09-13', 'CheckedOut',  N'Khách đã thanh toán'),
(2, 2, '2026-09-12', '2026-09-15', 'CheckedIn',   N''),
(3, 5, '2026-09-13', '2026-09-16', 'CheckedIn',   N'Yêu cầu phòng im lặng'),
(4, 3, '2026-09-15', '2026-09-18', 'Confirmed',   N'Đặt phòng VIP'),
(5, 4, '2026-09-16', '2026-09-20', 'Confirmed',   N'Gia đình 4 người');

-- ============================================
-- DU LIEU: PAYMENTS (Hoa don mau)
-- NGUOI LAM: HUNG
-- ============================================
INSERT INTO Payments (booking_id, room_total, service_total, grand_total, payment_method, payment_status) VALUES
(1, 900000, 200000, 1100000, N'Tiền mặt', N'Đã thanh toán');

GO
