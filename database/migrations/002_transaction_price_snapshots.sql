-- CORE-01. Back up first, stop application writes, select the intended DB.
-- Run 001 first. Legacy values are estimates, NOT recovered historical facts.
SET XACT_ABORT ON;
BEGIN TRY
    BEGIN TRANSACTION;
    DECLARE @rows BIGINT;
    -- Same order as application transactions. Prevent writes while backfilling.
    SELECT @rows = COUNT_BIG(*) FROM dbo.Rooms WITH (TABLOCKX, HOLDLOCK);
    SELECT @rows = COUNT_BIG(*) FROM dbo.Bookings WITH (TABLOCKX, HOLDLOCK);
    SELECT @rows = COUNT_BIG(*) FROM dbo.Booking_Services WITH (TABLOCKX, HOLDLOCK);
    SELECT @rows = COUNT_BIG(*) FROM dbo.Payments WITH (TABLOCKX, HOLDLOCK);

    IF COL_LENGTH('dbo.Bookings','price_per_night') IS NULL
        ALTER TABLE dbo.Bookings ADD price_per_night DECIMAL(18,6) NULL;
    IF COL_LENGTH('dbo.Bookings','snapshot_estimated') IS NULL
        ALTER TABLE dbo.Bookings ADD snapshot_estimated BIT NOT NULL CONSTRAINT DF_Bookings_SnapshotEstimated DEFAULT 0;
    IF COL_LENGTH('dbo.Booking_Services','service_name_snapshot') IS NULL
        ALTER TABLE dbo.Booking_Services ADD service_name_snapshot NVARCHAR(100) NULL;
    IF COL_LENGTH('dbo.Booking_Services','unit_price_snapshot') IS NULL
        ALTER TABLE dbo.Booking_Services ADD unit_price_snapshot DECIMAL(18,6) NULL;
    IF COL_LENGTH('dbo.Booking_Services','snapshot_estimated') IS NULL
        ALTER TABLE dbo.Booking_Services ADD snapshot_estimated BIT NOT NULL CONSTRAINT DF_BookingServices_SnapshotEstimated DEFAULT 0;
    IF COL_LENGTH('dbo.Payments','invoice_snapshot') IS NULL
        ALTER TABLE dbo.Payments ADD invoice_snapshot NVARCHAR(MAX) NULL;
    IF COL_LENGTH('dbo.Payments','snapshot_estimated') IS NULL
        ALTER TABLE dbo.Payments ADD snapshot_estimated BIT NOT NULL CONSTRAINT DF_Payments_SnapshotEstimated DEFAULT 0;
    IF COL_LENGTH('dbo.Payments','reconciliation_warning') IS NULL
        ALTER TABLE dbo.Payments ADD reconciliation_warning NVARCHAR(500) NULL;

    -- Separate compilation after ADD COLUMN (also safe on a second run).
    EXEC sp_executesql N'
        IF EXISTS (SELECT 1 FROM Rooms WHERE price < 0)
            OR EXISTS (SELECT 1 FROM Booking_Services WHERE total_price < 0 OR quantity <= 0)
            THROW 51003, ''Invalid legacy prices/quantities. Review before migration.'', 1;

        UPDATE b SET price_per_night = r.price, snapshot_estimated = 1
        FROM Bookings b JOIN Rooms r ON r.room_id=b.room_id
        WHERE b.price_per_night IS NULL;

        UPDATE bs SET service_name_snapshot=COALESCE(bs.service_name_snapshot,s.service_name),
            unit_price_snapshot=COALESCE(bs.unit_price_snapshot,
                CAST(CAST(bs.total_price AS DECIMAL(24,6))/bs.quantity AS DECIMAL(18,6))),
            snapshot_estimated=1
        FROM Booking_Services bs JOIN Services s ON s.service_id=bs.service_id
        WHERE bs.service_name_snapshot IS NULL OR bs.unit_price_snapshot IS NULL;

        ALTER TABLE Bookings ALTER COLUMN price_per_night DECIMAL(18,6) NOT NULL;
        ALTER TABLE Booking_Services ALTER COLUMN service_name_snapshot NVARCHAR(100) NOT NULL;
        ALTER TABLE Booking_Services ALTER COLUMN unit_price_snapshot DECIMAL(18,6) NOT NULL;

        UPDATE p SET snapshot_estimated=1,
            reconciliation_warning=CASE WHEN
                p.room_total <> b.price_per_night * DATEDIFF(DAY,b.checkin_date,b.checkout_date)
                OR p.service_total <> COALESCE((SELECT SUM(bs.total_price) FROM Booking_Services bs WHERE bs.booking_id=b.booking_id),0)
                OR p.grand_total <> p.room_total+p.service_total
                OR EXISTS (SELECT 1 FROM Booking_Services bs WHERE bs.booking_id=b.booking_id AND bs.unit_price_snapshot*bs.quantity <> bs.total_price)
                THEN N''Legacy details do not reconcile with recorded payment totals; review required.'' ELSE NULL END
        FROM Payments p JOIN Bookings b ON b.booking_id=p.booking_id
        WHERE p.invoice_snapshot IS NULL;

        UPDATE p SET invoice_snapshot=(
            SELECT p.payment_id AS paymentId,p.booking_id AS bookingId,
                c.full_name AS customerName,c.cccd AS customerCccd,c.phone AS customerPhone,c.address AS customerAddress,
                r.room_code AS roomCode,r.room_name AS roomName,
                CONVERT(VARCHAR(10),b.checkin_date,23) AS checkinDate,
                CONVERT(VARCHAR(10),b.checkout_date,23) AS checkoutDate,
                DATEDIFF(DAY,b.checkin_date,b.checkout_date) AS nights,b.price_per_night AS pricePerNight,
                p.room_total AS roomTotal,p.service_total AS serviceTotal,p.grand_total AS grandTotal,
                p.payment_method AS paymentMethod,p.payment_status AS paymentStatus,
                CONVERT(VARCHAR(33),p.paid_at,126) AS paidAt,
                CAST(1 AS BIT) AS snapshotEstimated,p.reconciliation_warning AS reconciliationWarning,
                JSON_QUERY((SELECT bs.id AS bookingServiceId,bs.service_name_snapshot AS serviceName,
                    bs.quantity,bs.unit_price_snapshot AS unitPrice,bs.total_price AS totalPrice,
                    bs.snapshot_estimated AS snapshotEstimated
                    FROM Booking_Services bs WHERE bs.booking_id=b.booking_id ORDER BY bs.id FOR JSON PATH)) AS services
            FOR JSON PATH,INCLUDE_NULL_VALUES,WITHOUT_ARRAY_WRAPPER)
        FROM Payments p JOIN Bookings b ON b.booking_id=p.booking_id
        JOIN Customers c ON c.customer_id=b.customer_id JOIN Rooms r ON r.room_id=b.room_id
        WHERE p.invoice_snapshot IS NULL;
    ';

    IF OBJECT_ID('dbo.CHK_Bookings_SnapshotPrice','C') IS NULL
        EXEC(N'ALTER TABLE Bookings ADD CONSTRAINT CHK_Bookings_SnapshotPrice CHECK(price_per_night>=0)');
    IF OBJECT_ID('dbo.CHK_BookingServices_SnapshotPrice','C') IS NULL
        EXEC(N'ALTER TABLE Booking_Services ADD CONSTRAINT CHK_BookingServices_SnapshotPrice CHECK(unit_price_snapshot>=0)');
    IF OBJECT_ID('dbo.CHK_Payments_SnapshotJson','C') IS NULL
        EXEC(N'ALTER TABLE Payments ADD CONSTRAINT CHK_Payments_SnapshotJson CHECK(invoice_snapshot IS NULL OR ISJSON(invoice_snapshot)=1)');

    COMMIT TRANSACTION;
    EXEC(N'SELECT payment_id,booking_id,snapshot_estimated,reconciliation_warning FROM Payments WHERE snapshot_estimated=1 ORDER BY payment_id');
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
