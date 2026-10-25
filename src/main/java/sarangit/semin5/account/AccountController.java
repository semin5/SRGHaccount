package sarangit.semin5.account;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class AccountController {
    private static final Map<String, TableSpec> TABLES = Map.of(
            "google", new TableSpec("google_accounts", List.of(
                    "number", "account", "password", "department", "purpose", "hancom_docs_quantity",
                    "user_1", "user_2", "user_3", "user_4", "user_5"),
                    List.of("number", "account", "department", "purpose", "hancom_docs_quantity",
                            "user_1", "user_2", "user_3", "user_4", "user_5")),
            "polaris", new TableSpec("polaris", List.of(
                    "pc", "department", "name", "building", "floor", "computer_name", "license",
                    "purchase_date", "ip", "installation_date", "remarks"),
                    List.of("pc", "department", "name", "building", "floor", "ip", "remarks")),
            "nas", new TableSpec("nas", List.of("department", "folder_name", "password"),
                    List.of("department", "folder_name")),
            "site-accounts", new TableSpec("site_accounts", List.of(
                    "name", "site", "remarks"),
                    List.of("name", "site", "remarks")),
            "corporate-phones", new TableSpec("corporate_phones", List.of(
                    "organization_level_1", "organization_level_2", "organization_level_3", "organization_level_4",
                    "mobile_number", "name", "position", "email", "landline_number", "extension_number",
                    "previous_carrier", "replacement_or_new", "identity_service_password", "google_account_email",
                    "google_account_password", "office_mobile_password", "additional_password",
                    "activation_status"),
                    List.of("organization_level_2", "organization_level_3", "organization_level_4",
                            "mobile_number", "name", "email", "extension_number", "google_account_email")),
            "certificates", new TableSpec("shared_certificates", List.of(
                    "task_name", "site", "ip", "account_id", "password"),
                    List.of("task_name", "site", "ip", "account_id")),
            "physicians", new TableSpec("physician_account_dates", List.of(
                    "userpidn", "username", "userdept", "userfrdt", "usertodt", "usersbdp",
                    "userrsdt", "userpwdt", "userdoct", "userdpds", "userdtid", "userspls", "usercomt"),
                    List.of("userpidn", "username", "userdept", "userfrdt", "userpwdt",
                            "userdoct", "userdpds", "userdtid", "userspls")));
    private static final Set<String> SECRET_COLUMNS = Set.of("password", "identity_service_password",
            "google_account_password", "office_mobile_password", "additional_password");
    private final JdbcTemplate jdbc;
    private final SecretFieldCipher cipher;

    public AccountController(JdbcTemplate jdbc, SecretFieldCipher cipher) {
        this.jdbc = jdbc;
        this.cipher = cipher;
    }

    private TableSpec table(String tab) {
        TableSpec spec = TABLES.get(tab);
        if (spec == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return spec;
    }

    @GetMapping("/{tab}")
    public ResponseEntity<List<Map<String, Object>>> list(@PathVariable String tab) {
        TableSpec spec = table(tab);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(listRows(spec));
    }

    private List<Map<String, Object>> listRows(TableSpec spec) {
        boolean polaris = spec.name().equals("polaris");
        String columns = String.join(", ", spec.listColumns()) + (polaris ? ", license" : "");
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT source_row, " + columns
                + " FROM " + spec.name() + " ORDER BY source_row");
        if (polaris) {
            Map<String, Integer> groups = new LinkedHashMap<>();
            for (Map<String, Object> row : rows) {
                Object license = row.remove("license");
                String key = license == null ? "" : license.toString().trim();
                row.put("license_group", groups.computeIfAbsent(key, ignored -> groups.size() + 1));
                row.put("license_group_name", key);
            }
        }
        return rows;
    }

    @GetMapping("/{tab}/{id}")
    public ResponseEntity<Map<String, Object>> detail(@PathVariable String tab, @PathVariable int id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(find(table(tab), id));
    }

    private Map<String, Object> find(TableSpec spec, int id) {
        boolean siteAccounts = spec.name().equals("site_accounts");
        Map<String, Object> row = jdbc.queryForList("SELECT source_row, " + String.join(", ", spec.columns())
                + (siteAccounts ? ", accounts" : "")
                + " FROM " + spec.name() + " WHERE source_row = ?", id).stream()
                .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var result = new LinkedHashMap<>(row);
        for (String column : spec.columns()) {
            if (SECRET_COLUMNS.contains(column) && result.get(column) instanceof String value) {
                result.put(column, cipher.decrypt(value));
            }
        }
        if (siteAccounts) result.put("accounts", SiteAccounts.read(result.get("accounts"), cipher));
        return result;
    }

    private Map<String, Object> findListRow(TableSpec spec, int id) {
        if (spec.name().equals("polaris")) {
            return listRows(spec).stream()
                    .filter(row -> ((Number) row.get("source_row")).intValue() == id)
                    .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        }
        return jdbc.queryForList("SELECT source_row, " + String.join(", ", spec.listColumns())
                + " FROM " + spec.name() + " WHERE source_row = ?", id).stream()
                .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PutMapping(value = "/polaris/group/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public Map<String, Object> updatePolarisGroup(@PathVariable int id,
                                                   @RequestBody Map<String, Object> values) {
        Object raw = values.get("license");
        String next = raw == null ? "" : raw.toString().trim();
        if (next.isEmpty() || next.length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "License is required (up to 255 characters)");
        }
        Object existing = find(table("polaris"), id).get("license");
        String current = existing == null ? "" : existing.toString().trim();
        Object expected = values.get("expectedLicense");
        if (expected == null || !current.equals(expected.toString().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "License group changed; reload and retry");
        }
        if (!next.equals(current)) {
            Integer duplicate = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM polaris WHERE TRIM(COALESCE(license, '')) = ?",
                    Integer.class, next);
            if (duplicate != null && duplicate > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "License group already exists");
            }
        }
        int updated = jdbc.update(
                "UPDATE polaris SET license = ? WHERE TRIM(COALESCE(license, '')) = ?",
                next, current);
        return Map.of("updated", updated);
    }

    private Object value(Map<String, Object> values, String column) {
        Object raw = values.get(column);
        if (raw == null || raw.toString().isBlank()) return null;
        if (column.equals("number") || column.equals("hancom_docs_quantity") || column.equals("floor")) {
            try {
                return Integer.valueOf(raw.toString().trim());
            } catch (NumberFormatException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, column + " must be a number");
            }
        }
        String text = raw.toString();
        if (text.length() > 255) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Value is too long");
        return SECRET_COLUMNS.contains(column) ? cipher.encrypt(text) : text;
    }

    @PostMapping(value = "/{tab}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> create(@PathVariable String tab, @RequestBody Map<String, Object> values) {
        TableSpec spec = table(tab);
        boolean siteAccounts = spec.name().equals("site_accounts");
        List<Object> parameters = new ArrayList<>(spec.columns().stream().map(column -> value(values, column)).toList());
        if (siteAccounts) parameters.add(SiteAccounts.write(values.get("accounts"), cipher));
        String columns = String.join(", ", spec.columns()) + (siteAccounts ? ", accounts" : "");
        String placeholders = String.join(", ", spec.columns().stream().map(ignored -> "?").toList())
                + (siteAccounts ? ", ?::jsonb" : "");
        for (int attempt = 0; attempt < 3; attempt++) {
            Integer id = jdbc.queryForObject("SELECT COALESCE(MAX(source_row), 0) + 1 FROM " + spec.name(), Integer.class);
            List<Object> insert = new ArrayList<>();
            insert.add(id);
            insert.addAll(parameters);
            try {
                jdbc.update("INSERT INTO " + spec.name() + " (source_row, " + columns
                        + ") VALUES (?, " + placeholders + ")", insert.toArray());
                return findListRow(spec, id);
            } catch (DuplicateKeyException exception) {
                if (attempt == 2) throw new ResponseStatusException(HttpStatus.CONFLICT);
            }
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT);
    }

    @PutMapping(value = "/{tab}/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> update(@PathVariable String tab, @PathVariable int id,
                                      @RequestBody Map<String, Object> values) {
        TableSpec spec = table(tab);
        boolean siteAccounts = spec.name().equals("site_accounts");
        List<Object> parameters = new ArrayList<>();
        for (String column : spec.columns()) parameters.add(value(values, column));
        if (siteAccounts) parameters.add(SiteAccounts.write(values.get("accounts"), cipher));
        parameters.add(id);
        String assignments = String.join(", ", spec.columns().stream().map(column -> column + " = ?").toList())
                + (siteAccounts ? ", accounts = ?::jsonb" : "");
        if (jdbc.update("UPDATE " + spec.name() + " SET " + assignments + " WHERE source_row = ?",
                parameters.toArray()) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return findListRow(spec, id);
    }

    @DeleteMapping("/{tab}/{id}")
    public void delete(@PathVariable String tab, @PathVariable int id) {
        TableSpec spec = table(tab);
        if (jdbc.update("DELETE FROM " + spec.name() + " WHERE source_row = ?", id) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    private record TableSpec(String name, List<String> columns, List<String> listColumns) {}
}
