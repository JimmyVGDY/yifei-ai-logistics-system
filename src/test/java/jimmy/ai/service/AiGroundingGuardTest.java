package jimmy.ai.service;

import jimmy.ai.model.AiDataResult;
import jimmy.ai.model.AiToolCall;
import jimmy.ai.model.GroundingCheck;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AiGroundingGuardTest {

    private final AiGroundingGuard groundingGuard = new AiGroundingGuard();

    @Test
    void dataClaimWithoutEvidenceShouldBeDiscarded() {
        String answer = "已查询系统数据库，共有 10 条记录。";

        GroundingCheck check = groundingGuard.check(answer, List.of(), List.of(), List.of());

        assertTrue(check.discardOriginal());
        assertTrue(check.issues().contains("UNSUPPORTED_DATA_CLAIM"));
    }

    @Test
    void partialDataShouldToneDownCompletenessClaims() {
        AiDataResult result = new AiDataResult(
                "业务数据查询",
                "运单中心",
                "命中 12 条",
                List.of("运单号"),
                List.of(Map.of("运单号", "WB001"), Map.of("运单号", "WB002")),
                "cursor-1",
                12L,
                2,
                10L,
                true,
                "继续查看剩余数据"
        );

        GroundingCheck check = groundingGuard.check(
                "已完整列出所有记录。",
                List.of(),
                List.of(new AiToolCall("业务数据查询", "运单中心", "命中 12 条")),
                List.of(result)
        );

        assertFalse(check.discardOriginal());
        assertTrue(check.issues().contains("PARTIAL_DATA_AS_FULL"));
        // 完整性措辞应被静默替换
        assertFalse(check.answer().contains("完整列出"));
        assertFalse(check.answer().contains("所有记录"));
    }

    @Test
    void groundedNormalAnswerShouldPassThrough() {
        String answer = "根据查询结果，运输任务中有 1 条异常记录。";

        GroundingCheck check = groundingGuard.check(
                answer,
                List.of(),
                List.of(new AiToolCall("业务数据查询", "运输任务", "命中 1 条")),
                List.of()
        );

        assertFalse(check.discardOriginal());
        assertTrue(check.issues().isEmpty());
        assertEquals(answer, check.answer());
    }

    @Test
    void toolCallWithEmptyResultShouldNotCountAsEvidence() {
        String answer = "已查询系统数据库，共有 10 条记录。";

        GroundingCheck check = groundingGuard.check(
                answer,
                List.of(),
                List.of(new AiToolCall("业务数据查询", "订单中心", "")),
                List.of()
        );

        assertTrue(check.discardOriginal());
        assertTrue(check.issues().contains("UNSUPPORTED_DATA_CLAIM"));
    }

    @Test
    void negativeAnswerShouldNotTriggerDataClaimDetection() {
        String answer = "未查询到相关记录，数据库中不存在匹配的数据。";

        GroundingCheck check = groundingGuard.check(answer, List.of(), List.of(), List.of());

        // 否定回答不应触发幻觉检测，但也没有证据支撑，按 discard 或 passThrough 取决于
        // 否定句过滤是否在 looksLikeDataClaim 之前生效
        assertFalse(check.discardOriginal(), "否定回答不应触发无证据数据声称检测");
    }
}
