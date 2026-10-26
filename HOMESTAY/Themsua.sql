
USE HOMESTAY;
GO

-- 1. XEM DANH SÁCH KHÁCH HÀNG
SELECT *
FROM dbo.Customers
ORDER BY customer_id;
GO

-- 2. THÊM KHÁCH HÀNG MỚI
INSERT INTO dbo.Customers
    (full_name, cccd, phone, email, address)
VALUES
    (N'Nguyễn Thị Mai',
     '001099008888',
     '0987654321',
     'mai@email.com',
     N'Hà Nội');
GO

-- 3. SỬA THÔNG TIN KHÁCH HÀNG
-- Ví dụ: sửa khách hàng có customer_id = 1
UPDATE dbo.Customers
SET
    full_name = N'Nguyễn Văn An',
    cccd = '001099001234',
    phone = '0901234567',
    email = 'an@email.com',
    address = N'Hà Nội'
WHERE customer_id = 1;
GO

-- 4. SỬA RIÊNG EMAIL BỊ GHÉP TRÙNG
UPDATE dbo.Customers
SET email = 'an@email.com'
WHERE customer_id = 1
  AND email = 'an@email.coman@email.com';
GO

-- 5. XÓA KHÁCH HÀNG
-- Chỉ xóa khi chắc chắn khách hàng chưa có dữ liệu
-- đặt phòng hoặc hóa đơn liên quan.
-- Thay 6 bằng customer_id cần xóa.
-- DELETE FROM dbo.Customers
-- WHERE customer_id = 6;
-- GO

-- 6. TÌM KIẾM KHÁCH HÀNG THEO TÊN
SELECT *
FROM dbo.Customers
WHERE full_name LIKE N'%Nguyễn%'
ORDER BY customer_id;
GO

-- 7. TÌM KIẾM THEO CCCD
SELECT *
FROM dbo.Customers
WHERE cccd = '001099001234';
GO
