package sarangit.semin5.account;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class SiteAccounts {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<Map<String, String>>> TYPE = new TypeReference<>() {};

    private SiteAccounts() {}

    static List<Map<String, String>> read(Object value, SecretFieldCipher cipher) {
        if (value == null) return List.of();
        try {
            List<Map<String, String>> stored = JSON.readValue(value.toString(), TYPE);
            List<Map<String, String>> result = new ArrayList<>();
            for (Map<String, String> entry : stored) {
                Map<String, String> account = new LinkedHashMap<>();
                account.put("label", entry.getOrDefault("label", ""));
                account.put("account", entry.getOrDefault("account", ""));
                account.put("password", cipher.decrypt(entry.getOrDefault("password", "")));
                account.put("content", entry.getOrDefault("content", ""));
                result.add(account);
            }
            return result;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid site accounts JSON", exception);
        }
    }

    static String write(Object value, SecretFieldCipher cipher) {
        if (!(value instanceof List<?> input) || input.size() > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "계정은 최대 50개까지 추가할 수 있습니다.");
        }
        List<Map<String, String>> stored = new ArrayList<>();
        for (Object item : input) {
            if (!(item instanceof Map<?, ?> entry)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "계정 형식이 올바르지 않습니다.");
            }
            String label = field(entry, "label");
            String account = field(entry, "account");
            String password = entry.get("password") instanceof String text ? text : "";
            String content = field(entry, "content");
            if (label.isEmpty() || (account.isEmpty() && password.isBlank() && content.isEmpty())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "명칭과 계정·암호·내용 중 하나 이상을 입력해 주세요.");
            }
            if (label.length() > 200 || account.length() > 2000 || password.length() > 2000
                    || content.length() > 2000) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "계정 정보가 너무 깁니다.");
            }
            stored.add(Map.of("label", label, "account", account, "password", cipher.encrypt(password),
                    "content", content));
        }
        try {
            return JSON.writeValueAsString(stored);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize site accounts", exception);
        }
    }

    private static String field(Map<?, ?> entry, String name) {
        Object value = entry.get(name);
        return value instanceof String text ? text.trim() : "";
    }
}
