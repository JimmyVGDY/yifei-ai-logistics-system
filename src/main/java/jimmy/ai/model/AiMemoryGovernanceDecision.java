package jimmy.ai.model;

/**
 * 一条候选长期记忆在写入前的治理判定结果。
 * <p>
 * 这里不保存敏感原文，只保存作用域、冲突组和生命周期等元信息，
 * 方便后续召回时做“只在合适场景生效”的过滤。
 */
public record AiMemoryGovernanceDecision(
        String memoryKey,
        String memoryScope,
        String scopeValue,
        String conflictGroup,
        String status,
        int priority,
        int evidenceCount,
        String policyJson) {
}
