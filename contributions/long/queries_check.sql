USE HomestayDB;
GO

-- 1. Kiểm tra số lượng dòng của từng bảng
SELECT 'Rooms' AS TableName, COUNT(*) AS TotalRows FROM Rooms
UNION ALL
SELECT 'Bookings', COUNT(*) FROM Bookings
UNION ALL
SELECT 'Booking_Services', COUNT(*) FROM Booking_Services
UNION ALL
SELECT 'Payments', COUNT(*) FROM Payments;

-- 2. Kiểm tra các booking có ngày trả không sau ngày nhận
SELECT booking_code, checkin_date, checkout_date 
FROM Bookings 
WHERE checkout_date <= checkin_date;

-- 3. Kiểm tra booking không bị hủy có lịch trùng nhau trong cùng phòng
SELECT b1.booking_code AS Booking1, b2.booking_code AS Booking2, b1.room_code
FROM Bookings b1
JOIN Bookings b2 ON b1.room_code = b2.room_code AND b1.booking_code < b2.booking_code
WHERE b1.status <> 'Cancelled' AND b2.status <> 'Cancelled'
  AND b1.checkin_date < b2.checkout_date 
  AND b1.checkout_date > b2.checkin_date;

-- 4. Kiểm tra tổng tiền hóa đơn không bằng tiền phòng cộng tiền dịch vụ
SELECT p.booking_code, p.room_total, p.service_total, p.grand_total, 
       (p.room_total + p.service_total) AS calculated_total
FROM Payments p
WHERE p.grand_total <> (p.room_total + p.service_total);

-- 5. Báo cáo doanh thu đã thanh toán theo tháng và năm dựa vào paid_at
SELECT 
    YEAR(paid_at) AS [Năm],
    MONTH(paid_at) AS [Tháng],
    COUNT(booking_code) AS [Số lượng hóa đơn],
    SUM(grand_total) AS [Tổng doanh thu]
FROM Payments
GROUP BY YEAR(paid_at), MONTH(paid_at)
ORDER BY [Năm] DESC, [Tháng] DESC;

-- 6. Thống kê tổng tiền thu được theo từng phương thức thanh toán
SELECT 
    payment_method AS [Phương thức],
    COUNT(*) AS [Số giao dịch],
    SUM(grand_total) AS [Tổng tiền]
FROM Payments
GROUP BY payment_method;
