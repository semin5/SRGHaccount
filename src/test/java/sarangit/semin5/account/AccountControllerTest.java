package sarangit.semin5.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

class AccountControllerTest {
    private final SecretFieldCipher cipher = new SecretFieldCipher(
            Base64.getEncoder().encodeToString(new byte[32]));

    private AccountController controller(JdbcTemplate jdbc) {
        return new AccountController(jdbc, cipher);
    }

    @Test
    void createStoresNasPasswordEncrypted() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(7);
        when(jdbc.queryForList(anyString(), eq(7))).thenReturn(List.of(Map.of("source_row", 7)));

        controller(jdbc).create("nas", Map.of("department", "IT", "folder_name", "shared",
                "password", "plain-secret"));

        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(contains("INSERT INTO nas"), arguments.capture());
        String stored = (String) arguments.getValue()[3];
        assertThat(stored).startsWith("enc:v1:").doesNotContain("plain-secret");
        assertThat(cipher.decrypt(stored)).isEqualTo("plain-secret");
    }

    @Test
    void siteAccountStoresMultipleEncryptedPasswordsAndKeepsCredentialsOutOfList() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(1);
        when(jdbc.queryForList(anyString(), eq(1))).thenReturn(List.of(Map.of("source_row", 1)));
        when(jdbc.queryForList(anyString())).thenReturn(List.of(Map.of(
                "source_row", 1, "name", "업무 사이트", "site", "https://example.test", "remarks", "비고")));

        AccountController controller = controller(jdbc);
        controller.create("site-accounts", Map.of("name", "업무 사이트", "site", "https://example.test",
                "remarks", "비고", "accounts", List.of(
                        Map.of("label", "관리자", "account", "user1", "password", "plain-secret"),
                        Map.of("label", "운영", "account", "user2", "password", "other-secret"))));

        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(contains("INSERT INTO site_accounts"), arguments.capture());
        String stored = (String) arguments.getValue()[4];
        assertThat(stored).contains("관리자", "운영").doesNotContain("plain-secret", "other-secret");
        assertThat(SiteAccounts.read(stored, cipher)).extracting(entry -> entry.get("password"))
                .containsExactly("plain-secret", "other-secret");

        assertThat(controller.list("site-accounts").getBody()).hasSize(1)
                .allSatisfy(row -> assertThat(row).doesNotContainKeys("account_id", "password", "accounts"));
        ArgumentCaptor<String> listSql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(listSql.capture());
        assertThat(listSql.getValue()).contains("FROM site_accounts");
        assertThat(listSql.getValue().split(" FROM ")[0])
                .doesNotContain("account_id", "password", "accounts");
    }

    @Test
    void siteAccountDetailDecryptsAccounts() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(2))).thenReturn(List.of(Map.of(
                "source_row", 2, "accounts", SiteAccounts.write(List.of(
                        Map.of("label", "기본 계정", "account", "user2", "password", "secret2")), cipher))));

        assertThat(controller(jdbc).detail("site-accounts", 2).getBody())
                .containsEntry("accounts", List.of(Map.of(
                        "label", "기본 계정", "account", "user2", "password", "secret2", "content", "")));
    }

    @Test
    void siteAccountSupportsDomainAndPasswordOnlyEntries() {
        var entries = List.of(
                Map.of("label", "도메인", "content", "saranghp.co.kr"),
                Map.of("label", "웹 호스팅", "account", "sarangmc", "password", "secret-host"),
                Map.of("label", "FTP", "password", "secret-ftp"));

        String stored = SiteAccounts.write(entries, cipher);
        assertThat(stored).contains("saranghp.co.kr").doesNotContain("secret-host", "secret-ftp");
        assertThat(SiteAccounts.read(stored, cipher)).containsExactly(
                Map.of("label", "도메인", "account", "", "password", "", "content", "saranghp.co.kr"),
                Map.of("label", "웹 호스팅", "account", "sarangmc", "password", "secret-host", "content", ""),
                Map.of("label", "FTP", "account", "", "password", "secret-ftp", "content", ""));
        assertThatThrownBy(() -> SiteAccounts.write(List.of(Map.of("label", "빈 항목")), cipher))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void siteAccountUpdateStoresEncryptedAccounts() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), eq("업무 사이트"), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any(), eq(2))).thenReturn(1);
        when(jdbc.queryForList(anyString(), eq(2))).thenReturn(List.of(Map.of("source_row", 2)));

        controller(jdbc).update("site-accounts", 2, Map.of("name", "업무 사이트", "accounts", List.of(
                Map.of("label", "관리자", "account", "user1", "password", "new-secret"))));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> encryptedAccounts = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(sql.capture(), eq("업무 사이트"), eq(null), eq(null), encryptedAccounts.capture(), eq(2));
        assertThat(sql.getValue()).contains("accounts = ?::jsonb");
        assertThat(SiteAccounts.read(encryptedAccounts.getValue(), cipher)).extracting(entry -> entry.get("password"))
                .containsExactly("new-secret");
    }

    @Test
    void corporatePhoneOmitsRemovedColumnsAndDecryptsAccountPassword() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(3))).thenReturn(List.of(Map.of(
                "source_row", 3, "mobile_number", "010-1234-5678",
                "email", "example@example.test", "additional_password", cipher.encrypt("secret"))));

        Map<String, Object> detail = controller(jdbc).detail("corporate-phones", 3).getBody();
        assertThat(detail).containsEntry("mobile_number", "010-1234-5678")
                .containsEntry("email", "example@example.test")
                .containsEntry("additional_password", "secret");
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sql.capture(), eq(3));
        assertThat(sql.getValue()).contains("FROM corporate_phones", "additional_password")
                .doesNotContain("office_mobile_login_info", "additional_email");
    }

    @Test
    void listDoesNotSelectSecretColumns() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString())).thenReturn(List.of(Map.of("source_row", 2, "account", "example")));
        AccountController controller = controller(jdbc);

        assertThat(controller.list("google").getBody()).hasSize(1);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sql.capture());
        assertThat(sql.getValue()).contains("FROM google_accounts").doesNotContain("password");
    }

    @Test
    void detailReadsFullRecordOnlyWhenRequested() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(2))).thenReturn(List.of(Map.of(
                "source_row", 2, "password", cipher.encrypt("example"))));
        AccountController controller = controller(jdbc);

        assertThat(controller.detail("google", 2).getBody()).containsEntry("password", "example");
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sql.capture(), eq(2));
        assertThat(sql.getValue()).contains("password");
    }

    @Test
    void polarisListGroupsMatchingLicensesAndNamesEachGroup() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString())).thenReturn(List.of(
                new LinkedHashMap<>(Map.of("source_row", 1, "license", "secret-a", "remarks", "memo")),
                new LinkedHashMap<>(Map.of("source_row", 2, "license", "secret-b", "remarks", "")),
                new LinkedHashMap<>(Map.of("source_row", 3, "license", "secret-a", "remarks", ""))));
        AccountController controller = controller(jdbc);

        List<Map<String, Object>> rows = controller.list("polaris").getBody();
        assertThat(rows).extracting(row -> row.get("license_group")).containsExactly(1, 2, 1);
        assertThat(rows).allSatisfy(row -> assertThat(row).doesNotContainKey("license"));
        assertThat(rows).extracting(row -> row.get("license_group_name"))
                .containsExactly("secret-a", "secret-b", "secret-a");
        assertThat(rows.get(0)).containsEntry("remarks", "memo");
    }

    @Test
    void changingLicenseGroupUpdatesAllMatchingRows() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(2))).thenReturn(List.of(Map.of("source_row", 2, "license", "old")));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("new"))).thenReturn(0);
        when(jdbc.update(anyString(), eq("new"), eq("old"))).thenReturn(3);
        AccountController controller = controller(jdbc);

        assertThat(controller.updatePolarisGroup(2, Map.of("license", "new", "expectedLicense", "old")))
                .containsEntry("updated", 3);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(sql.capture(), eq("new"), eq("old"));
        assertThat(sql.getValue()).contains("UPDATE polaris SET license").contains("TRIM(COALESCE");
    }

    @Test
    void changingLicenseGroupRejectsAnExistingGroup() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(2))).thenReturn(List.of(Map.of("source_row", 2, "license", "old")));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("new"))).thenReturn(1);
        AccountController controller = controller(jdbc);

        assertThatThrownBy(() -> controller.updatePolarisGroup(2, Map.of("license", "new", "expectedLicense", "old")))
                .isInstanceOf(ResponseStatusException.class);
        verify(jdbc, never()).update(anyString(), eq("new"), eq("old"));
    }

    @Test
    void changingLicenseGroupRejectsAStaleGroup() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(2))).thenReturn(List.of(Map.of("source_row", 2, "license", "changed")));
        AccountController controller = controller(jdbc);

        assertThatThrownBy(() -> controller.updatePolarisGroup(2,
                Map.of("license", "new", "expectedLicense", "old")))
                .isInstanceOf(ResponseStatusException.class);
        verify(jdbc, never()).update(anyString(), eq("new"), eq("changed"));
    }
}
