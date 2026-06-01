package jimmy.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

/**
 * AES 敏感字段加密工具 —— 用于手机号、身份证号、银行卡号等字段的数据库存储加密。
 * <p>
 * 开关：{@code app.encrypt.enabled}（默认 true），关闭后明文存储，方便本地调试。
 * 密钥：{@code app.encrypt.key}，开发环境使用内置默认密钥，生产环境通过环境变量覆盖。
 * </p>
 * <p>
 * 密文格式：{@code ENC:(base64)}，前缀标识已加密数据，兼容存量明文逐步迁移。
 * </p>
 */
@Component
public class FieldEncryptor {

    private static final String ALGORITHM = "AES";
    private static final String ENC_PREFIX = "ENC:";
    /** 开发环境默认密钥（16 字符），生产环境务必通过 APP_ENCRYPT_KEY 覆盖 */
    private static final String DEV_KEY = "Logistics@DevKey";
    /** 需要加密的字段名集合 */
    private static final Set<String> ENCRYPTED_FIELDS = new HashSet<>(Arrays.asList(
            "mobile", "phone", "contact_phone"
    ));

    private static final Base64.Encoder BASE64_ENCODER = Base64.getEncoder();
    private static final Base64.Decoder BASE64_DECODER = Base64.getDecoder();

    private final SecretKeySpec secretKey;
    private final boolean enabled;

    public FieldEncryptor(@Value("${app.encrypt.enabled:true}") boolean enabled,
                          @Value("${app.encrypt.key:}") String key) {
        this.enabled = enabled;
        String effectiveKey = StringUtils.hasText(key) ? key : DEV_KEY;
        this.secretKey = new SecretKeySpec(effectiveKey.substring(0, Math.min(16, effectiveKey.length())).getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    /** 判断是否为需要加密的字段名 */
    public static boolean isEncryptedField(String columnName) {
        return ENCRYPTED_FIELDS.contains(columnName);
    }

    /**
     * 加密明文，返回 {@code ENC:base64} 格式密文。
     * 加密关闭或输入为空时返回原文。
     */
    public String encrypt(String plain) {
        if (!enabled || !StringUtils.hasText(plain)) {
            return plain;
        }
        // 已加密的数据不重复加密
        if (plain.startsWith(ENC_PREFIX)) {
            return plain;
        }
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return ENC_PREFIX + BASE64_ENCODER.encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("字段加密失败", e);
        }
    }

    /**
     * 解密密文，返回原文。
     * 加密关闭、输入为空或非加密数据时直接返回输入值。
     */
    public String decrypt(String cipherText) {
        if (!enabled || !StringUtils.hasText(cipherText) || !cipherText.startsWith(ENC_PREFIX)) {
            return cipherText;
        }
        try {
            String base64 = cipherText.substring(ENC_PREFIX.length());
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] decrypted = cipher.doFinal(BASE64_DECODER.decode(base64));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 解密失败时返回原文（兼容存量明文数据）
            return cipherText;
        }
    }
}
