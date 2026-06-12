package jimmy.ai.model;

import java.util.List;

/**
 * AI 助手问答响应 —— 包含回答、引用、工具调用和审计追踪标识。
 */
public record AiChatResponse(
        String conversationId,
        String answer,
        List<AiCitation> citations,
        List<AiToolCall> toolCalls,
        List<AiDataResult> dataResults,
        String traceId,
        String operationId) {
}
