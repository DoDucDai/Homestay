-- Shared account registration, recovery and email verification. Preserve existing people and invoices.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF COL_LENGTH('dbo.Users','email_version') IS NULL
    ALTER TABLE dbo.Users ADD email_version INT NOT NULL CONSTRAINT DF_Users_EmailVersion DEFAULT 0,
        email_verified_at DATETIME2(0) NULL, customer_id INT NULL;
GO
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE name='FK_Users_Customer')
    ALTER TABLE dbo.Users ADD CONSTRAINT FK_Users_Customer FOREIGN KEY(customer_id) REFERENCES dbo.Customers(customer_id);
IF COL_LENGTH('dbo.Customers','email') IS NOT NULL AND COL_LENGTH('dbo.Customers','email')<508
    ALTER TABLE dbo.Customers ALTER COLUMN email NVARCHAR(254) NULL;
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE name='UX_Users_Customer' AND object_id=OBJECT_ID('dbo.Users'))
    CREATE UNIQUE INDEX UX_Users_Customer ON dbo.Users(customer_id) WHERE customer_id IS NOT NULL;
IF OBJECT_ID('dbo.AccountEmails','U') IS NULL
    CREATE TABLE dbo.AccountEmails(
        email NVARCHAR(254) NOT NULL PRIMARY KEY,
        user_id INT NOT NULL UNIQUE REFERENCES dbo.Users(user_id)
    );
-- Ambiguous legacy addresses remain unchanged and cannot be used for reset/register.
INSERT dbo.AccountEmails(email,user_id)
SELECT LOWER(LTRIM(RTRIM(u.email))),u.user_id FROM dbo.Users u
WHERE u.email IS NOT NULL AND LEN(LTRIM(RTRIM(u.email)))>0
    AND NOT EXISTS(SELECT 1 FROM dbo.AccountEmails e WHERE e.email=LOWER(LTRIM(RTRIM(u.email))) OR e.user_id=u.user_id)
    AND 1=(SELECT COUNT(*) FROM dbo.Users other WHERE LOWER(LTRIM(RTRIM(other.email)))=LOWER(LTRIM(RTRIM(u.email))));
IF OBJECT_ID('dbo.AccountChallenges','U') IS NULL
BEGIN
    CREATE TABLE dbo.AccountChallenges(
        challenge_id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
        user_id INT NOT NULL REFERENCES dbo.Users(user_id),
        purpose VARCHAR(6) NOT NULL CHECK(purpose IN('VERIFY','RESET')),
        email NVARCHAR(254) NOT NULL, email_version INT NOT NULL, auth_version INT NOT NULL,
        secret_hash CHAR(64) NOT NULL, attempts INT NOT NULL DEFAULT 0,
        created_at DATETIME2(0) NOT NULL DEFAULT SYSUTCDATETIME(),
        expires_at DATETIME2(0) NOT NULL, used_at DATETIME2(0) NULL,
        delivery_status VARCHAR(7) NOT NULL DEFAULT 'PENDING' CHECK(delivery_status IN('PENDING','SENT','FAILED'))
    );
    CREATE INDEX IX_AccountChallenges_User ON dbo.AccountChallenges(user_id,purpose,created_at);
END;
IF OBJECT_ID('dbo.AccountRateLimits','U') IS NULL
    CREATE TABLE dbo.AccountRateLimits(
        bucket_hash CHAR(64) NOT NULL PRIMARY KEY,
        window_until DATETIME2(0) NOT NULL, requests INT NOT NULL
    );
COMMIT;
