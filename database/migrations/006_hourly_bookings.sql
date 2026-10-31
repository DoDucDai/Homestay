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
