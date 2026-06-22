package jimmy.ai.service;

import jimmy.ai.model.AiMemoryCandidate;
import jimmy.ai.model.AiMemoryGovernanceDecision;
import jimmy.ai.model.AiToolCall;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AiMemoryGovernanceService 测试 —— 覆盖多维度信号判定。
 */
class AiMemoryGovernanceServiceTest {

    private final AiMemoryConfidenceEngine confidenceEngine = new AiMemoryConfidenceEngine();
    private final AiMemorySourceVerifier sourceVerifier = new AiMemorySourceVerifier();
    private final AiMemoryGovernanceService governanceService =
            new AiMemoryGovernanceService(confidenceEngine, sourceVerifier);

    // ─── 原有测试兼容（行为应保持一致或更严格） ─────────────

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

        // FAVORITE_MODULE 类型默认进入 CANDIDATE，等待更多证据
        assertEquals(AiMemoryGovernanceService.STATUS_CANDIDATE, decision.status());
    }

    // ─── 新增：多维度幻觉检测 ─────────────────────────────────

    @Test
    void fabricatedPreferenceWithoutSourceMatchShouldBeHallucination() {
        // 模型编造了一个偏好，但用户原话完全没有相应表达
        AiMemoryCandidate candidate = new AiMemoryCandidate(
                "ANSWER_STYLE",
                "表格格式偏好",
                "用户希望所有回答都用表格展示。",
                0.88
        );

        AiMemoryGovernanceDecision decision = governanceService.decide(
                candidate,
                "查一下张三的运单状态",
                List.of(new AiToolCall("业务数据查询", "运单中心", "命中 1 条"))
        );

        // 来源不匹配 + 无显式偏好信号 → 应为疑似幻觉
        assertEquals(AiMemoryGovernanceService.STATUS_HALLUCINATION, decision.status());
    }

    @Test
    void explicitPreferenceWithStrongSourceMatchShouldBeActive() {
        AiMemoryCandidate candidate = new AiMemoryCandidate(
                "ANSWER_STYLE",
                "简短表格偏好",
                "用户明确要求以后用简短表格回答。",
                0.93
        );

        AiMemoryGovernanceDecision decision = governanceService.decide(
                candidate,
                "以后都用表格，简短一点，记住了吗",
                List.of()
        );

        // 显式偏好 + 高置信度 + 强来源匹配 → ACTIVE
        assertEquals(AiMemoryGovernanceService.STATUS_ACTIVE, decision.status());
    }

    @Test
    void keywordFallbackMemoryShouldBeCandidateNotActive() {
        // 关键词降级产生的记忆，即使置信度高也不应直接 ACTIVE
        AiMemoryCandidate candidate = new AiMemoryCandidate(
                "QUERY_HABIT",
                "费用查询习惯",
                "用户经常查询费用结算。",
                0.90
        );

        AiMemoryGovernanceDecision decision = governanceService.decide(
                candidate,
                "我经常查费用结算的数据",
                List.of(new AiToolCall("业务数据查询", "费用结算", "命中 3 条")),
                false  // 关键词降级
        );

        // 关键词降级 → 不是 LLM 提炼 → 应该进入候选而非直接生效
        assertNotEquals(AiMemoryGovernanceService.STATUS_ACTIVE, decision.status(),
                "关键词降级产生的记忆不应直接生效");
    }

    @Test
    void llmHighConfidenceWithStrongSourceShouldBeActive() {
        AiMemoryCandidate candidate = new AiMemoryCandidate(
                "ANSWER_STYLE",
                "先结论后依据",
                "用户偏好先给出结论再列出关键依据。",
                0.96
        );

        AiMemoryGovernanceDecision decision = governanceService.decide(
                candidate,
                "以后你先给结论，再列关键依据",
                List.of(),
                true  // LLM 提炼
        );

        // 非常高的置信度 + 强匹配 + 低幻觉风险 + LLM 提炼 → ACTIVE
        assertEquals(AiMemoryGovernanceService.STATUS_ACTIVE, decision.status());
    }

    @Test
    void policyJsonShouldIncludeHallucinationRiskAndSource() {
        AiMemoryCandidate candidate = new AiMemoryCandidate(
                "ANSWER_STYLE", "简短偏好", "用户喜欢简短回答", 0.92);

        AiMemoryGovernanceDecision decision = governanceService.decide(
                candidate, "以后简短点", List.of(), true);

        assertNotNull(decision.policyJson());
        assertTrue(decision.policyJson().contains("hallucinationRisk"),
                "policyJson 应包含幻觉风险评分");
        assertTrue(decision.policyJson().contains("source"),
                "policyJson 应包含来源标记");
    }

    // ─── 边界条件 ───────────────────────────────────────────────

    @Test
    void nullToolCallsShouldNotThrow() {
        AiMemoryCandidate candidate = new AiMemoryCandidate(
                "QUERY_HABIT", "测试", "用户测试查询", 0.85);

        assertDoesNotThrow(() ->
                governanceService.decide(candidate, "测试查询", null));
    }

    @Test
    void emptyUserMessageShouldStillProduceValidDecision() {
        AiMemoryCandidate candidate = new AiMemoryCandidate(
                "QUERY_HABIT", "测试", "用户测试查询", 0.85);

        AiMemoryGovernanceDecision decision = governanceService.decide(
                candidate, "", List.of());

        assertNotNull(decision);
        assertNotNull(decision.status());
    }

    // ─── 召回状态判断 ──────────────────────────────────────────

    @Test
    void activeAndWeakeningStatusShouldBeRecallable() {
        assertTrue(governanceService.isRecallableStatus(AiMemoryGovernanceService.STATUS_ACTIVE));
        assertTrue(governanceService.isRecallableStatus(AiMemoryGovernanceService.STATUS_WEAKENING));
    }

    @Test
    void hallucinationCandidateAndArchivedShouldNotBeRecallable() {
        assertFalse(governanceService.isRecallableStatus(AiMemoryGovernanceService.STATUS_HALLUCINATION));
        assertFalse(governanceService.isRecallableStatus(AiMemoryGovernanceService.STATUS_CANDIDATE));
        assertFalse(governanceService.isRecallableStatus("ARCHIVED"));
    }
}
