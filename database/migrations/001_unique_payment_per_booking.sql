-- FIX-02: run against the intended Homestay database after making a backup.
-- Never delete duplicate invoices automatically. Resolve them with the owner.
SET XACT_ABORT ON;
BEGIN TRY
    BEGIN TRANSACTION;

    -- Hold the table stable between duplicate detection and constraint creation.
    IF EXISTS (
        SELECT booking_id FROM dbo.Payments WITH (TABLOCKX, HOLDLOCK)
        GROUP BY booking_id HAVING COUNT(*) > 1
    )
    BEGIN
        SELECT booking_id, COUNT(*) AS invoice_count
        FROM dbo.Payments GROUP BY booking_id HAVING COUNT(*) > 1;
        THROW 51001, 'Duplicate booking invoices found. Review with the owner; no data was deleted.', 1;
    END;

    IF NOT EXISTS (
        SELECT 1 FROM sys.indexes
        WHERE object_id = OBJECT_ID(N'dbo.Payments')
          AND name = N'UQ_Payments_Booking'
          AND is_unique = 1
          AND is_disabled = 0
    )
        ALTER TABLE dbo.Payments
            ADD CONSTRAINT UQ_Payments_Booking UNIQUE (booking_id);

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
