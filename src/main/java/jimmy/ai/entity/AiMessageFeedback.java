package jimmy.ai.entity;

import java.time.LocalDateTime;

/**
 * AI 消息反馈实体 —— 记录用户对 AI 回答的点赞/点踩评价。
 */
public class AiMessageFeedback {

    private Long id;
    private String messageId;
    private String conversationId;
    private String userId;
    private String rating;
    private String comment;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }
    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getRating() { return rating; }
    public void setRating(String rating) { this.rating = rating; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
