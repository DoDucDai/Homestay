-- Adapted from Hưng's Themsua.sql@14778a4. Manual maintenance, never run at startup.
-- Select the intended database. Default only previews exact duplicated email halves.
SET NOCOUNT ON;
SET XACT_ABORT ON;
DECLARE @Apply bit=0;
DECLARE @CustomerId int=NULL, @ExpectedEmail nvarchar(100)=NULL, @CorrectEmail nvarchar(100)=NULL;

IF @Apply=0
BEGIN
    SELECT customer_id,email AS current_email,LEFT(email,LEN(email)/2) AS proposed_email
    FROM dbo.Customers
    WHERE (@CustomerId IS NULL OR customer_id=@CustomerId)
        AND LEN(email)%2=0 AND LEN(email)>0
        AND LEFT(email,LEN(email)/2) COLLATE Latin1_General_100_BIN2 = RIGHT(email,LEN(email)/2) COLLATE Latin1_General_100_BIN2
        AND DATALENGTH(email)=2*DATALENGTH(LEFT(email,LEN(email)/2))
        AND LEFT(email,LEN(email)/2) LIKE N'%_@_%._%';
    RETURN;
END;

-- Explicit row and exact old/new values are required before allowing a write.
IF @CustomerId IS NULL OR @CustomerId<=0 OR @CorrectEmail IS NULL OR @ExpectedEmail IS NULL
    OR @CorrectEmail NOT LIKE N'%_@_%._%'
    OR DATALENGTH(@ExpectedEmail)<>2*DATALENGTH(@CorrectEmail)
    OR @ExpectedEmail COLLATE Latin1_General_100_BIN2<>(@CorrectEmail+@CorrectEmail) COLLATE Latin1_General_100_BIN2
    THROW 51000, 'Provide a customer ID and exact duplicated/correct email values.', 1;

BEGIN TRY
    BEGIN TRANSACTION;
    UPDATE dbo.Customers WITH (UPDLOCK,HOLDLOCK) SET email=@CorrectEmail
    WHERE customer_id=@CustomerId AND DATALENGTH(email)=DATALENGTH(@ExpectedEmail)
        AND email COLLATE Latin1_General_100_BIN2=@ExpectedEmail COLLATE Latin1_General_100_BIN2;
    IF @@ROWCOUNT<>1 THROW 51001, 'Current email differs or customer does not exist; nothing changed.', 1;
    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT>0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
