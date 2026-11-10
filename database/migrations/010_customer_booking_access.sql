-- Additive preparation for private customer bookings. Do not infer legacy ownership.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF COL_LENGTH('dbo.Users','customer_id') IS NULL
    THROW 51000, 'Apply account registration migration 008 first.', 1;
IF COL_LENGTH('dbo.Bookings','selected_payment_method') IS NULL
    ALTER TABLE dbo.Bookings ADD selected_payment_method NVARCHAR(50) NULL;
IF COL_LENGTH('dbo.Bookings','reservation_expires_at') IS NULL
    ALTER TABLE dbo.Bookings ADD reservation_expires_at DATETIME2(0) NULL;
IF COL_LENGTH('dbo.Bookings','payment_review_status') IS NULL
    ALTER TABLE dbo.Bookings ADD payment_review_status VARCHAR(12) NOT NULL
        CONSTRAINT DF_Bookings_PaymentReview DEFAULT 'None';
IF COL_LENGTH('dbo.Bookings','transfer_reported_at') IS NULL
    ALTER TABLE dbo.Bookings ADD transfer_reported_at DATETIME2(0) NULL;
IF COL_LENGTH('dbo.Bookings','payment_reviewed_at') IS NULL
    ALTER TABLE dbo.Bookings ADD payment_reviewed_at DATETIME2(0) NULL, payment_reviewed_by INT NULL;
IF COL_LENGTH('dbo.Bookings','cancellation_reason') IS NULL
    ALTER TABLE dbo.Bookings ADD cancellation_reason VARCHAR(20) NULL;
GO
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE name='CK_Bookings_SelectedPayment')
    ALTER TABLE dbo.Bookings ADD CONSTRAINT CK_Bookings_SelectedPayment CHECK
        (selected_payment_method IS NULL OR selected_payment_method IN(N'Tiền mặt',N'Chuyển khoản'));
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE name='CK_Bookings_PaymentReview')
    ALTER TABLE dbo.Bookings ADD CONSTRAINT CK_Bookings_PaymentReview CHECK
        (payment_review_status IN('None','Pending','Rejected','Confirmed'));
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE name='FK_Bookings_PaymentReviewer')
    ALTER TABLE dbo.Bookings ADD CONSTRAINT FK_Bookings_PaymentReviewer
        FOREIGN KEY(payment_reviewed_by) REFERENCES dbo.Users(user_id);
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE name='IX_Bookings_Customer_Created' AND object_id=OBJECT_ID('dbo.Bookings'))
    CREATE INDEX IX_Bookings_Customer_Created ON dbo.Bookings(customer_id,created_at DESC,booking_id DESC);
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE name='IX_Bookings_ReservationExpiry' AND object_id=OBJECT_ID('dbo.Bookings'))
    CREATE INDEX IX_Bookings_ReservationExpiry ON dbo.Bookings(status,reservation_expires_at)
        INCLUDE(room_id,payment_review_status);
COMMIT;
