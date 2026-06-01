package jimmy.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * AES 敏感字段加密工具 —— 用于手机号、身份证号、银行卡号等字段的数据库存储加密。
 * <p>
 * 密钥通过环境变量 {@code APP_ENCRYPT_KEY} 配置，至少 16 个字符（对应 AES-128）。
 * 默认使用空密钥时直接返回原文，适合本地开发跳过加密。
 * </p>
 * <p>
 * 密文格式：{@code ENC(base64)}，以 {@code ENC} 前缀标识已加密数据，
 * 兼容存量明文数据的逐步迁移。
 * </p>
 */
@Component
public class FieldEncryptor {

    private static final String ALGORITHM = "AES";
    private static final String ENC_PREFIX = "ENC:";
    private static final Base64.Encoder BASE64_ENCODER = Base64.getEncoder();
    private static final Base64.Decoder BASE64_DECODER = Base64.getDecoder();

    private final SecretKeySpec secretKey;
    private final boolean enabled;

    public FieldEncryptor(@Value("${app.encrypt.key:}") String key) {
        if (StringUtils.hasText(key) && key.length() >= 16) {
            this.secretKey = new SecretKeySpec(key.substring(0, 16).getBytes(StandardCharsets.UTF_8), ALGORITHM);
            this.enabled = true;
        } else {
            this.secretKey = null;
            this.enabled = false;
        }
    }

    /**
     * 加密明文，返回 {@code ENC:base64} 格式密文。
     * 未配置密钥或输入为空时返回原文。
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
     * 未配置密钥、输入为空或非加密数据时直接返回输入值。
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
            throw new RuntimeException("字段解密失败", e);
        }
    }
}
