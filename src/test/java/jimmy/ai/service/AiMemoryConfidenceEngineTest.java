package jimmy.ai.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AiMemoryConfidenceEngine 的完整测试。
 * <p>
 * 覆盖：幻觉风险评估 / 不确定词检测 / 显式偏好识别 / 自动升级判断 / 自动恢复判断 / 衰减归档判断。
 */
class AiMemoryConfidenceEngineTest {

    private final AiMemoryConfidenceEngine engine = new AiMemoryConfidenceEngine();

    // ─── 幻觉风险评估 ───────────────────────────────────────────

    @Test
    void modelGuessWithUncertainWordsShouldHaveHighHallucinationRisk() {
        // 模型说"用户可能希望..." —— 典型推测
        double risk = engine.hallucinationRisk(
                "用户可能希望以后都用表格展示",
                "查一下订单",
                0.85,
                false,  // 来源不匹配
                true    // LLM 提炼
        );
        assertTrue(risk >= 0.50, "含不确定词+来源不匹配应产生较高幻觉风险，实际=" + risk);
    }

    @Test
    void explicitPreferenceWithStrongSourceMatchShouldHaveLowRisk() {
        double risk = engine.hallucinationRisk(
                "用户喜欢简短的表格回答",
                "以后都用表格，简短一点",
                0.92,
                true,   // 来源强匹配
                true    // LLM 提炼
        );
        assertTrue(risk < 0.35, "显式偏好+强匹配应低风险，实际=" + risk);
    }

    @Test
    void keywordFallbackSourceShouldHaveLowerMismatchPenalty() {
        // 关键词降级产生的记忆，来源不匹配的惩罚应更低
        double riskLlm = engine.hallucinationRisk(
                "用户可能希望简短回答",
                "查订单",
                0.88,
                false,
                true   // LLM 提炼
        );
        double riskKeyword = engine.hallucinationRisk(
                "用户可能希望简短回答",
                "查订单",
                0.88,
                false,
                false  // 关键词降级
        );
        assertTrue(riskKeyword < riskLlm,
                "关键词降级应比LLM提炼有更低的来源不匹配惩罚，LLM=" + riskLlm + ", keyword=" + riskKeyword);
    }

    @Test
    void lowLlmConfidenceShouldIncreaseHallucinationRisk() {
        double riskHigh = engine.hallucinationRisk(
                "用户偏好表格格式", "查订单", 0.95, false, true);
        double riskLow = engine.hallucinationRisk(
                "用户偏好表格格式", "查订单", 0.73, false, true);
        assertTrue(riskLow > riskHigh,
                "LLM置信度越低风险应越高，highConf=" + riskHigh + ", lowConf=" + riskLow);
    }

    // ─── 不确定词检测 ───────────────────────────────────────────

    @ParameterizedTest
    @CsvSource({
            "用户可能希望简短回答, true",
            "用户习惯用表格展示, false",
            "这大概是一个偏好, true",
            "用户明确要求简短, false",
            "模型推测用户喜欢列表, true",
            "用户每次都说要详细, false"
    })
    void shouldDetectUncertaintyCorrectly(String text, boolean expected) {
        assertEquals(expected, engine.containsUncertainty(text),
                "text='" + text + "' uncertainty detection mismatch");
    }

    // ─── 显式偏好识别 ───────────────────────────────────────────

    @ParameterizedTest
    @CsvSource({
            "以后都查运输任务, true",
            "记住我喜欢简短回答, true",
            "帮我查一下订单, false",
            "今天有哪些新运单, false",
            "默认用表格展示, true",
            "不要展开太详细, true",
            "这个怎么操作, false"
    })
    void shouldDetectExplicitPreferenceCorrectly(String userMessage, boolean expected) {
        assertEquals(expected, engine.hasExplicitPreference(userMessage),
                "message='" + userMessage + "' explicit preference detection mismatch");
    }

    // ─── 自动升级判断 ───────────────────────────────────────────

    @Test
    void candidateWithEnoughEvidenceShouldAutoPromote() {
        assertTrue(engine.shouldAutoPromote(2, 0.88, "用户偏好简短回答", true));
    }

    @Test
    void candidateWithInsufficientEvidenceShouldNotAutoPromote() {
        assertFalse(engine.shouldAutoPromote(1, 0.90, "用户偏好简短回答", true));
    }

    @Test
    void candidateWithUncertaintyShouldNotAutoPromote() {
        assertFalse(engine.shouldAutoPromote(3, 0.90, "用户可能希望简短回答", true));
    }

    @Test
    void candidateWithoutSourceMatchShouldNotAutoPromote() {
        assertFalse(engine.shouldAutoPromote(3, 0.90, "用户偏好简短回答", false));
    }

    // ─── 自动恢复判断 ───────────────────────────────────────────

    @Test
    void hallucinationWithRecentMatchShouldAutoRecover() {
        assertTrue(engine.shouldAutoRecoverFromHallucination(1, 0.82, true));
    }

    @Test
    void hallucinationWithoutRecentMatchShouldNotAutoRecover() {
        assertFalse(engine.shouldAutoRecoverFromHallucination(1, 0.82, false));
    }

    @Test
    void hallucinationWithLowConfidenceShouldNotAutoRecover() {
        assertFalse(engine.shouldAutoRecoverFromHallucination(2, 0.72, true));
    }

    // ─── 衰减/归档判断 ───────────────────────────────────────────

    @Test
    void memoryStaleForMoreThan14DaysShouldDecay() {
        assertTrue(engine.shouldDecay(15, 15));
    }

    @Test
    void memoryRecalledRecentlyShouldNotDecay() {
        assertFalse(engine.shouldDecay(5, 10));
    }

    @Test
    void memoryStaleForMoreThan30DaysShouldArchive() {
        assertTrue(engine.shouldArchive(31));
    }

    @Test
    void memoryRecalledWithin30DaysShouldNotArchive() {
        assertFalse(engine.shouldArchive(20));
    }

    // ─── 边界条件 ───────────────────────────────────────────────

    @Test
    void nullTextShouldHaveZeroUncertainty() {
        assertFalse(engine.containsUncertainty(null));
    }

    @Test
    void nullUserMessageShouldNotHaveExplicitPreference() {
        assertFalse(engine.hasExplicitPreference(null));
    }

    @Test
    void emptyInputsShouldProduceReasonableRisk() {
        double risk = engine.hallucinationRisk("", "", 0.80, false, true);
        assertTrue(risk >= 0.0 && risk <= 1.0, "空输入仍应产出合法风险值(0~1)");
    }
}
