package jimmy.ai.model;

/**
 * AI 对话意图闸门结果。
 * <p>
 * 这里不做具体业务查询，只决定本轮对话是否允许进入业务查询工具。默认原则是：
 * 用户在纠正 AI、限定范围或表达偏好时，不查库；只有明确查询或上下文续查时才开放只读工具。
 */
public record AiConversationIntent(
        AiExecutionPlan executionPlan,
        boolean allowBusinessTools,
        boolean allowReadonlyFallback,
        boolean directAnswer,
        String answer
) {
    public AiConversationIntent {
        executionPlan = executionPlan == null ? AiExecutionPlan.general("未识别意图，走普通问答") : executionPlan;
        answer = answer == null ? "" : answer;
    }

    public static AiConversationIntent direct(AiExecutionMode mode, String answer, String reason) {
        return new AiConversationIntent(new AiExecutionPlan(mode, java.util.List.of(), "", reason),
                false, false, true, answer);
    }

    public static AiConversationIntent fromPlan(AiExecutionPlan plan) {
        AiExecutionMode mode = plan == null ? AiExecutionMode.GENERAL_CHAT : plan.mode();
        boolean queryAllowed = switch (mode) {
            case MODULE_QUERY, QUERY_CONTINUATION, GLOBAL_SEARCH, JOINED_QUERY, LOG_ANALYSIS, READONLY_SQL -> true;
            case RAG_SEARCH, GENERAL_CHAT, CONTROL_PREFERENCE, CLARIFY_REQUIRED -> false;
        };
        return new AiConversationIntent(plan, queryAllowed, queryAllowed, false, "");
    }
}
