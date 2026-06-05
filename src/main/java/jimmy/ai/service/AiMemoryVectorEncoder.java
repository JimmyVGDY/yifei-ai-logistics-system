package jimmy.ai.service;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 长期记忆本地向量编码器。
 * <p>
 * 当前项目接入的 OpenAI 兼容模型不一定提供 Embedding 接口，所以第一版使用确定性哈希向量作为兜底。
 * 后续如接入 Spring AI EmbeddingModel，只需要替换本类实现，不影响 MySQL 记忆表和 Qdrant 存储结构。
 */
@Component
public class AiMemoryVectorEncoder {

    public static final int VECTOR_SIZE = 128;

    public List<Double> encode(String text) {
        double[] vector = new double[VECTOR_SIZE];
        String normalized = text == null ? "" : text.trim().toLowerCase();
        if (normalized.isEmpty()) {
            normalized = "empty";
        }
        for (int i = 0; i < normalized.length(); i++) {
            String token = normalized.substring(i, Math.min(normalized.length(), i + 2));
            byte[] hash = sha256(token);
            int bucket = Byte.toUnsignedInt(hash[0]) % VECTOR_SIZE;
            double sign = (hash[1] & 1) == 0 ? 1.0 : -1.0;
            vector[bucket] += sign;
        }
        double norm = 0.0;
        for (double value : vector) {
            norm += value * value;
        }
        norm = Math.sqrt(norm);
        List<Double> result = new ArrayList<>(VECTOR_SIZE);
        for (double value : vector) {
            result.add(norm == 0 ? 0.0 : value / norm);
        }
        return result;
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }
}
