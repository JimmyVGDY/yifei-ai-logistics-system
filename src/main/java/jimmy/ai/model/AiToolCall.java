package jimmy.ai.model;

/**
 * AI 工具调用摘要 —— 记录本次问答中使用的只读工具及执行结果。
 */
public record AiToolCall(String toolName, String target, String result) {
}
