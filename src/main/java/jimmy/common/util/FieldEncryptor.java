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
    private static final String DEV_KEY = "Logistics@DevKey";
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
                          @Value("${app.encrypt.require-key:false}") boolean requireKey) {
        this.enabled = enabled;
        if (enabled && requireKey && !StringUtils.hasText(key)) {
            throw new IllegalStateException("已开启敏感字段加密，但未配置 app.encrypt.key");
        }
        String effectiveKey = StringUtils.hasText(key) ? key : DEV_KEY;
        this.secretKey = new SecretKeySpec(normalizeKey(effectiveKey), ALGORITHM);
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
                    return cipherText;
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
            return cipherText;
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

    private static byte[] normalizeKey(String key) {
        byte[] raw = key.getBytes(StandardCharsets.UTF_8);
        byte[] normalized = new byte[16];
        System.arraycopy(raw, 0, normalized, 0, Math.min(raw.length, normalized.length));
        return normalized;
    }
}
