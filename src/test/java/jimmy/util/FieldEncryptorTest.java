package jimmy.util;

import jimmy.common.util.FieldEncryptor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
}
