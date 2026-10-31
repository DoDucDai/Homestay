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
    password_scheme NVARCHAR(32) NOT NULL DEFAULT 'pbkdf2-v58',
    auth_version INT NOT NULL DEFAULT 0,
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
    document_type NVARCHAR(12) NOT NULL DEFAULT 'CCCD' CHECK (document_type IN ('CCCD','PASSPORT','LEGACY')),
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
    price_per_night DECIMAL(18,6) NOT NULL CHECK (price_per_night >= 0),
    snapshot_estimated BIT NOT NULL DEFAULT 0,
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
    service_name_snapshot NVARCHAR(100) NOT NULL,
    unit_price_snapshot DECIMAL(18,6) NOT NULL CHECK (unit_price_snapshot >= 0),
    snapshot_estimated BIT NOT NULL DEFAULT 0,
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
    paid_at         DATETIME      DEFAULT GETDATE(),
    invoice_snapshot NVARCHAR(MAX) NULL,
    snapshot_estimated BIT NOT NULL DEFAULT 0,
    reconciliation_warning NVARCHAR(500) NULL,
    CONSTRAINT CHK_Payments_SnapshotJson CHECK (invoice_snapshot IS NULL OR ISJSON(invoice_snapshot) = 1),
    CONSTRAINT UQ_Payments_Booking UNIQUE (booking_id)
);

GO

-- CORE-02: overlap, occupancy and current-status queries.
CREATE INDEX IX_Bookings_Room_Status_Dates ON dbo.Bookings(room_id,status,checkin_date,checkout_date);
GO

GO
-- Additive migration. Existing nightly reservations and invoice JSON stay intact.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF COL_LENGTH('dbo.Rooms','hourly_price') IS NULL
    ALTER TABLE dbo.Rooms ADD hourly_price DECIMAL(12,0) NULL
        CONSTRAINT CHK_Rooms_HourlyPrice CHECK (hourly_price > 0);
IF COL_LENGTH('dbo.Bookings','billing_unit') IS NULL
    ALTER TABLE dbo.Bookings ADD billing_unit VARCHAR(5) NOT NULL
        CONSTRAINT DF_Bookings_BillingUnit DEFAULT 'Night',
        checkin_at DATETIME2(0) NULL, checkout_at DATETIME2(0) NULL,
        price_per_hour DECIMAL(12,0) NULL;
GO
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE name='CHK_Bookings_Hourly')
    ALTER TABLE dbo.Bookings ADD CONSTRAINT CHK_Bookings_Hourly CHECK (
        (billing_unit='Night' AND checkin_at IS NULL AND checkout_at IS NULL AND price_per_hour IS NULL)
        OR (billing_unit='Hour' AND checkin_at IS NOT NULL AND checkout_at IS NOT NULL
            AND checkout_at>checkin_at AND price_per_hour IS NOT NULL AND price_per_hour>0));
COMMIT;
