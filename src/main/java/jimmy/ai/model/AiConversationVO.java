package jimmy.ai.model;

import java.util.List;

/**
 * AI 会话视图 —— 第一版会话存 Redis，过期后自动消失。
 */
public record AiConversationVO(
        String conversationId,
        String title,
        String createdAt,
        String updatedAt,
        List<AiMessageVO> messages) {
}
