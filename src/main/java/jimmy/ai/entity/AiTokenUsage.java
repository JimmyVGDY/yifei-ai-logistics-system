package jimmy.ai.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI Token 用量实体 —— 记录每次模型调用的 Token 消耗和费用估算。
 */
public class AiTokenUsage {

    private Long id;
    private String modelName;
    private String purpose;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private String userId;
    private String userCode;
    private String conversationId;
    private BigDecimal estimatedCost;
    private String modelBaseUrl;
    private Long durationMs;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }

    public Integer getPromptTokens() { return promptTokens; }
    public void setPromptTokens(Integer promptTokens) { this.promptTokens = promptTokens; }

    public Integer getCompletionTokens() { return completionTokens; }
    public void setCompletionTokens(Integer completionTokens) { this.completionTokens = completionTokens; }

    public Integer getTotalTokens() { return totalTokens; }
    public void setTotalTokens(Integer totalTokens) { this.totalTokens = totalTokens; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUserCode() { return userCode; }
    public void setUserCode(String userCode) { this.userCode = userCode; }

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }

    public BigDecimal getEstimatedCost() { return estimatedCost; }
    public void setEstimatedCost(BigDecimal estimatedCost) { this.estimatedCost = estimatedCost; }

    public String getModelBaseUrl() { return modelBaseUrl; }
    public void setModelBaseUrl(String modelBaseUrl) { this.modelBaseUrl = modelBaseUrl; }

    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
