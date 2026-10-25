-- Run on the PostgreSQL 5432 primary in srghaccount. Keep legacy fields for rollback.
ALTER TABLE public.site_accounts
    ADD COLUMN IF NOT EXISTS accounts jsonb;

-- Existing passwords are already AES-GCM ciphertext; copy without decrypting or logging them.
UPDATE public.site_accounts
SET accounts = jsonb_build_array(jsonb_build_object(
    'label', '기본 계정',
    'account', COALESCE(account_id, ''),
    'password', COALESCE(password, '')))
WHERE accounts IS NULL
  AND (NULLIF(account_id, '') IS NOT NULL OR NULLIF(password, '') IS NOT NULL);

UPDATE public.site_accounts SET accounts = '[]'::jsonb WHERE accounts IS NULL;
ALTER TABLE public.site_accounts ALTER COLUMN accounts SET DEFAULT '[]'::jsonb;
ALTER TABLE public.site_accounts ALTER COLUMN accounts SET NOT NULL;
