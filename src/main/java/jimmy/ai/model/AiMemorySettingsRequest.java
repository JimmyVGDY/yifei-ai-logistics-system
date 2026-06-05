package jimmy.ai.model;

/**
 * AI 长期记忆设置请求。
 */
public record AiMemorySettingsRequest(Boolean memoryEnabled, String answerStyle) {
}
