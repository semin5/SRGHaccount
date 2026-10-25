package sarangit.semin5.account;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SecretFieldCipher {
    private static final String PREFIX = "enc:v1:";
    private static final int NONCE_LENGTH = 12;
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public SecretFieldCipher(@Value("${APP_DATA_ENCRYPTION_KEY}") String encodedKey) {
        byte[] bytes = Base64.getDecoder().decode(encodedKey);
        if (bytes.length != 32) throw new IllegalArgumentException("APP_DATA_ENCRYPTION_KEY must be 32 bytes");
        key = new SecretKeySpec(bytes, "AES");
    }

    public String encrypt(String value) {
        if (value == null || value.isEmpty()) return value;
        byte[] nonce = new byte[NONCE_LENGTH];
        random.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            byte[] ciphertext = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(ByteBuffer.allocate(nonce.length + ciphertext.length)
                            .put(nonce).put(ciphertext).array());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to encrypt secret field", exception);
        }
    }

    public String decrypt(String value) {
        if (value == null || !value.startsWith(PREFIX)) return value; // Existing rows during migration.
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(value.substring(PREFIX.length()));
            if (bytes.length < NONCE_LENGTH + 16) throw new IllegalArgumentException("Invalid ciphertext");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key,
                    new GCMParameterSpec(128, bytes, 0, NONCE_LENGTH));
            return new String(cipher.doFinal(bytes, NONCE_LENGTH, bytes.length - NONCE_LENGTH),
                    StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Unable to decrypt secret field", exception);
        }
    }

    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }
}
