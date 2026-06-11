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

    /**
     * MyBatis 查询会话主表时只返回会话摘要字段，消息明细由 Service 再单独补齐。
     * 这里保留 8 参数构造器，避免 record 主构造器要求传入 messages 导致映射失败。
     */
    public AiConversationVO(String conversationId,
                            String title,
                            String status,
                            String createdAt,
                            String updatedAt,
                            String archivedAt,
                            Integer messageCount,
                            String contextSnapshot) {
        this(conversationId, title, status, createdAt, updatedAt, archivedAt,
                messageCount, contextSnapshot, List.of());
    }

    public AiConversationVO(String conversationId,
                            String title,
                            String createdAt,
                            String updatedAt,
                            List<AiMessageVO> messages) {
        this(conversationId, title, "ACTIVE", createdAt, updatedAt, null,
                messages == null ? 0 : messages.size(), null, messages);
    }
}
