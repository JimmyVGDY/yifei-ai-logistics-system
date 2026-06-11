package jimmy.ai.model;

import java.util.List;

/**
 * AI Agent 执行结果。
 *
 * @param finalAnswer      最终回答文本
 * @param totalIterations  总迭代轮数
 * @param totalToolCalls   总工具调用次数
 * @param citations        所有引用来源（含知识库 + 工具调用）
 * @param steps            每步执行记录
 * @param totalTokens      消耗的 Token 总数（估算）
 * @param durationMs       总耗时（毫秒）
 */
public record AgentResult(
        String finalAnswer,
        int totalIterations,
        int totalToolCalls,
        List<AiCitation> citations,
        List<AgentStep> steps,
        int totalTokens,
        long durationMs
) {
}
