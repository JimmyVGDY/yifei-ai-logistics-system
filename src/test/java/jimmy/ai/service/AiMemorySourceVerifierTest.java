package jimmy.ai.service;

import jimmy.ai.model.AiMemoryCandidate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AiMemorySourceVerifier 的完整测试。
 */
class AiMemorySourceVerifierTest {

    private final AiMemorySourceVerifier verifier = new AiMemorySourceVerifier();

    @Test
    void preferenceClaimMatchingUserWordsShouldBeStrongMatch() {
        AiMemoryCandidate candidate = new AiMemoryCandidate(
                "ANSWER_STYLE", "简短回答偏好", "用户希望简短精炼的回答", 0.90);

        AiMemorySourceVerifier.VerificationResult result = verifier.verify(
                candidate, "以后回答都简短一点，不要太长");

        assertEquals(AiMemorySourceVerifier.VerificationResult.STRONG_MATCH, result);
    }

    @Test
    void fabricatedPreferenceWithoutUserSupportShouldBeNoMatch() {
        // 模型编造了"用户喜欢表格"的偏好，但用户原话只问了订单
        AiMemoryCandidate candidate = new AiMemoryCandidate(
                "ANSWER_STYLE", "用户偏好表格格式", "用户喜欢用表格展示数据", 0.85);

        AiMemorySourceVerifier.VerificationResult result = verifier.verify(
                candidate, "帮我查一下订单123的详细情况");

        assertEquals(AiMemorySourceVerifier.VerificationResult.NO_MATCH, result);
    }

    @Test
    void partialKeywordOverlapShouldBeWeakMatch() {
        AiMemoryCandidate candidate = new AiMemoryCandidate(
                "QUERY_HABIT", "用户关注运输任务异常",
                "用户在查询运输任务时主要关注异常情况", 0.88);

        AiMemorySourceVerifier.VerificationResult result = verifier.verify(
                candidate, "运输任务最近有什么异常吗");

        // "运输任务"和"异常"都在用户原话中出现 → 强匹配
        assertEquals(AiMemorySourceVerifier.VerificationResult.STRONG_MATCH, result);
    }

    @Test
    void moduleNameMatchAloneShouldBeWeakMatch() {
        AiMemoryCandidate candidate = new AiMemoryCandidate(
                "FAVORITE_MODULE", "用户关注费用结算",
                "用户近期经常查询费用结算模块", 0.80);

        AiMemorySourceVerifier.VerificationResult result = verifier.verify(
                candidate, "查一下费用结算的记录");

        // "费用"匹配 → 部分匹配
        assertNotEquals(AiMemorySourceVerifier.VerificationResult.NO_MATCH, result);
    }

    @Test
    void nullCandidateShouldBeNoMatch() {
        assertEquals(AiMemorySourceVerifier.VerificationResult.NO_MATCH,
                verifier.verify(null, "任何消息"));
    }

    @Test
    void nullUserMessageShouldBeNoMatch() {
        AiMemoryCandidate candidate = new AiMemoryCandidate(
                "QUERY_HABIT", "偏好", "描述", 0.90);
        assertEquals(AiMemorySourceVerifier.VerificationResult.NO_MATCH,
                verifier.verify(candidate, null));
    }
}
