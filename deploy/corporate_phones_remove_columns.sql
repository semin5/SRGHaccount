-- Apply on the PostgreSQL 5432 primary after backing up srghaccount and
-- deploying an application build that no longer references these columns.
BEGIN;
ALTER TABLE public.corporate_phones
    DROP COLUMN IF EXISTS office_mobile_login_info,
    DROP COLUMN IF EXISTS additional_email;
COMMIT;
