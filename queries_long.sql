USE HomestayDB;
GO

-- ============================================================
-- NHIỆM VỤ CỦA LONG: QUERY THỐNG KÊ (ROOMS, BOOKINGS, SERVICES)
-- ============================================================

-- 1. Thống kê tổng số lượng phòng theo từng trạng thái (Trống, Đã đặt, Đang sử dụng, Bảo trì)
SELECT 
    status AS [Trạng thái phòng], 
    COUNT(*) AS [Số lượng phòng]
FROM Rooms
GROUP BY status;


-- 2. Thống kê danh sách các phòng được đặt nhiều nhất (Sắp xếp giảm dần)
SELECT 
    r.room_code AS [Mã phòng],
    r.room_name AS [Tên phòng],
    r.room_type AS [Loại phòng],
    COUNT(b.booking_id) AS [Số lần đặt]
FROM Rooms r
LEFT JOIN Bookings b ON r.room_id = b.room_id
GROUP BY r.room_id, r.room_code, r.room_name, r.room_type
ORDER BY [Số lần đặt] DESC;


-- 3. Thống kê tổng số booking trong tháng hiện tại
SELECT 
    COUNT(*) AS [Tổng booking tháng này]
FROM Bookings
WHERE MONTH(created_at) = MONTH(GETDATE()) 
  AND YEAR(created_at) = YEAR(GETDATE());


-- 4. Thống kê top dịch vụ được sử dụng nhiều nhất & tổng doanh thu từ từng dịch vụ
SELECT 
    s.service_name AS [Tên dịch vụ],
    s.unit_price AS [Đơn giá],
    ISNULL(SUM(bs.quantity), 0) AS [Tổng số lượng đã đặt],
    ISNULL(SUM(bs.total_price), 0) AS [Tổng doanh thu dịch vụ]
FROM Services s
LEFT JOIN Booking_Services bs ON s.service_id = bs.service_id
GROUP BY s.service_id, s.service_name, s.unit_price
ORDER BY [Tổng số lượng đã đặt] DESC;


-- 5. Thống kê chi tiết danh sách Booking đang hoạt động (Đã xác nhận hoặc Đang lưu trú)
SELECT 
    b.booking_id AS [Mã Booking],
    r.room_code AS [Phòng],
    b.checkin_date AS [Ngày nhận phòng],
    b.checkout_date AS [Ngày trả phòng],
    DATEDIFF(DAY, b.checkin_date, b.checkout_date) AS [Số đêm],
    b.status AS [Trạng thái]
FROM Bookings b
JOIN Rooms r ON b.room_id = r.room_id
WHERE b.status IN ('Confirmed', 'CheckedIn')
ORDER BY b.checkin_date ASC;