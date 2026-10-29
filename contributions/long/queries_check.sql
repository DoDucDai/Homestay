-- Select the intended database in SSMS first. Read-only; no USE or writes.
-- 1. Row counts for all tables.
SELECT 'Users' AS table_name,COUNT_BIG(*) AS row_count FROM dbo.Users
UNION ALL SELECT 'Rooms',COUNT_BIG(*) FROM dbo.Rooms
UNION ALL SELECT 'Customers',COUNT_BIG(*) FROM dbo.Customers
UNION ALL SELECT 'Services',COUNT_BIG(*) FROM dbo.Services
UNION ALL SELECT 'Bookings',COUNT_BIG(*) FROM dbo.Bookings
UNION ALL SELECT 'Booking_Services',COUNT_BIG(*) FROM dbo.Booking_Services
UNION ALL SELECT 'Payments',COUNT_BIG(*) FROM dbo.Payments;
-- 2. Invalid stay dates: expected zero rows.
SELECT booking_id,room_id,checkin_date,checkout_date,status FROM dbo.Bookings WHERE checkout_date<=checkin_date;
-- 3. Overlapping non-cancelled reservations: expected zero rows. Adjacent dates are allowed.
SELECT a.room_id,a.booking_id AS booking_a,b.booking_id AS booking_b,
 a.checkin_date AS arrival_a,a.checkout_date AS departure_a,b.checkin_date AS arrival_b,b.checkout_date AS departure_b
FROM dbo.Bookings a JOIN dbo.Bookings b ON a.room_id=b.room_id AND a.booking_id<b.booking_id
WHERE a.status<>'Cancelled' AND b.status<>'Cancelled'
 AND a.checkin_date<b.checkout_date AND b.checkin_date<a.checkout_date;
-- 4. Reconcile invoice components to captured prices, never current catalog.
-- Aggregate services before joining Payments to avoid multiplying revenue.
SELECT p.payment_id,p.booking_id,p.room_total,p.service_total,p.grand_total,
 DATEDIFF(day,b.checkin_date,b.checkout_date)*b.price_per_night AS expected_room_total,
 COALESCE(s.expected_service_total,0) AS expected_service_total
FROM dbo.Payments p JOIN dbo.Bookings b ON b.booking_id=p.booking_id
OUTER APPLY (SELECT SUM(CONVERT(decimal(28,0),bs.quantity)*bs.unit_price_snapshot) AS expected_service_total
 FROM dbo.Booking_Services bs WHERE bs.booking_id=b.booking_id) s
WHERE p.grand_total<>p.room_total+p.service_total
 OR p.room_total<>DATEDIFF(day,b.checkin_date,b.checkout_date)*b.price_per_night
 OR p.service_total<>COALESCE(s.expected_service_total,0);
-- 5. Paid revenue by year/month, based on paid_at (Vietnam business time).
SELECT YEAR(paid_at) AS revenue_year,MONTH(paid_at) AS revenue_month,COUNT_BIG(*) AS invoice_count,SUM(grand_total) AS revenue_vnd
FROM dbo.Payments WHERE payment_status=N'Đã thanh toán'
GROUP BY YEAR(paid_at),MONTH(paid_at) ORDER BY revenue_year,revenue_month;
-- 6. Paid revenue by year.
SELECT YEAR(paid_at) AS revenue_year,COUNT_BIG(*) AS invoice_count,SUM(grand_total) AS revenue_vnd
FROM dbo.Payments WHERE payment_status=N'Đã thanh toán' GROUP BY YEAR(paid_at) ORDER BY revenue_year;
-- 7. Paid revenue by method.
SELECT payment_method,COUNT_BIG(*) AS invoice_count,SUM(grand_total) AS revenue_vnd
FROM dbo.Payments WHERE payment_status=N'Đã thanh toán' GROUP BY payment_method ORDER BY payment_method;
-- 8. Unexpected payment lifecycle/duplicates: expected zero rows.
SELECT b.booking_id,b.status,COUNT(p.payment_id) AS payment_count
FROM dbo.Bookings b LEFT JOIN dbo.Payments p ON p.booking_id=b.booking_id GROUP BY b.booking_id,b.status
HAVING (b.status='CheckedOut' AND COUNT(p.payment_id)<>1) OR (b.status<>'CheckedOut' AND COUNT(p.payment_id)<>0);
-- 9. Missing/malformed frozen invoices: expected zero rows after import.
SELECT payment_id,booking_id FROM dbo.Payments WHERE invoice_snapshot IS NULL OR ISJSON(invoice_snapshot)<>1;
