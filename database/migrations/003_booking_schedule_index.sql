-- CORE-02. Run after 001 and 002; back up and select the intended database.
SET XACT_ABORT ON;
BEGIN TRY
    BEGIN TRANSACTION;
    IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.Bookings') AND name=N'IX_Bookings_Room_Status_Dates')
        CREATE INDEX IX_Bookings_Room_Status_Dates ON dbo.Bookings(room_id,status,checkin_date,checkout_date);
    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT>0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;

-- Audit only. Existing conflicts must be reviewed, never cancelled automatically.
SELECT first.booking_id AS first_booking_id,second.booking_id AS second_booking_id,first.room_id
FROM dbo.Bookings first JOIN dbo.Bookings second
    ON first.room_id=second.room_id AND first.booking_id<second.booking_id
    AND first.checkin_date<second.checkout_date AND first.checkout_date>second.checkin_date
WHERE first.status IN ('Confirmed','CheckedIn') AND second.status IN ('Confirmed','CheckedIn');
