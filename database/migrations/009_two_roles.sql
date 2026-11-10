-- Only admin and customer remain. Keep account ids, passwords and guest/history links.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
SELECT COUNT(*) FROM dbo.Users WITH(TABLOCKX,HOLDLOCK);

-- Revoke existing sessions only for accounts whose authority changes.
UPDATE dbo.Users SET role='customer',auth_version=auth_version+1
WHERE LOWER(LTRIM(RTRIM(role))) IN('staff','member');

DECLARE @roleDefault SYSNAME;
SELECT @roleDefault=d.name FROM sys.default_constraints d
JOIN sys.columns c ON c.object_id=d.parent_object_id AND c.column_id=d.parent_column_id
WHERE d.parent_object_id=OBJECT_ID('dbo.Users') AND c.name='role';
IF @roleDefault IS NOT NULL
BEGIN
    DECLARE @dropDefault NVARCHAR(MAX)='ALTER TABLE dbo.Users DROP CONSTRAINT '+QUOTENAME(@roleDefault);
    EXEC(@dropDefault);
END;
ALTER TABLE dbo.Users ADD CONSTRAINT DF_Users_Role DEFAULT 'customer' FOR role;

IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID('dbo.Users') AND name='CK_Users_Role')
    ALTER TABLE dbo.Users WITH CHECK ADD CONSTRAINT CK_Users_Role CHECK(role IN('admin','customer'));
ALTER TABLE dbo.Users WITH CHECK CHECK CONSTRAINT CK_Users_Role;
COMMIT;
