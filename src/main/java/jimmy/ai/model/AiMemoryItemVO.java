package jimmy.ai.model;

/**
 * AI 长期记忆列表项，前端仅展示脱敏摘要，不返回向量原文。
 */
public record AiMemoryItemVO(
        Long id,
        String memoryType,
        String memoryTitle,
        String memorySummary,
        Double confidence,
        String qdrantPointId,
        String createTime,
        String updateTime,
        String lastRecallTime,
        String status,
        String memoryScope,
        String scopeValue,
        String conflictGroup,
        Integer priority,
        Integer evidenceCount,
        Integer negativeCount,
        Long supersededBy,
        String lastAppliedAt,
        String policyJson) {

    public AiMemoryItemVO(Long id,
                          String memoryType,
                          String memoryTitle,
                          String memorySummary,
                          Double confidence,
                          String qdrantPointId,
                          String createTime,
                          String updateTime,
                          String lastRecallTime) {
        this(id, memoryType, memoryTitle, memorySummary, confidence, qdrantPointId, createTime, updateTime,
                lastRecallTime, "ACTIVE", "GLOBAL", "", memoryType, 50, 1, 0, null, null, null);
    }
}
