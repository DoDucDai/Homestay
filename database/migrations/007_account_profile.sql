-- Optional for existing accounts until their first profile update. Never invent personal data.
IF COL_LENGTH('Users','phone') IS NULL ALTER TABLE Users ADD phone VARCHAR(10) NULL;
IF COL_LENGTH('Users','email') IS NULL ALTER TABLE Users ADD email NVARCHAR(254) NULL;
IF COL_LENGTH('Users','birth_date') IS NULL ALTER TABLE Users ADD birth_date DATE NULL;
IF COL_LENGTH('Users','city') IS NULL ALTER TABLE Users ADD city NVARCHAR(100) NULL;
