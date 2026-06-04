package jimmy.ai.service;

import org.springframework.util.StringUtils;

/**
 * AI 运行时模型配置 —— 可来自 Nacos，也可来自本地 Spring Environment。
 */
public record AiRuntimeProperties(String apiKey, String baseUrl, String model, String source) {

    public boolean configured() {
        return StringUtils.hasText(apiKey) && !"missing".equalsIgnoreCase(apiKey);
    }
}
