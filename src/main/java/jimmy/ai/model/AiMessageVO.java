package jimmy.ai.model;

/**
 * AI 会话消息 —— 仅保存脱敏后的用户问题和系统回答摘要。
 */
public record AiMessageVO(String role, String content, String time) {
}
