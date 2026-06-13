package jimmy.ai.model;

import java.util.List;

/**
 * AI 执行计划。
 * <p>
 * 计划只描述“准备查什么、为什么查”，不能绕过后端权限、客户数据范围和只读 SQL 安全校验。
 */
public record AiExecutionPlan(
        AiExecutionMode mode,
        List<String> candidateModules,
        String keyword,
        String reason
) {
    public AiExecutionPlan {
        candidateModules = candidateModules == null ? List.of() : List.copyOf(candidateModules);
        keyword = keyword == null ? "" : keyword;
        reason = reason == null ? "" : reason;
    }

    public static AiExecutionPlan general(String reason) {
        return new AiExecutionPlan(AiExecutionMode.GENERAL_CHAT, List.of(), "", reason);
    }
}
