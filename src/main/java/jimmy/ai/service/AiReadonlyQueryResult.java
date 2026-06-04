package jimmy.ai.service;

import jimmy.ai.model.AiCitation;
import jimmy.ai.model.AiToolCall;

import java.util.List;

/**
 * AI 只读查询结果 —— 用于拼接模型上下文和前端工具调用摘要。
 */
public record AiReadonlyQueryResult(
        boolean executed,
        String answerContext,
        List<AiCitation> citations,
        List<AiToolCall> toolCalls) {

    public static AiReadonlyQueryResult empty() {
        return new AiReadonlyQueryResult(false, "", List.of(), List.of());
    }
}
