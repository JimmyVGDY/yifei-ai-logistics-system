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
        List<String> columns,
        String cursorId,
        Long total,
        Integer returnedCount,
        Long remainingCount,
        Boolean hasMore,
        String nextPageHint,
        List<AiDataResultGroup> dataGroups) {

    public AiReadonlyQueryResult {
        citations = citations == null ? List.of() : List.copyOf(citations);
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        rows = rows == null ? List.of() : List.copyOf(rows);
        columns = columns == null ? List.of() : List.copyOf(columns);
        dataGroups = dataGroups == null ? List.of() : List.copyOf(dataGroups);
    }

    public AiReadonlyQueryResult(boolean executed, String answerContext,
                                  List<AiCitation> citations, List<AiToolCall> toolCalls) {
        this(executed, answerContext, citations, toolCalls, List.of(), List.of());
    }

    public AiReadonlyQueryResult(boolean executed, String answerContext,
                                 List<AiCitation> citations, List<AiToolCall> toolCalls,
                                 List<Map<String, Object>> rows, List<String> columns) {
        this(executed, answerContext, citations, toolCalls, rows, columns, null, null, null, null, false, null, List.of());
    }

    public AiReadonlyQueryResult(boolean executed, String answerContext,
                                 List<AiCitation> citations, List<AiToolCall> toolCalls,
                                 List<Map<String, Object>> rows, List<String> columns,
                                 String cursorId, Long total, Integer returnedCount,
                                 Long remainingCount, Boolean hasMore, String nextPageHint) {
        this(executed, answerContext, citations, toolCalls, rows, columns, cursorId, total,
                returnedCount, remainingCount, hasMore, nextPageHint, List.of());
    }

    public static AiReadonlyQueryResult empty() {
        return new AiReadonlyQueryResult(false, "", List.of(), List.of());
    }
}
