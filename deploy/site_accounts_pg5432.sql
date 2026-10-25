-- Run on the PostgreSQL 5432 primary in database srghaccount.
-- Patroni streaming replication carries the table to the standby.
CREATE TABLE IF NOT EXISTS public.site_accounts (
    source_row integer PRIMARY KEY,
    name varchar(255),
    site varchar(255),
    remarks varchar(255),
    account_id varchar(255),
    password text
);

GRANT SELECT, INSERT, UPDATE, DELETE ON public.site_accounts TO semin5;
GRANT SELECT, UPDATE ON public.site_accounts TO "99241029";
