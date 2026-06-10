package jimmy.ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 长期记忆向量编码器。
 * <p>
 * 优先使用 Spring AI EmbeddingModel（当前接入 Ollama + bge-m3，1024 维），
 * 如果模型不可用则自动降级为确定性哈希向量（128 维）。
 * <p>
 * 替换 Embedding 模型只需要修改 application.yml 的 ollama.embedding.model 配置项，
 * 不影响 MySQL 记忆表和 Qdrant 存储结构（除维度变化需重建 Collection 外）。
 */
@Component
public class AiMemoryVectorEncoder {

    private static final Logger log = LoggerFactory.getLogger(AiMemoryVectorEncoder.class);

    /**
     * 当前使用的向量维度。
     * <p>
     * bge-m3 模型输出 1024 维，哈希降级输出 128 维。
     * Qdrant Collection 需与此维度匹配。
     */
    public static final int VECTOR_SIZE = 1024;

    /** 哈希降级模式的向量维度 */
    private static final int FALLBACK_VECTOR_SIZE = 128;

    /**
     * Spring AI EmbeddingModel（Ollama 自动装配），不可用时为 null。
     * <p>
     * 项目同时引入了 OpenAI 和 Ollama 的 Embedding 自动装配，
     * 通过 {@code @Qualifier("ollamaEmbeddingModel")} 明确指定使用本地模型。
     */
    @Autowired(required = false)
    @Qualifier("ollamaEmbeddingModel")
    private EmbeddingModel embeddingModel;

    /**
     * 启动时输出向量编码模式，方便运维确认 Ollama 是否生效。
     */
    @PostConstruct
    public void logEncoderMode() {
        if (embeddingModel != null) {
            log.info("向量编码模式: Ollama bge-m3 (1024 维) — 已就绪");
        } else {
            log.warn("向量编码模式: 哈希降级 (128 → 1024 零填充) — EmbeddingModel 未注入，请检查 Ollama 服务");
        }
    }

    /**
     * 对文本进行向量化编码。
     * <p>
     * 优先调用 Ollama bge-m3 模型生产语义向量（1024 维），
     * 模型不可用时降级为确定性哈希向量（128 维，零填充到 1024 维以兼容 Collection 配置）。
     *
     * @param text 待编码文本
     * @return 1024 维归一化向量
     */
    public List<Double> encode(String text) {
        if (text == null || text.isBlank()) {
            text = "empty";
        }
        // 优先使用 Ollama Embedding 模型
        if (embeddingModel != null) {
            try {
                float[] embedding = embeddingModel.embed(text);
                if (embedding != null && embedding.length == VECTOR_SIZE) {
                    return toDoubleList(embedding);
                }
                log.warn("EmbeddingModel 返回异常维度: expected={}, actual={}，降级哈希",
                        VECTOR_SIZE, embedding != null ? embedding.length : 0);
            } catch (Exception e) {
                log.warn("EmbeddingModel 调用失败: {}，降级哈希编码", e.getMessage());
            }
        }
        // 降级：确定性哈希向量 + 零填充到 1024 维
        return fallbackEncode(text);
    }

    /**
     * 降级方案：确定性哈希向量（128 维），零填充到 1024 维。
     * <p>
     * 与旧版 {@link #encode(String)} 算法完全一致，只是将 128 维结果扩展为 1024 维
     * （前 128 维为哈希向量，后 896 维补齐 0），兼容 Qdrant 1024 维 Collection。
     */
    private List<Double> fallbackEncode(String text) {
        double[] vector = new double[FALLBACK_VECTOR_SIZE];
        String normalized = text.trim().toLowerCase();
        if (normalized.isEmpty()) {
            normalized = "empty";
        }
        for (int i = 0; i < normalized.length(); i++) {
            String token = normalized.substring(i, Math.min(normalized.length(), i + 2));
            byte[] hash = sha256(token);
            int bucket = Byte.toUnsignedInt(hash[0]) % FALLBACK_VECTOR_SIZE;
            double sign = (hash[1] & 1) == 0 ? 1.0 : -1.0;
            vector[bucket] += sign;
        }
        // L2 归一化
        double norm = 0.0;
        for (double v : vector) {
            norm += v * v;
        }
        norm = Math.sqrt(norm);
        // 输出 1024 维（前 128 维为哈希向量，后面补 0）
        List<Double> result = new ArrayList<>(VECTOR_SIZE);
        for (int i = 0; i < FALLBACK_VECTOR_SIZE; i++) {
            result.add(norm == 0 ? 0.0 : vector[i] / norm);
        }
        for (int i = FALLBACK_VECTOR_SIZE; i < VECTOR_SIZE; i++) {
            result.add(0.0);
        }
        return result;
    }

    /**
     * float[] → List&lt;Double&gt; 转换。
     */
    private List<Double> toDoubleList(float[] embedding) {
        List<Double> result = new ArrayList<>(embedding.length);
        for (float value : embedding) {
            result.add((double) value);
        }
        return result;
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", e);
        }
    }
}
