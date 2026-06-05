package jimmy.ai.model;

/**
 * AI 回答完成后抽取出的长期记忆候选。
 */
public record AiMemoryCandidate(
        String memoryType,
        String memoryTitle,
        String memorySummary,
        double confidence) {
}
