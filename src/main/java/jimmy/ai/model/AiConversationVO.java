package jimmy.ai.model;

import java.util.List;

/**
 * AI 会话视图 —— MySQL 持久化后，Redis 只作为最近上下文缓存。
 */
public record AiConversationVO(
        String conversationId,
        String title,
        String status,
        String createdAt,
        String updatedAt,
        String archivedAt,
        Integer messageCount,
        String contextSnapshot,
        List<AiMessageVO> messages) {

    public AiConversationVO(String conversationId,
                            String title,
                            String createdAt,
                            String updatedAt,
                            List<AiMessageVO> messages) {
        this(conversationId, title, "ACTIVE", createdAt, updatedAt, null,
                messages == null ? 0 : messages.size(), null, messages);
    }
}
