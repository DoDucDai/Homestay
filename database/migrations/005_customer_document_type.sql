-- FIX-03: back up, stop writes, run after 001-004/password conversion.
-- Preserve every legacy document/phone. Never guess that an old number is a passport.
SET XACT_ABORT ON;
BEGIN TRY
    BEGIN TRANSACTION;
    IF COL_LENGTH('dbo.Customers','document_type') IS NULL
        ALTER TABLE dbo.Customers ADD document_type NVARCHAR(12) NULL;
    EXEC sp_executesql N'
        UPDATE Customers SET document_type=CASE WHEN LEN(cccd)=12
            AND cccd COLLATE Latin1_General_100_BIN2 NOT LIKE ''%[^0-9]%'' THEN ''CCCD'' ELSE ''LEGACY'' END
        WHERE document_type IS NULL;
        ALTER TABLE Customers ALTER COLUMN document_type NVARCHAR(12) NOT NULL;
        IF NOT EXISTS(SELECT 1 FROM sys.default_constraints d JOIN sys.columns c
            ON c.object_id=d.parent_object_id AND c.column_id=d.parent_column_id
            WHERE d.parent_object_id=OBJECT_ID(''dbo.Customers'') AND c.name=''document_type'')
            ALTER TABLE Customers ADD CONSTRAINT DF_Customers_DocumentType DEFAULT ''CCCD'' FOR document_type;
        IF OBJECT_ID(''dbo.CHK_Customers_DocumentType'',''C'') IS NULL
            ALTER TABLE Customers ADD CONSTRAINT CHK_Customers_DocumentType CHECK(document_type IN (''CCCD'',''PASSPORT'',''LEGACY''));
    ';
    COMMIT TRANSACTION;
    EXEC(N'SELECT customer_id,document_type,cccd,phone,email FROM Customers WHERE document_type=''LEGACY''
        OR LEN(phone)<>10 OR LEFT(phone,1)<>''0'' OR phone COLLATE Latin1_General_100_BIN2 LIKE ''%[^0-9]%''
        OR (email IS NOT NULL AND email<>'''' AND (email NOT LIKE ''%_@_%._%'' OR email LIKE ''% %'')) ORDER BY customer_id');
END TRY
BEGIN CATCH
    IF @@TRANCOUNT>0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
