-- ============================================
-- schema.sql - TAO DATABASE VA CAC BANG
-- NGUOI LAM: LONG + HUNG
-- Chay file nay trong SQL Server Management Studio (SSMS)
-- ============================================

-- Tao database
CREATE DATABASE HomestayDB;
GO

USE HomestayDB;
GO

-- ============================================
-- BANG 1: USERS (Tai khoan dang nhap)
-- NGUOI LAM: HUNG
-- ============================================
CREATE TABLE Users (
    user_id    INT IDENTITY(1,1) PRIMARY KEY,
    username   NVARCHAR(50)  NOT NULL UNIQUE,
    password   NVARCHAR(255) NOT NULL,
    full_name  NVARCHAR(100) NOT NULL,
    role       NVARCHAR(20)  NOT NULL DEFAULT 'staff', -- admin / staff
    created_at DATETIME      DEFAULT GETDATE()
);

-- ============================================
-- BANG 2: ROOMS (Phong)
-- NGUOI LAM: LONG
-- ============================================
CREATE TABLE Rooms (
    room_id     INT IDENTITY(1,1) PRIMARY KEY,
    room_code   NVARCHAR(20)  NOT NULL UNIQUE,   -- VD: P101, P102
    room_name   NVARCHAR(100) NOT NULL,
    room_type   NVARCHAR(50)  NOT NULL,           -- Single, Double, VIP, Family
    price       DECIMAL(12,0) NOT NULL,           -- Gia / dem
    status      NVARCHAR(30)  NOT NULL DEFAULT N'Trống', -- Trống / Đã đặt / Đang sử dụng / Bảo trì
    description NVARCHAR(500),
    created_at  DATETIME      DEFAULT GETDATE()
);

-- ============================================
-- BANG 3: CUSTOMERS (Khach hang)
-- NGUOI LAM: HUNG
-- ============================================
CREATE TABLE Customers (
    customer_id INT IDENTITY(1,1) PRIMARY KEY,
    full_name   NVARCHAR(100) NOT NULL,
    cccd        NVARCHAR(20)  NOT NULL UNIQUE,    -- So CCCD hoac Passport
    phone       NVARCHAR(15)  NOT NULL,
    email       NVARCHAR(100),
    address     NVARCHAR(200),
    created_at  DATETIME      DEFAULT GETDATE()
);

-- ============================================
-- BANG 4: BOOKINGS (Dat phong)
-- NGUOI LAM: LONG
-- ============================================
CREATE TABLE Bookings (
    booking_id    INT IDENTITY(1,1) PRIMARY KEY,
    customer_id   INT           NOT NULL REFERENCES Customers(customer_id),
    room_id       INT           NOT NULL REFERENCES Rooms(room_id),
    checkin_date  DATE          NOT NULL,
    checkout_date DATE          NOT NULL,
    status        NVARCHAR(30)  NOT NULL DEFAULT 'Confirmed', -- Confirmed / CheckedIn / CheckedOut / Cancelled
    note          NVARCHAR(500),
    created_at    DATETIME      DEFAULT GETDATE(),
    CONSTRAINT CHK_Dates CHECK (checkout_date > checkin_date)
);

-- ============================================
-- BANG 5: SERVICES (Dich vu)
-- NGUOI LAM: LONG
-- ============================================
CREATE TABLE Services (
    service_id   INT IDENTITY(1,1) PRIMARY KEY,
    service_name NVARCHAR(100) NOT NULL UNIQUE,
    unit_price   DECIMAL(12,0) NOT NULL CHECK (unit_price >= 0),
    status       NVARCHAR(20)  NOT NULL DEFAULT N'Hoạt động' -- Hoạt động / Ngừng
);

-- ============================================
-- BANG 6: BOOKING_SERVICES (Dich vu cua tung booking)
-- NGUOI LAM: LONG
-- ============================================
CREATE TABLE Booking_Services (
    id          INT IDENTITY(1,1) PRIMARY KEY,
    booking_id  INT           NOT NULL REFERENCES Bookings(booking_id),
    service_id  INT           NOT NULL REFERENCES Services(service_id),
    quantity    INT           NOT NULL DEFAULT 1 CHECK (quantity > 0),
    total_price DECIMAL(12,0) NOT NULL  -- = unit_price * quantity
);

-- ============================================
-- BANG 7: PAYMENTS (Hoa don / Thanh toan)
-- NGUOI LAM: HUNG
-- ============================================
CREATE TABLE Payments (
    payment_id      INT IDENTITY(1,1) PRIMARY KEY,
    booking_id      INT           NOT NULL REFERENCES Bookings(booking_id),
    room_total      DECIMAL(12,0) NOT NULL,  -- Tien phong
    service_total   DECIMAL(12,0) NOT NULL DEFAULT 0, -- Tien dich vu
    grand_total     DECIMAL(12,0) NOT NULL,  -- Tong cong
    payment_method  NVARCHAR(50)  NOT NULL DEFAULT N'Tiền mặt',
    payment_status  NVARCHAR(30)  NOT NULL DEFAULT N'Đã thanh toán',
    paid_at         DATETIME      DEFAULT GETDATE()
);

GO
