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
-- Demo password is 123456. Hashes are PBKDF2 with independent salts, not plaintext.
INSERT INTO Users (username, password, full_name, role, password_scheme) VALUES
('admin',    '{pbkdf2-v58}83ef42160e1dee58d2691b8c2972f4c745ee3ebcb0ca81021fcc8c92dca67ca55bb4e604b64764043171f92b3f31908b', N'Nguyễn Văn Admin',  'admin','pbkdf2-v58'),
('nhanvien', '{pbkdf2-v58}bcc86e907fe1526471578147bc10964535f958791ddf8272130563e192067b17a660f4940ac16f50a2fece1b8c29d2c6', N'Trần Thị Nhân Viên', 'staff','pbkdf2-v58');

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
INSERT INTO Bookings (customer_id, room_id, checkin_date, checkout_date, status, note, price_per_night) VALUES
(1, 1, '2026-09-10', '2026-09-13', 'CheckedOut',  N'Khách đã thanh toán',300000),
(2, 2, '2026-09-12', '2026-09-15', 'CheckedIn',   N'',500000),
(3, 5, '2026-09-13', '2026-09-16', 'CheckedIn',   N'Yêu cầu phòng im lặng',350000),
(4, 3, '2026-09-15', '2026-09-18', 'Confirmed',   N'Đặt phòng VIP',900000),
(5, 4, '2026-09-16', '2026-09-20', 'Confirmed',   N'Gia đình 4 người',1200000);

-- Explicit captured service for the paid sample invoice (not a live catalog join).
INSERT INTO Booking_Services(booking_id,service_id,quantity,total_price,service_name_snapshot,unit_price_snapshot)
VALUES(1,3,1,200000,N'Đưa đón sân bay',200000);

-- ============================================
-- DU LIEU: PAYMENTS (Hoa don mau)
-- NGUOI LAM: HUNG
-- ============================================
INSERT INTO Payments (booking_id, room_total, service_total, grand_total, payment_method, payment_status) VALUES
(1, 900000, 200000, 1100000, N'Tiền mặt', N'Đã thanh toán');

-- Frozen sample invoice, same contract as GET /api/payments/{id}.
UPDATE p SET invoice_snapshot=(
    SELECT p.payment_id AS paymentId,p.booking_id AS bookingId,
        c.full_name AS customerName,c.cccd AS customerCccd,c.phone AS customerPhone,c.address AS customerAddress,
        r.room_code AS roomCode,r.room_name AS roomName,
        CONVERT(VARCHAR(10),b.checkin_date,23) AS checkinDate,
        CONVERT(VARCHAR(10),b.checkout_date,23) AS checkoutDate,
        DATEDIFF(DAY,b.checkin_date,b.checkout_date) AS nights,b.price_per_night AS pricePerNight,
        p.room_total AS roomTotal,p.service_total AS serviceTotal,p.grand_total AS grandTotal,
        p.payment_method AS paymentMethod,p.payment_status AS paymentStatus,
        CONVERT(VARCHAR(33),p.paid_at,126) AS paidAt,
        CAST(0 AS BIT) AS snapshotEstimated,p.reconciliation_warning AS reconciliationWarning,
        JSON_QUERY((SELECT bs.id AS bookingServiceId,bs.service_name_snapshot AS serviceName,
            bs.quantity,bs.unit_price_snapshot AS unitPrice,bs.total_price AS totalPrice,
            bs.snapshot_estimated AS snapshotEstimated
            FROM Booking_Services bs WHERE bs.booking_id=b.booking_id ORDER BY bs.id FOR JSON PATH)) AS services
    FOR JSON PATH,INCLUDE_NULL_VALUES,WITHOUT_ARRAY_WRAPPER)
FROM Payments p JOIN Bookings b ON b.booking_id=p.booking_id
JOIN Customers c ON c.customer_id=b.customer_id JOIN Rooms r ON r.room_id=b.room_id;

GO
