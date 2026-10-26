-- SEC-01. Back up, stop ALL app writes, run 001/002/003 first.
-- Then run the Java password migration tool BEFORE starting the new backend.
-- SQL never attempts to hash/reset passwords or guess a plaintext/hash prefix.
SET XACT_ABORT ON;
BEGIN TRY
    BEGIN TRANSACTION;
    IF COL_LENGTH('dbo.Users','password_scheme') IS NULL
        ALTER TABLE dbo.Users ADD password_scheme NVARCHAR(32) NOT NULL
            CONSTRAINT DF_Users_PasswordScheme DEFAULT 'legacy';
    IF COL_LENGTH('dbo.Users','auth_version') IS NULL
        ALTER TABLE dbo.Users ADD auth_version INT NOT NULL CONSTRAINT DF_Users_AuthVersion DEFAULT 0;
    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT>0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
