-- Adapted from queries_long.sql, feature/long-database@6ce9cee (Long).
-- Read-only report: select the intended database in SSMS. Not a migration.
SET NOCOUNT ON;
DECLARE @Today date = CONVERT(date, SYSUTCDATETIME() AT TIME ZONE 'UTC' AT TIME ZONE 'SE Asia Standard Time');
DECLARE @Year int = YEAR(@Today), @Month int = MONTH(@Today);
DECLARE @Start date = DATEFROMPARTS(@Year, @Month, 1);
DECLARE @End date = DATEADD(month, 1, @Start);

-- 1. Current room status follows RoomSchedule, including overdue checked-in guests.
;WITH RoomState AS (
    SELECT CASE WHEN r.status=N'Bảo trì' THEN N'Bảo trì'
        WHEN EXISTS(SELECT 1 FROM dbo.Bookings b WHERE b.room_id=r.room_id AND b.status='CheckedIn') THEN N'Đang sử dụng'
        WHEN EXISTS(SELECT 1 FROM dbo.Bookings b WHERE b.room_id=r.room_id AND b.status='Confirmed'
            AND b.checkin_date<=@Today AND b.checkout_date>@Today) THEN N'Đã đặt'
        ELSE N'Trống' END AS status
    FROM dbo.Rooms r
)
SELECT v.status, COUNT(s.status) AS room_count
FROM (VALUES(N'Trống'),(N'Đã đặt'),(N'Đang sử dụng'),(N'Bảo trì')) v(status)
LEFT JOIN RoomState s ON s.status=v.status GROUP BY v.status;

-- 2. Ranking by non-cancelled bookings CREATED during the selected month.
SELECT r.room_id, r.room_code, COUNT(b.booking_id) AS non_cancelled_booking_count
FROM dbo.Rooms r LEFT JOIN dbo.Bookings b ON b.room_id=r.room_id
    AND b.status<>'Cancelled' AND b.created_at>=@Start AND b.created_at<@End
GROUP BY r.room_id,r.room_code ORDER BY non_cancelled_booking_count DESC,r.room_id;

-- 3. All bookings created in period (cancellations included, same definition as KPI).
SELECT COUNT(*) AS bookings_created_in_month FROM dbo.Bookings
WHERE created_at>=@Start AND created_at<@End;

-- 4. Paid service lines only, grouped by captured name and price, not today's catalog.
-- Aggregate before joining: no multiplication of payment totals by service rows.
SELECT bs.service_id, bs.service_name_snapshot, bs.unit_price_snapshot,
    SUM(CONVERT(bigint,bs.quantity)) AS paid_quantity,
    SUM(CONVERT(decimal(38,0),bs.total_price)) AS paid_service_line_total
FROM dbo.Booking_Services bs JOIN dbo.Payments p ON p.booking_id=bs.booking_id
WHERE p.payment_status=N'Đã thanh toán' AND p.paid_at>=@Start AND p.paid_at<@End
GROUP BY bs.service_id,bs.service_name_snapshot,bs.unit_price_snapshot
ORDER BY paid_service_line_total DESC,bs.service_id,bs.service_name_snapshot,bs.unit_price_snapshot;

-- 5. Active reservations/occupancy; overdue occupancy remains visible.
SELECT b.booking_id,c.full_name,r.room_code,b.checkin_date,b.checkout_date,b.status,
    DATEDIFF(day,b.checkin_date,b.checkout_date) AS booked_nights,
    CONVERT(bit,CASE WHEN b.status='CheckedIn' AND b.checkout_date<=@Today THEN 1 ELSE 0 END) AS overdue_checkout
FROM dbo.Bookings b JOIN dbo.Customers c ON c.customer_id=b.customer_id
JOIN dbo.Rooms r ON r.room_id=b.room_id
WHERE b.status IN ('Confirmed','CheckedIn') ORDER BY b.checkin_date,b.booking_id;
