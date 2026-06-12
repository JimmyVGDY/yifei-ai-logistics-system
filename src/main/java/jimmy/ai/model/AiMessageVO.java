package jimmy.ai.model;

/**
 * AI 会话消息 —— 仅保存脱敏后的用户问题和系统回答摘要。
 */
public record AiMessageVO(
        String messageId,
        String conversationId,
        String role,
        String content,
        String status,
        String time,
        String traceId,
        String operationId,
        String loginSessionId) {

    public AiMessageVO(String role, String content, String time) {
        this(null, null, role, content, "SUCCESS", time, null, null, null);
    }
}
