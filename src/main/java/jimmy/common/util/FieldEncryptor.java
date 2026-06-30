package jimmy.common.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 敏感字段加密工具。
 * <p>
 * 新写入数据使用 AES-GCM 随机 IV 加密，旧的 {@code ENC:} AES 数据继续保留解密兼容。
 * 对手机号这类需要查重的字段，使用 {@link #lookupHash(String)} 生成不可逆查询摘要。
 */
@Component
public class FieldEncryptor {

    private static final String ALGORITHM = "AES";
    private static final String LEGACY_TRANSFORMATION = "AES";
    private static final String GCM_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String LEGACY_PREFIX = "ENC:";
    private static final String GCM_PREFIX = "ENCGCM:";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int REQUIRED_KEY_LENGTH = 16;
    private static final Set<String> ENCRYPTED_FIELDS = new HashSet<>(Arrays.asList(
            "mobile", "phone", "contact_phone"
    ));
    private static final Base64.Encoder BASE64_ENCODER = Base64.getEncoder();
    private static final Base64.Decoder BASE64_DECODER = Base64.getDecoder();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final SecretKeySpec secretKey;
    private final boolean enabled;

    @Autowired
    public FieldEncryptor(@Value("${app.encrypt.enabled:true}") boolean enabled,
                          @Value("${app.encrypt.key:}") String key,
                          @Value("${app.encrypt.require-key:true}") boolean requireKey) {
        this.enabled = enabled;
        if (enabled && !StringUtils.hasText(key)) {
            throw new IllegalStateException("加密已启用但未配置 app.encrypt.key，请通过环境变量 APP_ENCRYPT_KEY 设置（建议至少16字符）");
        }
        if (enabled) {
            validateKey(key);
        }
        this.secretKey = StringUtils.hasText(key)
                ? new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), ALGORITHM)
                : null;
    }

    public FieldEncryptor(boolean enabled, String key) {
        this(enabled, key, false);
    }

    public static boolean isEncryptedField(String columnName) {
        return ENCRYPTED_FIELDS.contains(columnName);
    }

    public String encrypt(String plain) {
        if (!enabled || !StringUtils.hasText(plain) || isEncryptedValue(plain)) {
            return plain;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            SECURE_RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(GCM_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return GCM_PREFIX + BASE64_ENCODER.encodeToString(payload);
        } catch (Exception e) {
            throw new RuntimeException("字段加密失败", e);
        }
    }

    public String decrypt(String cipherText) {
        if (!enabled || !StringUtils.hasText(cipherText)) {
            return cipherText;
        }
        try {
            if (cipherText.startsWith(GCM_PREFIX)) {
                byte[] payload = BASE64_DECODER.decode(cipherText.substring(GCM_PREFIX.length()));
                if (payload.length <= GCM_IV_LENGTH) {
                    throw new IllegalArgumentException("GCM 密文格式不完整");
                }
                byte[] iv = Arrays.copyOfRange(payload, 0, GCM_IV_LENGTH);
                byte[] encrypted = Arrays.copyOfRange(payload, GCM_IV_LENGTH, payload.length);
                Cipher cipher = Cipher.getInstance(GCM_TRANSFORMATION);
                cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
                return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
            }
            if (cipherText.startsWith(LEGACY_PREFIX)) {
                Cipher cipher = Cipher.getInstance(LEGACY_TRANSFORMATION);
                cipher.init(Cipher.DECRYPT_MODE, secretKey);
                byte[] decrypted = cipher.doFinal(BASE64_DECODER.decode(cipherText.substring(LEGACY_PREFIX.length())));
                return new String(decrypted, StandardCharsets.UTF_8);
            }
            return cipherText;
        } catch (Exception e) {
            throw new IllegalArgumentException("字段解密失败，密文可能被篡改或密钥不匹配", e);
        }
    }

    public String legacyEncryptForLookup(String plain) {
        if (!enabled || !StringUtils.hasText(plain) || isEncryptedValue(plain)) {
            return plain;
        }
        try {
            Cipher cipher = Cipher.getInstance(LEGACY_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            return LEGACY_PREFIX + BASE64_ENCODER.encodeToString(cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException("字段兼容查询密文生成失败", e);
        }
    }

    public String lookupHash(String plain) {
        if (!enabled || !StringUtils.hasText(plain)) {
            return plain;
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secretKey.getEncoded(), HMAC_ALGORITHM));
            return BASE64_ENCODER.encodeToString(mac.doFinal(plain.trim().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException("字段查询摘要生成失败", e);
        }
    }

    private static boolean isEncryptedValue(String value) {
        return value.startsWith(LEGACY_PREFIX) || value.startsWith(GCM_PREFIX);
    }

    private static void validateKey(String key) {
        if (!StringUtils.hasText(key) || key.getBytes(StandardCharsets.UTF_8).length < REQUIRED_KEY_LENGTH) {
            throw new IllegalStateException("app.encrypt.key 至少需要16个UTF-8字节，禁止短密钥零填充");
        }
        int length = key.getBytes(StandardCharsets.UTF_8).length;
        if (length != 16 && length != 24 && length != 32) {
            throw new IllegalStateException("app.encrypt.key 长度必须为16、24或32个UTF-8字节，以匹配 AES-128/192/256");
        }
        String trimmed = key.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.contains("password") || lower.contains("123456") || lower.contains("qwerty")
                || lower.contains("changeme") || lower.contains("default")) {
            throw new IllegalStateException("app.encrypt.key 过于常见，请使用随机高强度密钥");
        }
        long distinct = trimmed.chars().distinct().count();
        if (distinct < 8 || isRepeatedPattern(trimmed)) {
            throw new IllegalStateException("app.encrypt.key 熵过低，请使用包含大小写、数字或符号的随机密钥");
        }
    }

    private static boolean isRepeatedPattern(String key) {
        for (int size = 1; size <= key.length() / 2; size++) {
            if (key.length() % size != 0) {
                continue;
            }
            String pattern = key.substring(0, size);
            StringBuilder repeated = new StringBuilder(key.length());
            while (repeated.length() < key.length()) {
                repeated.append(pattern);
            }
            if (repeated.toString().equals(key)) {
                return true;
            }
        }
        return false;
    }
}
