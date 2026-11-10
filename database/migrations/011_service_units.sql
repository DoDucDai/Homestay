-- Explicit units for catalog and future booking lines. Existing quantities remain unchanged.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF COL_LENGTH('dbo.Services','unit') IS NULL
    ALTER TABLE dbo.Services ADD unit NVARCHAR(20) NOT NULL CONSTRAINT DF_Services_Unit DEFAULT N'lượt';
IF COL_LENGTH('dbo.Booking_Services','unit_snapshot') IS NULL
    ALTER TABLE dbo.Booking_Services ADD unit_snapshot NVARCHAR(20) NULL;
COMMIT;
