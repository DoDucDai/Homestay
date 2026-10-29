USE HomestayDB;
GO

-- ============================================================
-- TRUY VẤN KIỂM TRA TÍNH TOÀN VẸN VÀ LOGIC DỮ LIỆU (LONG)
-- ============================================================

-- 1. Thống kê tổng số lượng phòng theo từng trạng thái
SELECT 
    status AS [Trạng thái phòng], 
    COUNT(*) AS [Số lượng phòng]
FROM Rooms
GROUP BY status;

-- 2. Kiểm tra danh sách đặt phòng và tính số đêm lưu trú thực tế
SELECT 
    b.booking_id AS [Mã Booking ID],
    r.room_code AS [Mã phòng],
    r.room_name AS [Tên phòng],
    b.checkin_date AS [Ngày nhận],
    b.checkout_date AS [Ngày trả],
    DATEDIFF(DAY, b.checkin_date, b.checkout_date) AS [Số đêm lưu trú],
    r.price AS [Đơn giá phòng],
    (DATEDIFF(DAY, b.checkin_date, b.checkout_date) * r.price) AS [Tiền phòng tính toán]
FROM Bookings b
JOIN Rooms r ON b.room_id = r.room_id;

-- 3. Kiểm tra tính đối soát hóa đơn thanh toán (Grand Total = Room Total + Service Total)
SELECT 
    booking_id AS [Mã Booking ID],
    room_total AS [Tiền phòng],
    service_total AS [Tiền dịch vụ],
    grand_total AS [Tổng tiền hóa đơn],
    (room_total + service_total) AS [Tổng tính lại]
FROM Payments
WHERE grand_total <> (room_total + service_total);

-- 4. Báo cáo doanh thu đã thanh toán theo Tháng / Năm
SELECT 
    YEAR(paid_at) AS [Năm],
    MONTH(paid_at) AS [Tháng],
    COUNT(*) AS [Số hóa đơn],
    SUM(grand_total) AS [Tổng doanh thu VND]
FROM Payments
GROUP BY YEAR(paid_at), MONTH(paid_at)
ORDER BY [Năm] DESC, [Tháng] DESC;

-- 5. Báo cáo doanh thu theo từng phương thức thanh toán
SELECT 
    payment_method AS [Phương thức thanh toán],
    COUNT(*) AS [Số lượng giao dịch],
    SUM(grand_total) AS [Tổng tiền]
FROM Payments
GROUP BY payment_method;
