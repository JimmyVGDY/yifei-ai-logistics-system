package jimmy.ai.service;

import jimmy.ai.model.AiMemoryCandidate;
import jimmy.ai.model.AiMemoryGovernanceDecision;
import jimmy.ai.model.AiToolCall;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AiMemoryGovernanceServiceTest {

    private final AiMemoryGovernanceService governanceService = new AiMemoryGovernanceService();

    @Test
    void explicitTaskExceptionPreferenceShouldBecomeScopedActiveMemory() {
        AiMemoryCandidate candidate = new AiMemoryCandidate(
                "QUERY_HABIT",
                "仅查询运输任务异常",
                "用户要求只查询运输任务中的异常，不查其他模块异常。",
                0.91
        );

        AiMemoryGovernanceDecision decision = governanceService.decide(
                candidate,
                "以后只查运输任务里的异常，不要查异常管理，记住了吗",
                List.of(new AiToolCall("业务数据查询", "运输任务", "命中 1 条"))
        );

        assertEquals(AiMemoryGovernanceService.STATUS_ACTIVE, decision.status());
        assertEquals(AiMemoryGovernanceService.SCOPE_SCENARIO, decision.memoryScope());
        assertEquals("task_exception", decision.scopeValue());
        assertEquals("query_scope:task_exception", decision.conflictGroup());
    }

    @Test
    void uncertainModelGuessShouldBeQuarantinedAsSuspectedHallucination() {
        AiMemoryCandidate candidate = new AiMemoryCandidate(
                "QUERY_HABIT",
                "疑似偏好",
                "模型推测用户可能希望以后都按异常任务查询。",
                0.95
        );

        AiMemoryGovernanceDecision decision = governanceService.decide(
                candidate,
                "刚才只是问一下情况",
                List.of()
        );

        assertEquals(AiMemoryGovernanceService.STATUS_HALLUCINATION, decision.status());
        assertFalse(governanceService.isRecallable(decision));
    }

    @Test
    void weakFavoriteModuleSignalShouldRemainCandidate() {
        AiMemoryCandidate candidate = new AiMemoryCandidate(
                "FAVORITE_MODULE",
                "常看费用",
                "用户最近查询过费用结算。",
                0.80
        );

        AiMemoryGovernanceDecision decision = governanceService.decide(
                candidate,
                "看看费用情况",
                List.of(new AiToolCall("业务数据查询", "费用结算", "命中 3 条"))
        );

        assertEquals(AiMemoryGovernanceService.STATUS_CANDIDATE, decision.status());
        assertFalse(governanceService.isRecallable(decision));
    }
}
