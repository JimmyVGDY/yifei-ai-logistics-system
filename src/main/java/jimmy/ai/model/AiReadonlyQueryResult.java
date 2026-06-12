package jimmy.ai.model;

import jimmy.ai.model.AiCitation;
import jimmy.ai.model.AiToolCall;

import java.util.List;
import java.util.Map;

/**
 * AI 只读查询结果 —— 用于拼接模型上下文和前端工具调用摘要。
 * <p>
 * rows 字段携带查询返回的实际数据行，供前端渲染可滚动分页表格；
 * 模型上下文仍使用 answerContext 文本摘要，不受影响。
 */
public record AiReadonlyQueryResult(
        boolean executed,
        String answerContext,
        List<AiCitation> citations,
        List<AiToolCall> toolCalls,
        List<Map<String, Object>> rows,
        List<String> columns) {

    public AiReadonlyQueryResult(boolean executed, String answerContext,
                                  List<AiCitation> citations, List<AiToolCall> toolCalls) {
        this(executed, answerContext, citations, toolCalls, List.of(), List.of());
    }

    public static AiReadonlyQueryResult empty() {
        return new AiReadonlyQueryResult(false, "", List.of(), List.of());
    }
}
