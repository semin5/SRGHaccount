package sarangit.semin5.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class SecretFieldCipherTest {
    private final SecretFieldCipher cipher = new SecretFieldCipher(
            Base64.getEncoder().encodeToString(new byte[32]));

    @Test
    void encryptsWithDifferentNoncesAndDecrypts() {
        String first = cipher.encrypt("비밀 1234");
        String second = cipher.encrypt("비밀 1234");
        assertThat(first).startsWith("enc:v1:").isNotEqualTo(second).doesNotContain("비밀");
        assertThat(cipher.decrypt(first)).isEqualTo("비밀 1234");
        assertThat(cipher.decrypt(second)).isEqualTo("비밀 1234");
        assertThat(cipher.decrypt("old plaintext")).isEqualTo("old plaintext");
    }

    @Test
    void wrongKeyFailsClosed() {
        byte[] otherKey = new byte[32];
        otherKey[0] = 1;
        var other = new SecretFieldCipher(Base64.getEncoder().encodeToString(otherKey));
        assertThatThrownBy(() -> other.decrypt(cipher.encrypt("비밀")))
                .isInstanceOf(IllegalStateException.class);
    }
}
