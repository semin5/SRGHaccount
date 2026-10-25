# Account list

Spring Boot and React account list based on the server list UI.

- URL: `/account/`; application listener: `13011` on each HA node.
- Database: `srghaccount` on the PostgreSQL 5432 VIP.
- Tables: `google_accounts`, `polaris`, `nas`, `corporate_phones`, `shared_certificates`, `physician_account_dates`, `site_accounts`.
- `/account` is served behind `/it` authentication. Passwords are encrypted in PostgreSQL and omitted from list responses; the detail API decrypts them for authorized site users.
- Runtime credentials belong in `/etc/SRGH_IT/account.env`, not in the project.
- Build: `npm ci && npm run build` in `frontend`, then `mvn package` at project root.

The imported workbook contains sensitive password and license data. Keep direct app-port access restricted and never log full records. Apply `deploy/site_accounts_pg5432.sql` on the PostgreSQL 5432 primary before deploying a build that includes the site-accounts tab.
