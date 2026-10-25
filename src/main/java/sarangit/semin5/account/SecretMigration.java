package sarangit.semin5.account;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SecretMigration implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final SecretFieldCipher cipher;

    public SecretMigration(JdbcTemplate jdbc, SecretFieldCipher cipher) {
        this.jdbc = jdbc;
        this.cipher = cipher;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        migrate("google_accounts");
        migrate("nas");
        migrate("corporate_phones", "identity_service_password", "google_account_password",
                "office_mobile_password", "additional_password");
        migrate("shared_certificates", "password");
        migrate("site_accounts", "password");
    }

    private void migrate(String table) {
        migrate(table, "password");
    }

    private void migrate(String table, String... columns) {
        for (String column : columns) migrateColumn(table, column);
    }

    private void migrateColumn(String table, String column) {
        int updated = 0;
        for (var row : jdbc.queryForList("SELECT source_row, " + column + " FROM " + table
                + " WHERE " + column + " IS NOT NULL AND " + column + " <> ''")) {
            String value = (String) row.get(column);
            if (cipher.isEncrypted(value)) {
                cipher.decrypt(value); // Fail if a different key or a damaged value is present.
                continue;
            }
            updated += jdbc.update("UPDATE " + table + " SET " + column + " = ? "
                            + "WHERE source_row = ? AND " + column + " = ?",
                    cipher.encrypt(value), row.get("source_row"), value);
        }
        System.out.println(table + "." + column + " secret fields migrated: " + updated);
    }
}
