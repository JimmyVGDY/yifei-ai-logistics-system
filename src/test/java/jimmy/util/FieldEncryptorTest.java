package jimmy.util;

import jimmy.common.util.FieldEncryptor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FieldEncryptorTest {

    @Test
    void shouldEncryptNewValuesWithGcmAndDecryptLegacyValues() {
        FieldEncryptor encryptor = new FieldEncryptor(true, "Logistics@DevKey");

        String encrypted = encryptor.encrypt("13800000000");
        String legacyEncrypted = encryptor.legacyEncryptForLookup("13800000000");

        assertThat(encrypted).startsWith("ENCGCM:");
        assertThat(encrypted).isNotEqualTo(encryptor.encrypt("13800000000"));
        assertThat(encryptor.decrypt(encrypted)).isEqualTo("13800000000");
        assertThat(legacyEncrypted).startsWith("ENC:");
        assertThat(encryptor.decrypt(legacyEncrypted)).isEqualTo("13800000000");
        assertThat(encryptor.lookupHash("13800000000")).isEqualTo(encryptor.lookupHash("13800000000"));
    }

    @Test
    void shouldRejectWeakOrShortKeysWhenEncryptionEnabled() {
        assertThatThrownBy(() -> new FieldEncryptor(true, "abc"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("至少需要16");
        assertThatThrownBy(() -> new FieldEncryptor(true, "1234567890123456"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("过于常见");
    }

    @Test
    void shouldThrowWhenGcmCipherTextIsTampered() {
        FieldEncryptor encryptor = new FieldEncryptor(true, "Logistics@DevKey");
        String encrypted = encryptor.encrypt("13800000000");
        String tampered = encrypted.substring(0, encrypted.length() - 2) + "AA";

        assertThatThrownBy(() -> encryptor.decrypt(tampered))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("字段解密失败");
    }
}
