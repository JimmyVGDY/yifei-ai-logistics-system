package jimmy.ai.model;

import java.util.List;

/**
 * AI 长期记忆召回结果。
 */
public record AiMemoryRecallResult(
        boolean enabled,
        int hitCount,
        String context,
        List<AiCitation> citations) {
}
